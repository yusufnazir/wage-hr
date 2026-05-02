# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: m1-platform.spec.ts >> M1 — multi-host isolation (demo vs unknown tenant) >> demo shell vs unknown-tenant host after same login session
- Location: e2e\m1-platform.spec.ts:153:7

# Error details

```
Error: expect(locator).toHaveText(expected) failed

Locator: getByTestId('me-tenant')
Expected: "demo"
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toHaveText" with timeout 5000ms
  - waiting for getByTestId('me-tenant')

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - status [ref=e2]:
    - generic [ref=e3]:
      - img [ref=e5]
      - generic [ref=e7]:
        - text: Static route
        - button "Hide static indicator" [ref=e8] [cursor=pointer]:
          - img [ref=e9]
  - alert [ref=e12]
  - generic [ref=e13]:
    - complementary [ref=e14]:
      - link "Wage Payroll Demo tenant demo" [ref=e16] [cursor=pointer]:
        - /url: /app
        - generic [ref=e17]:
          - img "Wage Payroll" [ref=e18]
          - generic [ref=e19]:
            - paragraph [ref=e20]: Demo tenant
            - paragraph [ref=e21]: demo
      - navigation [ref=e22]:
        - list [ref=e23]:
          - listitem [ref=e24]:
            - button "Workspace" [expanded] [ref=e25] [cursor=pointer]:
              - generic [ref=e26]: Workspace
              - img [ref=e27]
            - list [ref=e29]:
              - listitem [ref=e30]:
                - link "Dashboard" [ref=e31] [cursor=pointer]:
                  - /url: /app
                  - img [ref=e32]
                  - generic [ref=e34]: Dashboard
              - listitem [ref=e35]:
                - link "Documents" [ref=e36] [cursor=pointer]:
                  - /url: /app/documents
                  - img [ref=e37]
                  - generic [ref=e40]: Documents
          - listitem [ref=e41]:
            - button "Security" [expanded] [ref=e42] [cursor=pointer]:
              - generic [ref=e43]: Security
              - img [ref=e44]
            - list [ref=e46]:
              - listitem [ref=e47]:
                - link "Users" [ref=e48] [cursor=pointer]:
                  - /url: /app/users
                  - img [ref=e49]
                  - generic [ref=e53]: Users
              - listitem [ref=e54]:
                - link "Roles" [ref=e55] [cursor=pointer]:
                  - /url: /app/roles
                  - img [ref=e56]
                  - generic [ref=e59]: Roles
      - button "Collapse sidebar" [ref=e61] [cursor=pointer]:
        - generic [ref=e62]: Collapse sidebar
    - generic [ref=e63]:
      - banner [ref=e64]:
        - generic [ref=e65]:
          - img [ref=e66]
          - generic [ref=e67]:
            - generic [ref=e68]: Wage Payroll
            - generic [ref=e69]: demo
        - generic [ref=e70]:
          - 'button "Color theme: System (light). Click to cycle light, dark, and system." [ref=e71] [cursor=pointer]': System (light)
          - button "Account menu VI" [ref=e73] [cursor=pointer]:
            - generic [ref=e74]: Account menu
            - text: VI
      - main [ref=e75]:
        - paragraph [ref=e77]: Loading dashboard…
```

# Test source

