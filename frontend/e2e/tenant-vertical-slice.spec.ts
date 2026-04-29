import { test, expect } from "@playwright/test";

const port = process.env.PLAYWRIGHT_PORT ?? "3007";

test.describe("tenant web vertical slice", () => {
  test("demo tenant /app redirects to auth login when unauthenticated", async ({ page }) => {
    await page.goto(`http://demo.lvh.me:${port}/app`);
    await expect(page).toHaveURL(new RegExp(`^http://auth\\.lvh\\.me:${port}/login(\\?.*)?$`));
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  });

  test("unknown tenant host shows tenant-not-found after API 404", async ({ page }) => {
    test.skip(!process.env.PLAYWRIGHT_API_BASE_URL, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300)");
    await page.goto(`http://nosuchtenant.lvh.me:${port}/app`);
    await expect(page.getByText(/Unknown tenant for this host/i)).toBeVisible();
  });
});

test.describe("redirect safety (BFF)", () => {
  test("redirect-check rejects external returnTo", async ({ request }) => {
    test.skip(!process.env.PLAYWRIGHT_API_BASE_URL, "Set PLAYWRIGHT_API_BASE_URL and start API + Next (BFF proxies)");
    const url = `http://auth.lvh.me:${port}/api/bff/v1/auth/redirect-check?returnTo=${encodeURIComponent("https://evil.example/")}`;
    const res = await request.get(url);
    expect(res.status()).toBe(400);
  });

  test("redirect-check allows demo tenant app URL", async ({ request }) => {
    test.skip(!process.env.PLAYWRIGHT_API_BASE_URL, "Set PLAYWRIGHT_API_BASE_URL and start API + Next (BFF proxies)");
    const returnTo = `http://demo.lvh.me:${port}/app`;
    const url = `http://auth.lvh.me:${port}/api/bff/v1/auth/redirect-check?returnTo=${encodeURIComponent(returnTo)}`;
    const res = await request.get(url);
    expect(res.status()).toBe(204);
  });
});
