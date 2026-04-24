import { defineConfig, devices } from "@playwright/test";

/** Dev default 3007; override with PLAYWRIGHT_PORT when that port is already in use. */
const port = process.env.PLAYWRIGHT_PORT ?? "3007";
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: process.env.CI
    ? undefined
    : {
        command: `npx next dev --turbopack -p ${port}`,
        url: `http://127.0.0.1:${port}`,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
