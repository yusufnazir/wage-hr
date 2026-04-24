import { test, expect } from "@playwright/test";

/**
 * M1 platform E2E — requires API + DB (see `docs/guides/E2E-TESTING-STANDARDS.md`).
 *
 * Env: `PLAYWRIGHT_API_BASE_URL` (e.g. `http://127.0.0.1:8300`) — Spring API must be up; the browser only talks to Next
 * (`/api/bff/...`). Ensure Next dev has `API_BASE_URL` (see `frontend/.env.example`).
 *
 * Port: `PLAYWRIGHT_PORT` (default 3007) must match `returnTo` / tenant URLs below.
 */
const port = process.env.PLAYWRIGHT_PORT ?? "3007";
const hasApi = !!process.env.PLAYWRIGHT_API_BASE_URL;

test.describe("M1 — theming", () => {
  test("login page exposes theme toggle (no API required)", async ({ page }) => {
    await page.goto(`http://auth.lvh.me:${port}/login`);
    await expect(page.getByTestId("theme-toggle")).toBeVisible();
  });
});

test.describe("M1 — auth host → tenant host session", () => {
  test("login on auth.* redirects to demo tenant /app (happy path)", async ({ page }) => {
    test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");

    await page.goto(`http://auth.lvh.me:${port}/login`);
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();

    await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
    await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");

    await Promise.all([
      page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
      page.getByRole("button", { name: "Continue" }).click(),
    ]);

    await expect(page.getByTestId("tenant-app-shell")).toBeVisible();
    await expect(page.getByTestId("me-email")).toHaveText("admin@demo.lvh.me");
    await expect(page.getByTestId("me-tenant")).toHaveText("demo");
    await expect(page.getByTestId("me-privileges")).toContainText("USER_VIEW");
    await expect(page.getByTestId("demo-ok")).toContainText("USER_VIEW");
    await expect(page.getByTestId("tenant-switcher")).toBeVisible();
    await expect(page.getByTestId("tenant-link-demo")).toBeVisible();
    await expect(page.getByTestId("tenant-link-acme")).toBeVisible();
  });
});

test.describe("M1 — CSRF + state change (locale PATCH)", () => {
  test("changing locale updates nav labels (PATCH /me/locale)", async ({ page }) => {
    test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");

    await page.goto(`http://auth.lvh.me:${port}/login`);
    await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
    await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
    await Promise.all([
      page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
      page.getByRole("button", { name: "Continue" }).click(),
    ]);

    await expect(page.getByTestId("locale-select")).toBeVisible();
    const patch = page.waitForResponse(
      (r) => r.url().includes("/api/bff/v1/me/locale") && r.request().method() === "PATCH",
    );
    await page.getByTestId("locale-select").selectOption("nl");
    await expect((await patch).status()).toBe(204);
    await expect(page.getByText("Gebruikers")).toBeVisible();
    await expect(page.getByTestId("locale-select")).toHaveValue("nl");
  });
});

test.describe("M1 — multi-host isolation (demo vs unknown tenant)", () => {
  test("demo shell vs unknown-tenant host after same login session", async ({ page }) => {
    test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");

    await page.goto(`http://auth.lvh.me:${port}/login`);
    await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
    await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
    await Promise.all([
      page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
      page.getByRole("button", { name: "Continue" }).click(),
    ]);
    await expect(page.getByTestId("me-tenant")).toHaveText("demo");

    await page.goto(`http://nosuchtenant.lvh.me:${port}/app`);
    await expect(page.getByText(/Unknown tenant for this host/i)).toBeVisible();
  });
});
