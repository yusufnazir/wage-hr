# Run prompt: Tenant roles directory + edit (web v1)

Use this message in Cursor with the **`@`** attachments shown below. **Expand `docs/modules/role-admin.md` first** (sole behavioral + schema authority) with any missing API payload details, error codes, nav seed details, and acceptance criteria — then implement.

---

## Copy-paste (Cursor chat)

Replace `@wage-payroll` with your repository root folder name in Cursor.

```text
Run feature (Phases 0, 1, 2, 4 only — web only, skip mobile Phase 3): @docs/prompts/MASTER-FEATURE-END-TO-END.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — module (sole behavioral + schema authority): @docs/modules/role-admin.md — @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md — @docs/guides/DATA-MODEL-STANDARDS.md — docs: @docs — repo root: @wage-payroll

Existing codebase: foundation (scaffold + security) is already in place.

Implement a proper roles view for tenant admins and platform superadmins (via admin host + lens):
- Web routes:
  - /app/roles: roles list (search by name, sort by name)
  - /app/roles/[roleId]: separate role detail/edit view
- Privileges:
  - ROLE_VIEW: list + detail read-only
  - ROLE_EDIT: create + edit (name + privileges)
- Enforce tenant ceiling: role privileges assignable only from tenant effective pool (allowances ∪ subscription-derived).
- Superadmin tenant lens: works under admin.{BASE_DOMAIN} with X-Tenant-Id; mutating elevated calls require X-Break-Glass-Reason and are audited (existing break-glass rules).
- Add nav item /app/roles visible with ROLE_VIEW only.
- Add platform superadmin view-only page:
  - /app/platform-role-templates (admin.{BASE_DOMAIN}) lists the two templates (ADMIN, EMPLOYEE) and privilege sets.
- Tenant bootstrap on registration:
  - `POST /api/v1/auth/register` creates a new tenant, copies role templates into tenant roles, creates membership, assigns the default copied tenant role from platform setting `auth.registration.default_role_template_code` (default **ADMIN** when unset), and leaves email unverified until verify-email — see [`account-registration.md`](../modules/account-registration.md).

Execute with depth of docs/templates/4. BACKEND-FEATURE-PROMPT.md and docs/templates/5. WEB-FRONTEND-PROMPT.md. Read docs/guides/README.md and docs/output/ARCHITECTURE-DEFINITION.md; if architecture conflicts with PROJECT-CONTEXT or role-admin.md, follow PROJECT-CONTEXT + role-admin.md. Do not merge schema assumptions from other module docs. Do not use MASTER-FOUNDATION-TO-FEATURES unless role-admin.md requires net-new stack work.

When done: update docs/modules/role-admin.md (API notes, web flows, file pointers), and confirm backend + frontend builds/tests for touched areas.
```

---

## Attachments checklist

| Attach | Purpose |
|--------|---------|
| `docs/prompts/MASTER-FEATURE-END-TO-END.md` | Phase workflow + non-negotiables |
| `docs/prompts/PROJECT-CONTEXT.md` | `{FEATURE_NAME}` / contract tokens; ensure **Feature work** row points at `role-admin.md` |
| `docs/modules/role-admin.md` | Sole spec — extend before coding |
| `docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md` | Migrations / authority (needed if adding privilege codes via Liquibase) |
| `docs/guides/DATA-MODEL-STANDARDS.md` | Allowed columns / PII |
| `docs/` | Guides + architecture |
| Repo root | `backend/`, `frontend/`, etc. |

