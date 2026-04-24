import { test, expect } from "@playwright/test";

test.describe("auth shell", () => {
  test("home renders and login page is reachable", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "wage-payroll" })).toBeVisible();
    await page.getByRole("link", { name: "Sign in" }).click();
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  });
});
