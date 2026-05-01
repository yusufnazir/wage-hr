# Run: account-registration (MASTER-FEATURE-END-TO-END)

Copy the block below into a **new Cursor chat**. Replace **`@wage-payroll`** with your repository root folder name as attached with **`@`** (the folder that contains `docs/`, `backend/`, `frontend/`, and the mobile project).

**Prerequisites:** [`docs/modules/account-registration.md`](../modules/account-registration.md) is the sole behavioral + schema authority. [`PROJECT-CONTEXT.md`](./PROJECT-CONTEXT.md) feature row points to this module (already updated in-repo).

---

```text
Run feature (Phases 0–4): @docs/prompts/MASTER-FEATURE-END-TO-END.md — filled values: @docs/prompts/PROJECT-CONTEXT.md — module (sole behavioral + schema authority): @docs/modules/account-registration.md — @docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md — @docs/guides/DATA-MODEL-STANDARDS.md — docs: @docs — repo root: @wage-payroll

Existing codebase: foundation (scaffold + security) is already in place. Execute MASTER-FEATURE-END-TO-END for this feature using the single attached module doc as the contract — backend (depth of docs/templates/4. BACKEND-FEATURE-PROMPT.md), web (5), mobile (6), then Phase 4 verification. Read docs/guides/README.md and docs/output/ARCHITECTURE-DEFINITION.md; if architecture conflicts with PROJECT-CONTEXT or the module doc, follow PROJECT-CONTEXT + module. Do not merge schema assumptions from other module docs. Do not use MASTER-FOUNDATION-TO-FEATURES or greenfield scaffold Phases 2–3 unless the module explicitly requires net-new stack work. If the module doc is incomplete, expand it with acceptance criteria before implementing — do not shrink scope silently. When done, update cross-linked module docs (auth, role-admin, platform-settings, user, web-auth-session, tenancy-routing) if behavior changed, add or refresh docs/output/FEATURE-account-registration-VERIFICATION.md if used, and confirm builds/tests for backend, web, and mobile.
```
