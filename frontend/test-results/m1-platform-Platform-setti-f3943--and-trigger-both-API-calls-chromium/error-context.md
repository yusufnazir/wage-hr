# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: m1-platform.spec.ts >> Platform settings — mail test >> superadmin can use Save + send test and trigger both API calls
- Location: e2e\m1-platform.spec.ts:77:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 400
Received array: [204, 502]
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
      - link "Wage Payroll Wage Payroll Open a tenant subdomain" [ref=e16] [cursor=pointer]:
        - /url: /app
        - generic [ref=e17]:
          - img "Wage Payroll" [ref=e18]
          - generic [ref=e19]:
            - paragraph [ref=e20]: Wage Payroll
            - paragraph [ref=e21]: Open a tenant subdomain
      - navigation [ref=e22]:
        - list [ref=e23]:
          - listitem [ref=e24]:
            - button "Administration" [expanded] [ref=e25] [cursor=pointer]:
              - generic [ref=e26]: Administration
              - img [ref=e27]
            - list [ref=e29]:
              - listitem [ref=e30]:
                - link "Tenants" [ref=e31] [cursor=pointer]:
                  - /url: /app/platform-tenants
                  - img [ref=e32]
                  - generic [ref=e35]: Tenants
              - listitem [ref=e36]:
                - link "Role templates" [ref=e37] [cursor=pointer]:
                  - /url: /app/platform-role-templates
                  - img [ref=e38]
                  - generic [ref=e41]: Role templates
              - listitem [ref=e42]:
                - link "Platform currencies" [ref=e43] [cursor=pointer]:
                  - /url: /app/platform-currencies
                  - img [ref=e44]
                  - generic [ref=e47]: Platform currencies
              - listitem [ref=e48]:
                - link "Platform countries" [ref=e49] [cursor=pointer]:
                  - /url: /app/platform-countries
                  - img [ref=e50]
                  - generic [ref=e54]: Platform countries
              - listitem [ref=e55]:
                - link "Mail templates" [ref=e56] [cursor=pointer]:
                  - /url: /app/platform-mail-templates
                  - img [ref=e57]
                  - generic [ref=e59]: Mail templates
              - listitem [ref=e60]:
                - link "Platform settings" [ref=e61] [cursor=pointer]:
                  - /url: /app/platform-settings
                  - img [ref=e62]
                  - generic [ref=e65]: Platform settings
      - button "Collapse sidebar" [ref=e67] [cursor=pointer]:
        - generic [ref=e68]: Collapse sidebar
    - generic [ref=e69]:
      - banner [ref=e70]:
        - generic [ref=e71]:
          - img [ref=e72]
          - generic [ref=e73]:
            - generic [ref=e74]: Wage Payroll
            - generic [ref=e75]: —
        - generic [ref=e76]:
          - 'button "Color theme: System (light). Click to cycle light, dark, and system." [ref=e77] [cursor=pointer]': System (light)
          - button "Account menu AD" [ref=e79] [cursor=pointer]:
            - generic [ref=e80]: Account menu
            - text: AD
      - main [ref=e81]:
        - generic [ref=e82]:
          - generic [ref=e83]:
            - heading "Platform settings" [level=1] [ref=e84]
            - link "← Dashboard" [ref=e85] [cursor=pointer]:
              - /url: /app
          - paragraph [ref=e86]: Global deployment settings (all tenants). Changes are audited on the server. Secret fields are stored as plain text in platform_setting — restrict operator access accordingly.
          - navigation "Settings sections" [ref=e88]:
            - button "General" [ref=e89] [cursor=pointer]
            - button "MinIO" [ref=e90] [cursor=pointer]
            - button "Mail API" [ref=e91] [cursor=pointer]
          - paragraph [ref=e92]: "Request failed: 400 {\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,\"detail\":\"MAIL_API_NOT_CONFIGURED\",\"instance\":\"/api/v1/platform/settings/mail/test\",\"code\":\"MAIL_API_NOT_CONFIGURED\",\"traceId\":\"2006c705-7d19-4"
          - generic [ref=e93]:
            - heading "Mail API" [level=2] [ref=e94]
            - generic [ref=e95]:
              - text: Mail provider API base URL
              - textbox "Mail provider API base URL" [ref=e96]:
                - /placeholder: https://api.mailprovider.com/v1
            - generic [ref=e97]:
              - text: Project key
              - textbox "Project key" [ref=e98]
            - generic [ref=e99]:
              - text: Username
              - textbox "Username" [ref=e100]
            - generic [ref=e101]:
              - text: Password
              - textbox "Password" [ref=e102]
            - button "Save mail" [ref=e103] [cursor=pointer]
            - generic [ref=e104]:
              - paragraph [ref=e105]: Uses the currently saved Mail API settings and sends a test message to one recipient.
              - generic [ref=e106]:
                - text: Test recipient
                - textbox "Test recipient" [ref=e107]:
                  - /placeholder: recipient@example.com
                  - text: qa@example.com
              - button "Send test mail" [ref=e108] [cursor=pointer]
              - button "Save + send test" [ref=e109] [cursor=pointer]
