package com.photosearch.search.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

@Component
public class RemoteAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RemoteAuthFilter.class);

    @Value("${app.auth-service.url:http://auth-service:8081}")
    private String authServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String cookieHeader = buildCookieHeader(request);

        if (cookieHeader != null) {
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
