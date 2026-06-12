import {
  Anchor,
  Avatar,
  Badge,
  Box,
  Button,
  Container,
  Divider,
  Group,
  Text,
  Title,
  AppShell,
  ThemeIcon,
} from "@mantine/core";
import { Link, useLocation } from "react-router-dom";
import { IconSparkles } from "@tabler/icons-react";
import type { ReactNode } from "react";
import type { AuthUser } from "../api/authApi";
import { getHighestRole, isElevated, ROLE_COLORS, ROLE_LABELS } from "../utils/roles";

type AppLayoutProps = {
  user: AuthUser;
  onLogout: () => void;
  children: ReactNode;
};

function NavLink({ to, active, children }: { to: string; active: boolean; children: ReactNode }) {
  return (
    <Anchor
      component={Link}
      to={to}
      underline="never"
      fw={active ? 600 : 500}
      size="sm"
      style={{
        color: active ? "var(--mantine-color-brand-6)" : "var(--mantine-color-dimmed)",
        paddingBottom: 4,
        borderBottom: `2px solid ${active ? "var(--mantine-color-brand-6)" : "transparent"}`,
        transition: "color 0.15s ease, border-color 0.15s ease",
      }}
    >
      {children}
    </Anchor>
  );
}

export default function AppLayout({ user, onLogout, children }: AppLayoutProps) {
  const location = useLocation();
  const elevated = isElevated(user.authorities);
  const role = getHighestRole(user.authorities);

  return (
    <AppShell header={{ height: 64 }}>
      <AppShell.Header className="glass-header">
        <Container size="xl" h="100%">
          <Group justify="space-between" align="center" h="100%">
            <Group gap="md">
              <ThemeIcon size={40} radius="md" variant="light" color="brand">
                <IconSparkles size={20} />
              </ThemeIcon>

              <Box>
                <Title
                  order={3}
                  style={{
                    lineHeight: 1.1,
                    background: "linear-gradient(135deg, #7114ef 0%, #a76ef4 100%)",
                    WebkitBackgroundClip: "text",
                    WebkitTextFillColor: "transparent",
                    backgroundClip: "text",
                  }}
                >
                  Photo Search
                </Title>
                <Text size="xs" c="dimmed">
                  From Google Drive
                </Text>
              </Box>
            </Group>

            <Group gap="lg">
              <NavLink to="/" active={location.pathname === "/"}>
                Gallery
              </NavLink>
              {elevated && (
                <NavLink to="/admin" active={location.pathname === "/admin"}>
                  Admin
                </NavLink>
              )}
              {elevated && (
                <NavLink to="/users" active={location.pathname === "/users"}>
                  Users
                </NavLink>
              )}
            </Group>

            <Group gap="sm">
              <Box visibleFrom="sm" ta="right">
                <Group gap={6} justify="flex-end" wrap="nowrap">
                  <Text size="sm" fw={500}>
                    {user.name || user.email}
                  </Text>
                  <Badge size="xs" variant="light" color={ROLE_COLORS[role]}>
                    {ROLE_LABELS[role]}
                  </Badge>
                </Group>
                <Text size="xs" c="dimmed">
                  {user.email}
                </Text>
              </Box>
              <Avatar src={user.picture || undefined} size="sm" radius="xl" />
              <Button variant="light" size="xs" onClick={onLogout}>
                Logout
              </Button>
            </Group>
          </Group>
        </Container>
      </AppShell.Header>

      <AppShell.Main>
        <Box style={{ flex: 1, minHeight: "calc(100vh - 140px)" }}>
          {children}
        </Box>

        <Box
          component="footer"
          mt="xl"
          style={{ borderTop: "1px solid var(--mantine-color-default-border)" }}
        >
          <Container size="xl" py="md">
            <Group justify="space-between">
              <Text size="sm" c="dimmed">
                Photo Search
              </Text>
              <Group gap="xs">
                <Text size="xs" c="dimmed">
                  Powered by CLIP and FAISS
                </Text>
                <Divider orientation="vertical" />
                <Text size="xs" c="brand.6" fw={500}>
                  made with ♥ by George
                </Text>
              </Group>
            </Group>
          </Container>
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}
