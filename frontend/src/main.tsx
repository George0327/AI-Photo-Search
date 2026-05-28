import React from "react";
import ReactDOM from "react-dom/client";
import { MantineProvider, createTheme } from "@mantine/core";
import { Notifications } from "@mantine/notifications";
import "@mantine/core/styles.css";
import "@mantine/notifications/styles.css";
import "./index.css";

import App from "./App";

const theme = createTheme({
  primaryColor: "brand",
  colors: {
    brand: [
      "#f6eeff",
      "#e5d5fc",
      "#c7a3f8",
      "#a76ef4",
      "#8c42f0",
      "#7a25ef",
      "#7114ef",
      "#6007d6",
      "#5603bf",
      "#4a00a8",
    ],
  },
  defaultRadius: "sm",
  fontFamily: "Inter, system-ui, -apple-system, sans-serif",
  headings: {
    fontFamily: "Inter, system-ui, -apple-system, sans-serif",
    fontWeight: "600",
  },
  shadows: {
    md: "0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05)",
  },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto">
      <Notifications />
      <App />
    </MantineProvider>
  </React.StrictMode>,
);
