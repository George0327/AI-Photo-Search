import { useEffect, useState } from "react";
import {
  Alert,
  Avatar,
  Badge,
  Card,
  Center,
  Container,
  Group,
  Loader,
  Select,
  Stack,
  Table,
  Text,
  Title,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { fetchUsers, changeUserRole } from "../api/userApi";
import type { UserDto } from "../api/userApi";
import {
  assignableRoles,
  getHighestRole,
  getRank,
  ROLE_LABELS,
} from "../utils/roles";
import type { AppRole } from "../utils/roles";

type UsersPageProps = {
  currentUserAuthorities: string[];
};

const BACKEND_TO_APP: Record<UserDto["role"], AppRole> = {
  USER: "ROLE_USER",
  ADMIN: "ROLE_ADMIN",
  SUPER_ADMIN: "ROLE_SUPER_ADMIN",
  OWNER: "ROLE_OWNER",
};

const APP_TO_BACKEND: Record<AppRole, UserDto["role"]> = {
  ROLE_USER: "USER",
  ROLE_ADMIN: "ADMIN",
  ROLE_SUPER_ADMIN: "SUPER_ADMIN",
  ROLE_OWNER: "OWNER",
};

export default function UsersPage({ currentUserAuthorities }: UsersPageProps) {
  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [changingUserId, setChangingUserId] = useState<number | null>(null);

  const myRole = getHighestRole(currentUserAuthorities);
  const myRank = getRank(myRole);
  const canAssign = assignableRoles(myRole);

  const roleSelectData = canAssign.map((r) => ({
    value: APP_TO_BACKEND[r],
    label: ROLE_LABELS[r],
  }));

  async function loadUsers() {
    setLoading(true);
    setError(null);
    try {
      setUsers((await fetchUsers()).users);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load users.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadUsers();
  }, []);

  async function handleRoleChange(user: UserDto, newBackendRole: UserDto["role"]) {
    setChangingUserId(user.id);
    try {
      const updated = await changeUserRole(user.id, newBackendRole);
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      notifications.show({
        title: "Role updated",
        message: `${user.fullName} is now ${ROLE_LABELS[BACKEND_TO_APP[newBackendRole]]}.`,
        color: "green",
        autoClose: 4000,
      });
    } catch (e) {
      notifications.show({
        title: "Failed to update role",
        message: e instanceof Error ? e.message : "Could not change role.",
        color: "red",
        autoClose: 5000,
      });
    } finally {
      setChangingUserId(null);
    }
  }

  return (
    <Container size="xl" py="md">
      <Stack>
        <Title order={2}>Users</Title>
        <Text size="sm" c="dimmed">
          Manage user roles. You can only affect users with a rank below your own.
        </Text>

        {error && (
          <Alert color="red" title="Error">
            {error}
          </Alert>
        )}

        {loading ? (
          <Center py={80}>
            <Loader />
          </Center>
        ) : (
          <Card withBorder radius="md" p={0}>
            <Table.ScrollContainer minWidth={700}>
              <Table verticalSpacing="sm" striped highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>User</Table.Th>
                    <Table.Th>Email</Table.Th>
                    <Table.Th>Current Role</Table.Th>
                    <Table.Th>Joined</Table.Th>
                    <Table.Th>Change Role</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {users.map((u) => {
                    const theirAppRole = BACKEND_TO_APP[u.role];
                    const theirRank = getRank(theirAppRole);
                    const canEdit = myRank > theirRank;

                    return (
                      <Table.Tr key={u.id}>
                        <Table.Td>
                          <Group gap="sm">
                            <Avatar
                              src={u.profilePictureUrl ?? undefined}
                              size="sm"
                              radius="xl"
                            />
                            <Text size="sm" fw={500}>
                              {u.fullName}
                            </Text>
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" c="dimmed">
                            {u.email}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Badge variant="light">{ROLE_LABELS[theirAppRole]}</Badge>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">
                            {new Date(u.createdAt).toLocaleDateString(undefined, {
                              year: "numeric",
                              month: "short",
                              day: "numeric",
                            })}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Select
                            size="xs"
                            placeholder="Assign role"
                            data={roleSelectData}
                            value={u.role}
                            disabled={!canEdit || changingUserId === u.id}
                            onChange={(val) => {
                              if (val) handleRoleChange(u, val as UserDto["role"]);
                            }}
                            w={140}
                          />
                        </Table.Td>
                      </Table.Tr>
                    );
                  })}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
            {users.length === 0 && !loading && (
              <Text size="sm" c="dimmed" p="md">
                No users found.
              </Text>
            )}
          </Card>
        )}
      </Stack>
    </Container>
  );
}
