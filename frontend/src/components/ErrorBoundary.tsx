import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";
import { Alert, Button, Center, Stack, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";

type Props = { children: ReactNode };
type State = { error: Error | null };

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Uncaught error:", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <Center h="100vh">
          <Stack align="center" maw={480} px="md">
            <Alert
              color="red"
              title="Something went wrong"
              icon={<IconAlertCircle size={18} />}
              w="100%"
            >
              <Text size="sm" mb="sm">
                {this.state.error.message || "An unexpected error occurred."}
              </Text>
              <Button
                size="xs"
                variant="light"
                color="red"
                onClick={() => this.setState({ error: null })}
              >
                Try again
              </Button>
            </Alert>
          </Stack>
        </Center>
      );
    }
    return this.props.children;
  }
}