```ts
  63  |     await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
  64  |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  65  |     await Promise.all([
  66  |       page.waitForURL(new RegExp(`http://admin\\.lvh\\.me:${port}/app`)),
  67  |       page.getByRole("button", { name: "Continue" }).click(),
  68  |     ]);
  69  | 
  70  |     await page.goto(`http://admin.lvh.me:${port}/app/platform-mail-templates`);
  71  |     await expect(page.getByTestId("platform-mail-templates-page")).toBeVisible({ timeout: 20_000 });
  72  |     await expect(page.getByTestId("mail-template-edit-TENANT_INVITATION")).toBeVisible();
  73  |   });
  74  | });
  75  | 
  76  | test.describe("Platform settings — mail test", () => {
  77  |   test("superadmin can use Save + send test and trigger both API calls", async ({ page }) => {
  78  |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");
  79  | 
  80  |     await page.goto(`http://auth.lvh.me:${port}/login`);
  81  |     await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
  82  |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  83  |     await Promise.all([
  84  |       page.waitForURL(new RegExp(`http://admin\\.lvh\\.me:${port}/app`)),
  85  |       page.getByRole("button", { name: "Continue" }).click(),
  86  |     ]);
  87  | 
  88  |     await page.goto(`http://admin.lvh.me:${port}/app/platform-settings`);
  89  |     await page.getByTestId("platform-settings-tab-mail").click();
  90  |     await expect(page.getByTestId("platform-settings-save-mail-and-test")).toBeVisible();
  91  | 
  92  |     await page.getByTestId("platform-settings-mail-test-to").fill("qa@example.com");
  93  | 
  94  |     const patchReq = page.waitForResponse(
  95  |       (r) => r.url().includes("/api/bff/v1/platform/settings") && r.request().method() === "PATCH",
  96  |     );
  97  |     const postReq = page.waitForResponse(
  98  |       (r) => r.url().includes("/api/bff/v1/platform/settings/mail/test") && r.request().method() === "POST",
  99  |     );
  100 | 
  101 |     await page.getByTestId("platform-settings-save-mail-and-test").click();
  102 | 
  103 |     await expect((await patchReq).status()).toBe(204);
  104 |     // Test-send can succeed (204) or fail with provider connectivity in local dev (502).
  105 |     await expect([204, 502]).toContain((await postReq).status());
  106 |     await expect(page.getByTestId("platform-settings-msg")).toBeVisible();
  107 |   });
  108 | });
  109 | 
  110 | test.describe("M1 — billing catalog privilege", () => {
  111 |   test("viewer sees billing summary but not plan picker (catalog 403)", async ({ page }) => {
  112 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");
  113 | 
  114 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  115 |     await page.getByRole("textbox", { name: "Email" }).fill("viewer@demo.lvh.me");
  116 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  117 |     await Promise.all([
  118 |       page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
  119 |       page.getByRole("button", { name: "Continue" }).click(),
  120 |     ]);
  121 | 
  122 |     await expect(page.getByTestId("billing-summary-section")).toBeVisible();
  123 |     await expect(page.getByTestId("billing-plans-forbidden")).toBeVisible();
  124 |     await expect(page.getByTestId("billing-plan-picker")).toHaveCount(0);
  125 |   });
  126 | });
  127 | 
  128 | test.describe("M1 — CSRF + state change (locale PATCH)", () => {
  129 |   test("changing locale updates nav labels (PATCH /me/locale)", async ({ page }) => {
  130 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");
  131 | 
  132 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  133 |     await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
  134 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  135 |     await Promise.all([
  136 |       page.waitForURL(new RegExp(`http://admin\\.lvh\\.me:${port}/app`)),
  137 |       page.getByRole("button", { name: "Continue" }).click(),
  138 |     ]);
  139 | 
  140 |     await page.getByTestId("user-menu-trigger").click();
  141 |     await page.getByTestId("user-menu-locale-toggle").click();
  142 |     const patch = page.waitForResponse(
  143 |       (r) => r.url().includes("/api/bff/v1/me/locale") && r.request().method() === "PATCH",
  144 |     );
  145 |     await page.getByTestId("user-menu-locale-nl").click();
  146 |     await expect((await patch).status()).toBe(204);
  147 |     await expect(page.getByText("Gebruikers")).toBeVisible();
  148 |     await expect(page.getByTestId("me-locale-display")).toHaveText("nl");
  149 |   });
  150 | });
  151 | 
  152 | test.describe("M1 — multi-host isolation (demo vs unknown tenant)", () => {
  153 |   test("demo shell vs unknown-tenant host after same login session", async ({ page }) => {
  154 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");
  155 | 
  156 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  157 |     await page.getByRole("textbox", { name: "Email" }).fill("viewer@demo.lvh.me");
  158 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  159 |     await Promise.all([
  160 |       page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
  161 |       page.getByRole("button", { name: "Continue" }).click(),
  162 |     ]);
> 163 |     await expect(page.getByTestId("me-tenant")).toHaveText("demo");
      |                                                 ^ Error: expect(locator).toHaveText(expected) failed
  164 | 
  165 |     await page.goto(`http://nosuchtenant.lvh.me:${port}/app`);
  166 |     await expect(page.getByText(/Unknown tenant for this host/i)).toBeVisible();
  167 |   });
  168 | });
  169 | 
  170 | test.describe("M1 — tenant member on demo host", () => {
  171 |   test("member login still lands on demo tenant /app with tenant context", async ({ page }) => {
  172 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");
  173 | 
  174 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  175 |     await page.getByRole("textbox", { name: "Email" }).fill("viewer@demo.lvh.me");
  176 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  177 |     await Promise.all([
  178 |       page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
  179 |       page.getByRole("button", { name: "Continue" }).click(),
  180 |     ]);
  181 |     await expect(page.getByTestId("me-tenant")).toHaveText("demo");
  182 |     await expect(page.getByTestId("demo-ok")).toContainText("Tenant user directory reachable");
  183 |   });
  184 | });
  185 | 
  186 | test.describe("M7 — platform countries", () => {
  187 |   test("platform superadmin can open platform countries page", async ({ page }) => {
  188 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");
  189 | 
  190 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  191 |     await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
  192 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  193 |     await Promise.all([
  194 |       page.waitForURL(new RegExp(`http://admin\\.lvh\\.me:${port}/app`)),
  195 |       page.getByRole("button", { name: "Continue" }).click(),
  196 |     ]);
  197 | 
  198 |     await page.goto(`http://admin.lvh.me:${port}/app/platform-countries`);
  199 |     await expect(page.getByTestId("platform-countries-page")).toBeVisible({ timeout: 20_000 });
  200 |     await expect(page.getByTestId("superadmin-lens-select")).toHaveCount(0);
  201 |     await expect(page.getByTestId("platform-countries-new")).toBeVisible();
  202 |   });
  203 | 
  204 |   test("tenant viewer cannot use platform countries management", async ({ page }) => {
  205 |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL and start the API");
  206 | 
  207 |     await page.goto(`http://auth.lvh.me:${port}/login`);
  208 |     await page.getByRole("textbox", { name: "Email" }).fill("viewer@demo.lvh.me");
  209 |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  210 |     await Promise.all([
  211 |       page.waitForURL(new RegExp(`http://demo\\.lvh\\.me:${port}/app`)),
  212 |       page.getByRole("button", { name: "Continue" }).click(),
  213 |     ]);
  214 | 
  215 |     await page.goto(`http://demo.lvh.me:${port}/app/platform-countries`);
  216 |     await expect(page.getByText(/Only a platform operator/i)).toBeVisible({ timeout: 20_000 });
  217 |   });
  218 | });
  219 | 
```