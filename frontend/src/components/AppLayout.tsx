import {
  Avatar,
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

type AppLayoutProps = {
  user: AuthUser;
  onLogout: () => void;
  children: ReactNode;
};

export default function AppLayout({
  user,
  onLogout,
  children,
}: AppLayoutProps) {
  const location = useLocation();
  const isAdmin = user.authorities.includes("ROLE_ADMIN");

  return (
    <AppShell header={{ height: 64 }}>
      <AppShell.Header>
        <Container size="xl" h="100%">
          <Group justify="space-between" align="center" h="100%">
            <Group gap="md">
              <ThemeIcon size={40} radius="md" variant="light" color="brand">
                <IconSparkles size={20} />
              </ThemeIcon>

              <Box>
                <Title order={3} c="brand.8" style={{ lineHeight: 1.1 }}>
                  Photo AI Search
                </Title>
                <Text size="xs" c="dimmed">
                  Google Drive integration
                </Text>
              </Box>
            </Group>

            <Group gap="xs">
              <Button
                component={Link}
                to="/"
                variant={location.pathname === "/" ? "filled" : "subtle"}
              >
                Gallery
              </Button>

              {isAdmin && (
                <Button
                  component={Link}
                  to="/admin"
                  variant={location.pathname === "/admin" ? "filled" : "subtle"}
                >
                  Admin
                </Button>
              )}
            </Group>

            <Group gap="sm">
              <Box visibleFrom="sm" ta="right">
                <Text size="sm" fw={500}>
                  {user.name || user.email}
                </Text>
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
                Photo AI Search
              </Text>
              <Group gap="xs">
                <Text size="xs" c="dimmed">
                  Powered by CLIP and FAISS
                </Text>
                <Divider orientation="vertical" />
                <Text size="xs" c="brand.6" fw={500}>
                  Thesis Project
                </Text>
              </Group>
            </Group>
          </Container>
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}
