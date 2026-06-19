# Module: Payroll calculation bases

**Feature slug:** `payroll-calculation-bases`  
**Milestone:** M5 (extends [`payroll-wage-component-engine.md`](./payroll-wage-component-engine.md))  
**Related:** [`payroll-engine-country.md`](./payroll-engine-country.md) (target), [`platform-countries.md`](./platform-countries.md), [`pay-periods.md`](./pay-periods.md)

---

## 1. Objective

Replace simplistic wage-component flags (`taxable_wage_tax`, `taxable_social_security`, `taxable_pension`, `taxable_vacation_reserve`, `net_effect`) with a **rule-driven** model: each component declares **how it affects one or more payroll calculation bases** (GROSS, NET, LOONBELASTING, AOV, etc.) via explicit effect rows.

Payroll processing becomes:

```text
Wage component amounts → base effects → accumulated bases → tax/contribution rules → result lines
```

not a single hardcoded `gross → tax → net` pipeline.

---

## 2. Scope (this slice)

**Included**

- Platform catalog `platform_payroll_base` (seeded codes).
- Effect tables: `platform_wage_component_template_base_effect`, `platform_wage_component_base_effect`, `tenant_wage_component_base_effect`.
- Column `impact_side` on `platform_wage_component`, `platform_wage_component_template` (via defaults JSON), and `tenant_wage_component`.
- Liquibase migration from legacy boolean flags + `net_effect` into effect rows (idempotent).
- JPA entities, repositories, `PayrollBaseAccumulator` used by `DefaultPayrollEngine` when evaluating period previews.
- Copy template effects to tenant row on `TenantWageComponentService` provision/create.

**Excluded (follow-up)**

- Deprecating/dropping legacy flag columns (kept for backward compatibility until UI and seeds move fully to effects).
- Platform superadmin CRUD UI for bases and template effects.
- Tenant-editable effects (tenants remain template-only for tax behavior).
- PERCENTAGE / FORMULA effect types in the accumulator beyond parsing (FULL and IGNORE paths are implemented; others reserved).
- Persisted per-run base snapshots (`tenant_payroll_result_line` extension).

---

## 3. Product rules

| Layer | Who edits | Notes |
|--------|-----------|--------|
| `platform_payroll_base` | Platform operators (seed; future API) | Global catalog; `code` unique. Optional `category` groups bases (TAX, CONTRIBUTION, ACCRUAL, …). |
| `platform_wage_component_template_base_effect` | Platform operators (seed; future API) | Default effects when tenants instantiate a template. |
| `platform_wage_component_base_effect` | Platform operators | Statutory `platform_wage_component` rows. |
| `tenant_wage_component_base_effect` | System copy on provision | Copied from template at tenant component create; not tenant-mutable in v1. |

**`impact_side`:** `EMPLOYEE` (default), `EMPLOYER`, or `BOTH` — which side of the payroll relationship the component amount applies to for reporting and future employer-liability bases.

---

## 4. Data model (allowed tables and columns)

### `platform_payroll_base`

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | UUID |
| `code` | VARCHAR(50) NOT NULL | none | Unique (e.g. `GROSS`, `LOONBELASTING`) |
| `name` | VARCHAR(255) NOT NULL | none | Display label |
| `category` | VARCHAR(50) | none | Optional: TAX, CONTRIBUTION, ACCRUAL, NET, GROSS, STATUTORY |
| `active` | BOOLEAN NOT NULL | none | Default true |
| `created_at` | TIMESTAMP NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

### `platform_wage_component_template_base_effect`

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | |
| `platform_wage_component_template_id` | VARCHAR(36) FK NOT NULL | none | → `platform_wage_component_template` |
| `platform_payroll_base_id` | VARCHAR(36) FK NOT NULL | none | → `platform_payroll_base` |
| `effect_direction` | VARCHAR(20) NOT NULL | none | `INCREASE`, `DECREASE`, `IGNORE` |
| `effect_calculation_type` | VARCHAR(20) NOT NULL | none | `FULL`, `PERCENTAGE`, `FIXED`, `FORMULA` |
| `effect_value` | DECIMAL(18,6) | none | Meaning depends on type (e.g. 100 for FULL %) |
| `priority` | INT NOT NULL | none | Default 0 |
| `effective_from` | DATE | none | Optional |
| `effective_until` | DATE | none | Optional |
| `active` | BOOLEAN NOT NULL | none | |
| `created_at` | TIMESTAMP NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

Unique: (`platform_wage_component_template_id`, `platform_payroll_base_id`).

### `platform_wage_component_base_effect`

Same columns as template effects, with `platform_wage_component_id` FK → `platform_wage_component`.  
Unique: (`platform_wage_component_id`, `platform_payroll_base_id`).

### `tenant_wage_component_base_effect`

