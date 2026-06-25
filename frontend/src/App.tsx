import { useEffect, useState } from "react";
import {
  Button,
  Center,
  Container,
  Loader,
  Modal,
  Paper,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconBrandGoogle } from "@tabler/icons-react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import DriveGalleryPage from "./pages/DriveGalleryPage";
import AdminPage from "./pages/AdminPage";
import UsersPage from "./pages/UsersPage";
import AppLayout from "./components/AppLayout";
import { fetchCurrentUser, loginWithGoogle, logout } from "./api/authApi";
import type { AuthUser } from "./api/authApi";
import { SESSION_EXPIRED_EVENT } from "./api/apiClient";
import { isElevated } from "./utils/roles";

export default function App() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  async function loadCurrentUser() {
    try {
      setLoading(true);
      const currentUser = await fetchCurrentUser();
      setUser(currentUser);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadCurrentUser();
  }, []);

  useEffect(() => {
    const handler = () => setSessionExpired(true);
    window.addEventListener(SESSION_EXPIRED_EVENT, handler);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handler);
  }, []);

  if (loading) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    );
  }

  if (sessionExpired) {
    return (
      <Modal
        opened
        onClose={() => {}}
        withCloseButton={false}
        centered
        title="Session expired"
        closeOnClickOutside={false}
        closeOnEscape={false}
      >
        <Stack>
          <Text size="sm" c="dimmed">
            Your session has expired. Please log in again to continue.
          </Text>
          <Button fullWidth onClick={logout}>
            Log out
          </Button>
        </Stack>
      </Modal>
    );
  }

  if (!user?.authenticated) {
    return (
      <Center h="100vh">
        <Container size="xs">
          <Paper withBorder shadow="md" radius="lg" p="xl">
            <Stack align="center">
              <Paper
                withBorder
                radius="md"
                w={56}
                h={56}
                style={{
                  display: "grid",
                  placeItems: "center",
                  background: "var(--mantine-color-violet-light)",
                  borderColor: "var(--mantine-color-violet-light-hover)",
                }}
              >
                <Text size="xs" fw={700} c="violet.7">
                  LOGO
                </Text>
              </Paper>

              <Title order={2} c="violet.8">
                Photo AI Search
              </Title>

              <Text c="dimmed" ta="center">
                Sign in with your authorized Google account to access the photo
                gallery.
              </Text>

              <Button
                leftSection={<IconBrandGoogle size={18} />}
                onClick={loginWithGoogle}
                fullWidth
              >
                Sign in with Google
              </Button>
            </Stack>
          </Paper>
        </Container>
      </Center>
    );
  }

  const elevated = isElevated(user.authorities);

  return (
    <BrowserRouter>
      <AppLayout user={user} onLogout={logout}>
        <Routes>
          <Route path="/" element={<DriveGalleryPage />} />

          <Route
            path="/admin"
            element={
              elevated ? (
                <AdminPage currentUserAuthorities={user.authorities} />
              ) : (
                <Navigate to="/" replace />
              )
            }
          />

          <Route
            path="/users"
            element={
              elevated ? (
                <UsersPage currentUserAuthorities={user.authorities} />
              ) : (
                <Navigate to="/" replace />
              )
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppLayout>
    </BrowserRouter>
  );
}