```

# Test source

```ts
  5   |  *
  6   |  * Env: `PLAYWRIGHT_API_BASE_URL` (e.g. `http://127.0.0.1:8300`) — Spring API must be up; the browser only talks to Next
  7   |  * (`/api/bff/...`). Ensure Next dev has `API_BASE_URL` (see `frontend/.env.example`).
  8   |  *
  9   |  * Port: `PLAYWRIGHT_PORT` (default 3007) must match `returnTo` / tenant URLs below.
  10  |  */
  11  | const port = process.env.PLAYWRIGHT_PORT ?? "3007";
  12  | const hasApi = !!process.env.PLAYWRIGHT_API_BASE_URL;
  13  | 
  14  | test.describe("M1 — theming", () => {
  15  |   test("login page exposes theme toggle (no API required)", async ({ page }) => {
  16  |     await page.goto(`http://auth.lvh.me:${port}/login`);
  17  |     await expect(page.getByTestId("theme-toggle")).toBeVisible();
  18  |   });
  19  | });
  20  | 
  21  | test.describe("M1 — platform public surface (auth shell)", () => {
  22  |   test("login page shows applicationName from GET /api/v1/platform/public-surface when API is up", async ({ page }) => {
  23  |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");
  24  | 
  25  |     await page.goto(`http://auth.lvh.me:${port}/login`);
  26  |     await expect(page.getByTestId("auth-marketing-product-name")).toContainText("Wage Payroll", { timeout: 20_000 });
  27  |   });
  28  | });
  29  | 
  30  | test.describe("M1 — auth host → tenant host session", () => {
  31  |   test("platform superadmin login redirects to admin.* /app (operator workspace)", async ({ page }) => {
  32  |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");
  33  | 
  34  |     await page.goto(`http://auth.lvh.me:${port}/login`);
  35  |     await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  36  | 
  37  |     await page.getByRole("textbox", { name: "Email" }).fill("admin@demo.lvh.me");
  38  |     await page.getByRole("textbox", { name: "Password" }).fill("ChangeMe!1");
  39  | 
  40  |     await Promise.all([
  41  |       page.waitForURL(new RegExp(`http://admin\\.lvh\\.me:${port}/app`)),
  42  |       page.getByRole("button", { name: "Continue" }).click(),
  43  |     ]);
  44  | 
  45  |     await expect(page.getByTestId("tenant-app-shell")).toBeVisible();
  46  |     await expect(page.getByTestId("me-email")).toHaveText("admin@demo.lvh.me");
  47  |     await expect(page.getByTestId("me-tenant")).toHaveText("—");
  48  |     await expect(page.getByTestId("me-platform-operator")).toHaveText("yes");
  49  |     await expect(page.getByTestId("tenant-switcher")).toBeVisible();
  50  |     await expect(page.getByTestId("tenant-link-demo")).toBeVisible();
  51  |     await expect(page.getByTestId("tenant-link-acme")).toBeVisible();
  52  | 
  53  |     await expect(page.getByTestId("billing-summary-section")).toBeVisible();
  54  |     await expect(page.getByTestId("billing-summary-forbidden")).toBeVisible();
  55  |   });
  56  | });
  57  | 
  58  | test.describe("Mail templates — platform list", () => {
  59  |   test("superadmin can open mail templates list and see invitation template", async ({ page }) => {
  60  |     test.skip(!hasApi, "Set PLAYWRIGHT_API_BASE_URL (e.g. http://127.0.0.1:8300) and start the API");
  61  | 
  62  |     await page.goto(`http://auth.lvh.me:${port}/login`);
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
> 105 |     await expect([204, 502]).toContain((await postReq).status());
      |                              ^ Error: expect(received).toContain(expected) // indexOf
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
  163 |     await expect(page.getByTestId("me-tenant")).toHaveText("demo");
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
```