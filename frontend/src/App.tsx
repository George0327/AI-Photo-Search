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

import DriveGalleryPage from "./pages/DriveGalleryPage";
import { fetchCurrentUser, loginWithGoogle, logout } from "./api/authApi";
import type { AuthUser } from "./api/authApi";

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
    <>
      <Group justify="flex-end" p="sm">
        <Group gap="xs">
          <Avatar src={user.picture || undefined} size="sm" radius="xl" />
          <Text size="sm">{user.email}</Text>
        </Group>

        <Button variant="light" size="xs" onClick={logout}>
          Logout
        </Button>
      </Group>

      <DriveGalleryPage />
    </>
  );
}