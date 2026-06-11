package com.photosearch.indexing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RemoteAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RemoteAuthFilter.class);
    private static final long CACHE_TTL_MS = 30_000;

    @Value("${app.auth-service.url:http://auth-service:8081}")
    private String authServiceUrl;

    private final RestTemplate restTemplate;
    private final Map<String, Object[]> tokenCache = new ConcurrentHashMap<>();

    public RemoteAuthFilter(@Qualifier("authRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String cookieHeader = buildCookieHeader(request);

        if (cookieHeader != null) {
            // Check in-memory cache first to avoid calling auth-service on every request
            Object[] cached = tokenCache.get(cookieHeader);
            if (cached != null) {
                if (System.currentTimeMillis() < (long) cached[1]) {
                    SecurityContextHolder.getContext().setAuthentication((Authentication) cached[0]);
                    filterChain.doFilter(request, response);
                    return;
                }
                tokenCache.remove(cookieHeader);
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Cookie", cookieHeader);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> authResponse = restTemplate.exchange(
                        authServiceUrl + "/api/auth/me",
                        HttpMethod.GET,
                        entity,
                        Map.class
                );

                Map<?, ?> body = authResponse.getBody();
                if (body != null && Boolean.TRUE.equals(body.get("authenticated"))) {
                    List<?> rawAuthorities = (List<?>) body.get("authorities");
                    List<SimpleGrantedAuthority> authorities = rawAuthorities == null ? List.of() :
                            rawAuthorities.stream()
                                    .map(a -> new SimpleGrantedAuthority(a.toString()))
                                    .toList();

                    String email = (String) body.get("email");
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    tokenCache.put(cookieHeader, new Object[]{auth, System.currentTimeMillis() + CACHE_TTL_MS});
                }
            } catch (Exception e) {
                logger.warn("Auth service unreachable: {}", e.getMessage());
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Auth service unavailable");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String buildCookieHeader(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .map(c -> c.getName() + "=" + c.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);
    }
}