| Column | Type | PII | Notes |
|--------|------|-----|--------|
| `id` | VARCHAR(36) PK | none | |
| `tenant_id` | VARCHAR(36) NOT NULL | low | Tenant scope |
| `tenant_wage_component_id` | VARCHAR(36) FK NOT NULL | none | |
| `platform_payroll_base_id` | VARCHAR(36) FK NOT NULL | none | |
| `effect_direction` | VARCHAR(20) NOT NULL | none | |
| `effect_calculation_type` | VARCHAR(20) NOT NULL | none | |
| `effect_value` | DECIMAL(18,6) | none | |
| `priority` | INT NOT NULL | none | |
| `effective_from` | DATE | none | |
| `effective_until` | DATE | none | |
| `active` | BOOLEAN NOT NULL | none | |
| `created_at` | TIMESTAMP NOT NULL | none | |
| `updated_at` | TIMESTAMP NOT NULL | none | |

Unique: (`tenant_id`, `tenant_wage_component_id`, `platform_payroll_base_id`).

### Column additions (legacy compatibility)

| Table | Column | Type | Notes |
|-------|--------|------|--------|
| `platform_wage_component` | `impact_side` | VARCHAR(20) NOT NULL DEFAULT `EMPLOYEE` | |
| `tenant_wage_component` | `impact_side` | VARCHAR(20) NOT NULL DEFAULT `EMPLOYEE` | |

**Deprecated (do not use in new code):** `taxable_wage_tax`, `taxable_social_security`, `taxable_pension`, `taxable_vacation_reserve`, `net_effect` on platform/tenant wage components and corresponding keys in `definition_defaults_json`. Migration task backfills effects; engine reads effects first.

---

## 5. Enums

| Enum | Values |
|------|--------|
| `PayrollBaseEffectDirection` | `INCREASE`, `DECREASE`, `IGNORE` |
| `PayrollBaseEffectCalculationType` | `FULL`, `PERCENTAGE`, `FIXED`, `FORMULA` |
| `PayrollImpactSide` | `EMPLOYEE`, `EMPLOYER`, `BOTH` |

---

## 6. Legacy → base mapping (migration)

Used by `DataMigrateLegacyTaxFlagsToBaseEffects` (Liquibase):

| Legacy signal | Base code | Direction | Type | Value |
|---------------|-----------|-----------|------|-------|
| `taxable_wage_tax` true | `LOONBELASTING` | INCREASE | FULL | 100 |
| `taxable_wage_tax` false | `LOONBELASTING` | IGNORE | FULL | 0 |
| `taxable_social_security` true | `AOV`, `AWW`, `SZF` | INCREASE | FULL | 100 each |
| `taxable_pension` true | `PENSION` | INCREASE | FULL | 100 |
| `taxable_vacation_reserve` true | `VACATION` | INCREASE | FULL | 100 |
| `net_effect` ADD_TO_NET | `NET` | INCREASE | FULL | 100 |
| `net_effect` SUBTRACT_FROM_NET | `NET` | DECREASE | FULL | 100 |
| `net_effect` NO_EFFECT | `NET` | IGNORE | FULL | 0 |
| `component_type` EARNING and `phase` GROSS | `GROSS` | INCREASE | FULL | 100 |

---

## 7. Engine

`PayrollBaseAccumulator` loads active `tenant_wage_component_base_effect` rows for evaluated components, resolves `platform_payroll_base.code`, and applies each evaluated component amount to an in-memory `Map<String, BigDecimal>` keyed by base code. `DefaultPayrollEngine` attaches this map per employee to `PayrollRunResult#employeeBaseTotals` when period preview runs.

Tax rules (`platform_country_tax_rule`) consume relevant base totals in a later milestone; this slice does not change tax JSON parsing.

**Phased delivery:** Engine consumption of bases for SR statutory tax, PERCENTAGE/FORMULA effect types, and legacy flag retirement are scheduled in [`../product/PAYROLL-ENGINE-ROADMAP.md`](../product/PAYROLL-ENGINE-ROADMAP.md) (Phases 2, 5, and cross-phase deprecation).

**SR wage tax input (Phase 2):** `SurinameWageTaxCalculator` reads per-employee total for base code **`LOONBELASTING`** (see [`payroll-engine-country.md`](./payroll-engine-country.md) §5.2). Golden expected bases: [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md).

---

## 8. Security and audit

- Tenant effect rows scoped by `tenant_id`; no new tenant REST endpoints in this slice.
- Future platform APIs: platform superadmin only; audit resource types TBD.

---

## 9. Acceptance

- Liquibase applies on clean DB and existing dev DBs.
- Seeded `platform_payroll_base` rows exist for SR payroll bases.
- Existing templates and tenant components receive effect rows after migration.
- Formula preview (`POST .../pay-periods/{id}/formula-preview`) returns `employeeBaseTotals` when components and effects exist.
