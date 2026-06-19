# Suriname wage tax — agent prompts (copy-paste)

**Module authority:** [`docs/modules/suriname-wage-tax-rules.md`](../modules/suriname-wage-tax-rules.md)  
**Use in:** Cursor **Agent** chat (not Ask mode)  
**Command:** Start with `/feature` (or `/payroll-retro` for payroll-only context)

**Status (2026-06-18):** **P1 complete** — lump sum **1009** → **1024** and jubilee **1010** → **1048** are **Live** per [`suriname-wage-tax-rules.md`](../modules/suriname-wage-tax-rules.md) §6–§8. Prompts A–C below are retained for reference or partial reruns.

---

## Which prompt to use

| Goal | Copy section |
|------|----------------|
| **Both P1 gaps** (lump sum + jubilee) | [Prompt A](#prompt-a--p1-gaps-lump-sum--jubilee) |
| **Lump sum only** (1009 → 1024) | [Prompt B](#prompt-b--lump-sum-only-1009--1024) |
| **Jubilee only** (1010 + service-year table) | [Prompt C](#prompt-c--jubilee-only-1010) |
| **Spec first** (expand module before code) | [Prompt D](#prompt-d--expand-spec-first-no-code-yet) |
| **Review only** (no code) | [Prompt E](#prompt-e--gap-review-only-no-implementation) |

---

## Attachments (add via `@` in Cursor)

**Always attach:**

- `@docs/modules/suriname-wage-tax-rules.md`
- `@docs/prompts/PROJECT-CONTEXT.md`
- `@docs/modules/payroll-engine-country.md`
- `@docs/product/PAYROLL-GOLDEN-SCENARIO-SR.md`
- `@backend`

**If changing Liquibase/seeds:**

- `@docs/guides/SCHEMA-PERSISTENCE-PREFLIGHT.md`
- `@docs/guides/DATA-MODEL-STANDARDS.md`
- `@docs/guides/LIQUIBASE-RULES.md`

---

## Prompt A — P1 gaps (lump sum + jubilee)

```
/feature

Implement the P1 gaps from @docs/modules/suriname-wage-tax-rules.md §8.6:

1. Wire lump sum wage tax: gross **1009** → tax **1024** using `SR_PAYMENTS_AT_ONCE_YEAR`
2. Wire jubilee wage tax: gross **1010** using `SR_SERVICE_YEARS_17A_MONTH` (+ Art. 10 anniversary exemption per §4.4 if in scope)

Follow the module spec §4.3, §4.4, §6, §7. Match existing patterns in:
- SurinameTenantDerivedComponentService
- SurinameCountryRuleKeys
- SurinameWageTaxCalculator

Out of scope for this run:
- P3 benefits-in-kind, inspector approval
- Pre-Jul 2025 overtime (out of product scope per spec)
- Frontend/mobile unless required for payroll preview

Before coding: summarize goal, scope, out-of-scope in 3 bullets.
Add/update tests: SurinameWageTaxCalculatorTest, SurinamePayrollGoldenIT (or focused ITs).
Fix template **1024** FK to `SR_PAYMENTS_AT_ONCE_YEAR` if spec requires it.
Update the module doc §6/§8 when done.
```

---

## Prompt B — Lump sum only (1009 / 1024)

```
/feature

Implement §4.3 payment at once only from @docs/modules/suriname-wage-tax-rules.md:

- Gross template **1009** (`SUR_LUMP_SUM`)
- Wage tax template **1024** (`SUR_WAGE_TAX_LUMP_SUM`)
- Rule `SR_PAYMENTS_AT_ONCE_YEAR` (5/15/25/35% marginal ladder)

Wire through SurinameCountryRuleKeys, SurinameTenantDerivedComponentService, SurinameWageTaxCalculator.
Acceptance: module §7 scenario 3.

Out of scope: jubilee (1010), Art. 10 anniversary table, benefits-in-kind, UI changes.

Before coding: 3-bullet goal/scope/out-of-scope summary.
Add unit/IT tests with numeric expectations.
Fix **1024** platform_country_tax_rule_id FK if needed (audit §8.2).
Update module §6 and §8.6 when complete.

Before marking done: run from `backend/`:
`mvn test -Dtest=SurinameWageTaxCalculatorTest,SurinameTenantDerivedComponentServiceTest,SurinameJubileeSupportTest,SurinamePayrollGoldenIT`
```

---

## Prompt C — Jubilee only (1010)

```
/feature

Implement §4.4 jubilee from @docs/modules/suriname-wage-tax-rules.md:

- Gross template **1010** (`SUR_JUBILEE`); wage tax template **1048** (`SUR_WAGE_TAX_JUBILEE`)
- Rule `SR_SERVICE_YEARS_17A_MONTH` (`LEGACY_SERVICE_YEAR_TABLE`)
- Extend SurinameWageTaxCalculator to support service-year table kind

Apply Art. 10 anniversary exemption (§4.4 table) before taxing remainder, unless user defers — if employee service-years source is undefined, expand module doc with acceptance criteria BEFORE implementing.

Acceptance: module §7 scenario 4.

Out of scope: lump sum (1024), benefits-in-kind, inspector approval workflow.

Before coding: 3-bullet goal/scope/out-of-scope summary.
Add tests for at least one tenure band (e.g. 25 years).
Update module §6 and §8.6 when complete.

Before marking done: run from `backend/`:
`mvn test -Dtest=SurinameWageTaxCalculatorTest,SurinameTenantDerivedComponentServiceTest,SurinameJubileeSupportTest,SurinamePayrollGoldenIT`
```

---

## Prompt D — Expand spec first (no code yet)

```
/plan

Review @docs/modules/suriname-wage-tax-rules.md §8.6 P1 gaps and expand the module doc with implementation-ready acceptance criteria for:

1. Lump sum 1009/1024 — inputs, base amount, rule resolution, expected test amounts
2. Jubilee 1010 — where service years come from, exemption then tax steps, one worked example per tenure band

Do NOT write backend code in this run. Output: proposed additions to the module doc only.
```

---

## Prompt E — Gap review only (no implementation)

```
/payroll-retro

Gap analysis only — do not implement.

Compare @docs/modules/suriname-wage-tax-rules.md §8.6 to current code under @backend/src/main/java/com/wagepayroll/payroll/country/.

Report: what is still missing, file paths, and recommended implementation order.
```

---

## Quick reference — remaining gaps (spec §8.6)

| Priority | Gap | Status |
|----------|-----|--------|
| ~~**P1**~~ | Lump sum **1009** → **1024** | **Done** |
| ~~**P1**~~ | Jubilee **1010** → **1048** | **Done** |
| **P2** | Benefits-in-kind valuations (car, housing, meals) | Open |
| **P3** | Inspector-approval flag for Art. 17a regimes | Open |

For gap review only, use [Prompt E](#prompt-e--gap-review-only-no-implementation).
