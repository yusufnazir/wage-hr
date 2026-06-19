# Verification — Employee periodic payroll transactions

Manual smoke checks after deployment or local run (complements `TenantEmployeePayrollStandingInstructionsIT`).

## Preconditions

- Demo tenant (`demo.lvh.me`), user with `EMPLOYEE_PAYROLL_STANDING_VIEW` + `EMPLOYEE_PAYROLL_STANDING_MANAGE` (demo Admin).
- At least one company, department, job, employee group, employee, active tenant wage component, and pay period overlapping standing instruction effective dates.

## Steps

1. **Navigation** — Open tenant app; sidebar shows **Employee payroll inputs** when `EMPLOYEE_PAYROLL_STANDING_VIEW` is present (menu seed `nav.employee_payroll_inputs`).
2. **Standing instruction** — Select company and employee; create a standing instruction (fixed amount or quantity × rate) for an active wage component; list shows the row with effective dates and component name.
3. **Overlap guard** — Attempt a second overlapping active instruction for the same employee + component; API returns **409** (`STANDING_INSTRUCTION_OVERLAP`).
4. **Materialize** — Choose a pay period whose dates overlap the instruction; click **Generate period inputs**; JSON summary shows `created` ≥ 1 on first run.
5. **Idempotency** — Run **Generate period inputs** again; `created` and `updated` stay **0** if nothing changed; no duplicate rows for the same employee + period + component.
6. **Manual override** — In period transactions, mark a row with manual override (or use PUT with `manualOverride`); run materialize again; amount must **not** change; summary shows `skippedManualOverride` ≥ 1.
7. **Inactive component** — Deactivate the wage component; materialize returns **409** (`INACTIVE_WAGE_COMPONENT_MATERIALIZATION`).

## Automated tests

```text
cd backend && ./mvnw test -Dtest=TenantEmployeePayrollStandingInstructionsIT
```

## Builds

- Backend: `./mvnw test` (or at minimum `test-compile`).
- Frontend: `npm run lint` and `npm run build` from `frontend/`.
