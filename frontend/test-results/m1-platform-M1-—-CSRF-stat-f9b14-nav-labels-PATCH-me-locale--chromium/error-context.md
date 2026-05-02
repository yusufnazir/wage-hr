# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: m1-platform.spec.ts >> M1 — CSRF + state change (locale PATCH) >> changing locale updates nav labels (PATCH /me/locale)
- Location: e2e\m1-platform.spec.ts:129:7

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Gebruikers')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for getByText('Gebruikers')

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
            - button "Beheer" [expanded] [ref=e25] [cursor=pointer]:
              - generic [ref=e26]: Beheer
              - img [ref=e27]
            - list [ref=e29]:
              - listitem [ref=e30]:
                - link "Tenants" [ref=e31] [cursor=pointer]:
                  - /url: /app/platform-tenants
                  - img [ref=e32]
                  - generic [ref=e35]: Tenants
              - listitem [ref=e36]:
                - link "Roltemplates" [ref=e37] [cursor=pointer]:
                  - /url: /app/platform-role-templates
                  - img [ref=e38]
                  - generic [ref=e41]: Roltemplates
              - listitem [ref=e42]:
                - link "Valuta's" [ref=e43] [cursor=pointer]:
                  - /url: /app/platform-currencies
                  - img [ref=e44]
                  - generic [ref=e47]: Valuta's
              - listitem [ref=e48]:
                - link "Landen" [ref=e49] [cursor=pointer]:
                  - /url: /app/platform-countries
                  - img [ref=e50]
                  - generic [ref=e54]: Landen
              - listitem [ref=e55]:
                - link "Mailsjablonen" [ref=e56] [cursor=pointer]:
                  - /url: /app/platform-mail-templates
                  - img [ref=e57]
                  - generic [ref=e59]: Mailsjablonen
              - listitem [ref=e60]:
                - link "Platforminstellingen" [ref=e61] [cursor=pointer]:
                  - /url: /app/platform-settings
                  - img [ref=e62]
                  - generic [ref=e65]: Platforminstellingen
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
      - generic [ref=e81]:
        - paragraph [ref=e82]: Kies een tenant om tenanttools op deze host te laden (platformoperator).
        - generic [ref=e83]:
          - generic [ref=e84]: Tenantlens
          - combobox "Tenantlens" [ref=e85]:
            - option "Kies tenant…" [selected]
            - option "acme — Acme Corp"
            - option "demo — Demo tenant"
      - main [ref=e86]:
        - generic [ref=e88]:
          - generic [ref=e89]:
            - heading "Your tenants" [level=2] [ref=e90]
            - paragraph [ref=e91]: Open another tenant in the same browser session (same relay cookies).
            - list [ref=e92]:
              - listitem [ref=e93]:
                - link "Acme Corp" [ref=e94] [cursor=pointer]:
                  - /url: http://acme.lvh.me:3007/app
                - text: · acme · Reader
              - listitem [ref=e95]:
                - link "Demo tenant" [ref=e96] [cursor=pointer]:
                  - /url: http://demo.lvh.me:3007/app
                - text: · demo · Admin
          - generic [ref=e97]:
            - heading "Current user" [level=2] [ref=e98]
            - paragraph [ref=e99]: Change language from the account menu (header).
            - generic [ref=e100]:
              - generic [ref=e101]:
                - term [ref=e102]: Email
                - definition [ref=e103]: admin@demo.lvh.me
              - generic [ref=e104]:
                - term [ref=e105]: Locale
                - definition [ref=e106]: nl
              - generic [ref=e107]:
                - term [ref=e108]: Tenant handle
                - definition [ref=e109]: —
              - generic [ref=e110]:
                - term [ref=e111]: Privileges
                - definition [ref=e112]: (none in this context)
              - generic [ref=e113]:
                - term [ref=e114]: Platform operator
                - definition [ref=e115]: "yes"
              - generic [ref=e116]:
                - term [ref=e117]: Plan features (subscription)
                - definition [ref=e118]: (none)
          - generic [ref=e119]:
            - heading "Billing integration" [level=2] [ref=e120]
            - paragraph [ref=e121]: "GET /api/bff/v1/tenant/billing/summary (**USER_VIEW**). Catalog: GET …/commercial-plans (**TENANT_SETTINGS_EDIT**). Read-only; no provider customer ids in summary."
            - paragraph [ref=e122]: Billing summary requires USER_VIEW in this tenant.
            - paragraph [ref=e123]: Commercial plan catalog (Stripe/PayPal ids) is limited to users with TENANT_SETTINGS_EDIT.
          - generic [ref=e124]:
            - heading "Privacy & data lifecycle" [level=2] [ref=e125]
            - paragraph [ref=e126]:
              - text: Subject export and erasure request (M1). See
              - code [ref=e127]: docs/modules/data-lifecycle.md
              - text: .
            - generic [ref=e128]:
              - button "Download JSON export" [ref=e129] [cursor=pointer]
              - button "Request account erasure" [ref=e130] [cursor=pointer]
          - generic [ref=e131]:
            - heading "Navigation (API)" [level=2] [ref=e132]
            - paragraph [ref=e133]: GET /api/bff/v1/me/navigation — filtered by privileges (sidebar mirrors this tree).
            - list [ref=e134]:
              - listitem [ref=e135]:
                - text: Beheer · nav.group.administration · (group)
                - list [ref=e136]:
                  - listitem [ref=e137]: Tenants · nav.platform_tenants · /app/platform-tenants
                  - listitem [ref=e138]: Roltemplates · nav.platform_role_templates · /app/platform-role-templates
                  - listitem [ref=e139]: Valuta's · nav.platform_currencies · /app/platform-currencies
                  - listitem [ref=e140]: Landen · nav.platform_countries · /app/platform-countries
                  - listitem [ref=e141]: Mailsjablonen · nav.platform_mail_templates · /app/platform-mail-templates
                  - listitem [ref=e142]: Platforminstellingen · nav.platform_settings · /app/platform-settings
          - generic [ref=e143]:
            - heading "Privilege check" [level=2] [ref=e144]
            - paragraph [ref=e145]: GET /api/bff/v1/tenant/users (requires USER_VIEW)
            - paragraph [ref=e146]: Denied or failed (HTTP 403).
```

# Test source

```ts
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
> 147 |     await expect(page.getByText("Gebruikers")).toBeVisible();
      |                                                ^ Error: expect(locator).toBeVisible() failed
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