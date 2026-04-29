# Run prompt: Tenant user directory + edit (web v1)

Use this message in Cursor with the **`@`** attachments shown below. **Expand `docs/modules/user.md` first** (sole behavioral + schema authority) with API payloads, query parameters, errors, **`## Proposed Schema Extension`** for membership **status** + **last active**, web routes, and acceptance criteria if anything is still missing — then implement.

---

## Copy-paste (Cursor chat)

Replace `@wage-payroll` with your repository root folder name in Cursor.

```text
Run feature (Phases 0, 1, 2, 4 only — web only, skip mobile Phase 3): @docs/prompts/MASTER-FEATURE-END-TO-END.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — module (sole behavioral + schema authority): @docs/modules/user.md — @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md — @docs/guides/DATA-MODEL-STANDARDS.md — docs: @docs — repo root: @wage-payroll

Existing codebase: foundation (scaffold + security) is already in place. Implement tenant user administration for the current tenant only.

Product summary:
- Replace placeholder /app/users (DemoUserViewBody / demo privileged call) with a real tenant user list backed by new APIs.
- List columns: email, last active, status, roles. Pagination page size 20. Search/filter: email, status, role. Sortable on all four columns (deterministic tie-break, e.g. user id; roles sort key = lexicographically lowest role name in tenant).
- USER_VIEW: list only — no successful edit UX; GET/PATCH detail for another user must fail without USER_EDIT (403). No links to /app/users/[userId] for viewers, or equivalent guard.
- USER_EDIT: separate edit view at /app/users/[userId] (user_account UUID). May change email (user_account) and assign/remove roles in tenant only (user_role). Editor cannot change their own roles (UI + server). Changing own email is allowed.
- Out of scope v1: invite by email, remove membership, create user, password reset for others, platform superadmin user admin, mobile.
- Reserve /app/users/new for future create/invite reusing the same form component as edit; v1 either omit Add user or show not-available — do not implement create APIs.
- Persistence: membership currently lacks status and last-active; add Liquibase + entities per module doc under Proposed Schema Extension; follow SCHEMA-PERSISTENCE-PREFLIGHT and DATA-MODEL-STANDARDS. Status v1 is read-only in UI (filter/sort/display) unless module doc explicitly adds suspend.

Execute with depth of docs/templates/4. BACKEND-FEATURE-PROMPT.md and docs/templates/5. WEB-FRONTEND-PROMPT.md. Read docs/guides/README.md and docs/output/ARCHITECTURE-DEFINITION.md; if architecture conflicts with PROJECT-CONTEXT or user.md, follow PROJECT-CONTEXT + user.md. Do not merge schema assumptions from other module docs. Do not use MASTER-FOUNDATION-TO-FEATURES unless user.md requires net-new stack work. Deprecate or narrow DemoController demo USER_VIEW endpoint if redundant; keep tests meaningful.

When done: update docs/modules/user.md (API notes, web flows, file pointers), add or refresh verification notes, confirm backend + frontend builds/tests for touched areas.
```

---

## Attachments checklist

| Attach | Purpose |
|--------|---------|
| `docs/prompts/MASTER-FEATURE-END-TO-END.md` | Phase workflow + non-negotiables |
| `docs/prompts/PROJECT-CONTEXT.md` | `{FEATURE_NAME}` / contract tokens; ensure **Feature work** row points at `user.md` |
| `docs/modules/user.md` | Sole spec — extend before coding |
| `docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md` | Migrations / authority |
| `docs/guides/DATA-MODEL-STANDARDS.md` | Allowed columns / PII |
| `docs/` | Guides + architecture |
| Repo root | `backend/`, `frontend/`, etc. |

---

## Optional follow-up

After implementation, regenerate or reconcile **`docs/output/ARCHITECTURE-DEFINITION.md`** if the checklist requires it for governance.
