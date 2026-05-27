import { useEffect, useState } from "react";
import {
  Avatar,
  Button,
  Center,
  Container,
  Group,
  Loader,
  Paper,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { IconBrandGoogle } from "@tabler/icons-react";
import {
  BrowserRouter,
  Link,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";

import DriveGalleryPage from "./pages/DriveGalleryPage";
import AdminPage from "./pages/AdminPage";
import { fetchCurrentUser, loginWithGoogle, logout } from "./api/authApi";
import type { AuthUser } from "./api/authApi";

function AppRoutes({ user }: { user: AuthUser }) {
  const location = useLocation();
  const isAdmin = user.authorities.includes("ROLE_ADMIN");

  return (
    <>
      <Group justify="space-between" p="sm">
        <Group>
          <Button
            component={Link}
            to="/"
            variant={location.pathname === "/" ? "filled" : "subtle"}
            size="xs"
          >
            Gallery
          </Button>

          {isAdmin && (
            <Button
              component={Link}
              to="/admin"
              variant={location.pathname === "/admin" ? "filled" : "subtle"}
              size="xs"
            >
              Admin
            </Button>
          )}
        </Group>

        <Group gap="xs">
          <Avatar src={user.picture || undefined} size="sm" radius="xl" />
          <Text size="sm">{user.email}</Text>

          <Button variant="light" size="xs" onClick={logout}>
            Logout
          </Button>
        </Group>
      </Group>

      <Routes>
        <Route path="/" element={<DriveGalleryPage />} />

        <Route
          path="/admin"
          element={isAdmin ? <AdminPage /> : <Navigate to="/" replace />}
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}

export default function App() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  async function loadCurrentUser() {
    try {
      setLoading(true);
      const currentUser = await fetchCurrentUser();
      setUser(currentUser);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadCurrentUser();
  }, []);

  if (loading) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    );
  }

  if (!user?.authenticated) {
    return (
      <Center h="100vh">
        <Container size="xs">
          <Paper withBorder shadow="md" radius="md" p="xl">
            <Stack align="center">
              <Title order={2}>Photo AI Search</Title>

              <Text c="dimmed" ta="center">
                Sign in with your authorized Google account to access the photo gallery.
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

  return (
    <BrowserRouter>
      <AppRoutes user={user} />
    </BrowserRouter>
  );
}