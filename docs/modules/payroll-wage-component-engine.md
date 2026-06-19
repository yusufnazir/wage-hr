# Module: Payroll Wage Component Engine

**Feature slug:** `payroll-wage-component-engine`  
**Milestone:** M13 (schema + engine skeleton)  
**Related:** [`platform-countries.md`](./platform-countries.md) (ISO country catalog), [`payroll-org-structure.md`](./payroll-org-structure.md) (company `payroll_country`, employee, pay period runs), [`platform-settings.md`](./platform-settings.md) (operator / superadmin patterns for future platform UI), [`security.md`](./security.md) (`@RequiresPrivilege`, tenant isolation), [`audit.md`](./audit.md) (append-only events for future mutating APIs), long-form product design: [`../features/payroll-wage-component-engine-design.md`](../features/payroll-wage-component-engine-design.md)

---

## 1. Objective

Deliver a **configuration-driven payroll wage component** layer: statutory (country-owned) definitions are **platform-seeded and not tenant-editable**; tenant-defined earnings and deductions are created **from country templates** so tenants stay aligned with local payroll practice. The **calculation engine** remains generic (phases, metadata, country providers) while **Suriname (`SR`)** is the first concrete `CountryRuleProvider`.

---

## 2. Scope (this milestone)

**Included**

- Liquibase DDL for component definitions, templates, tax-rule parameters, tenant components, input transactions, immutable result lines, and balance tables (structure only for balances).
- Seed data: Suriname statutory placeholder components, templates with numeric `template_code` (`1001` base salary, `1002` overtime, `1003` loan repayment, plus law/catalog rows `1004`–`1044`), and one placeholder `platform_country_tax_rule` row.
- Privileges: `WAGE_COMPONENT_VIEW`, `WAGE_COMPONENT_MANAGE` (tenant catalog), granted to demo tenant roles and ADMIN role template (same pattern as pay period / bank templates).
- JPA entities and repositories under `com.wagepayroll.domain.wagecomponent`.
- Engine skeleton: `PayrollEngine` / `DefaultPayrollEngine`, `CountryRuleProvider` / `SurinameCountryRuleProvider`, `CountryRuleProviderRegistry`.
- Tenant REST API for wage components (template-only): list/get, `POST` create from template (`codeSuffix` + optional `name`), slim `PUT` (name, code suffix, ledgers, payslip, active), `PATCH` active; responses include `templateCode` for UI.

**Excluded (follow-up)**

- Full phased calculation, formula engine (see §11), ledger posting writers, balance mutation services.

- Historical payroll recalculation UI and finalized-run locking (documented as requirements below).
- Employee-level **recurring / periodic** payroll inputs and how they **materialize** into `tenant_wage_component_transaction` — see agent guide [`../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md`](../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md) and module [`employee-periodic-payroll-transactions.md`](./employee-periodic-payroll-transactions.md).

---

## 3. Product rules

| Layer | Who edits | Notes |
|--------|-----------|--------|
| `platform_wage_component` | Platform operators only (future API; today Liquibase seed) | Rows with `statutory = true` model **tax and statutory slots** for a `country_code`. Tenants **never** insert/update/delete these. |
| `platform_wage_component_template` | Platform operators only (seed) | Presents **guided defaults** (`definition_defaults_json`) when a tenant creates a `tenant_wage_component`. |
| `platform_country_tax_rule` | Platform operators only (seed) | Versioned parameters (JSON) consumed by country providers; not tenant-editable. |
| `tenant_wage_component` | Tenant (privilege-gated) | **Template-only:** rows are always created from `platform_wage_component_template` (`platform_template_id` required). Full calculation, taxability, phase, formula, and template description are **not** tenant-mutable; tenants may edit **name**, **code suffix** (stored code = `template_code` or `template_code` + `_` + normalized suffix), **debit/credit tenant ledger**, **print_on_payslip**, and **active**. |

**Company boundary:** Tenant wage components are scoped to `tenant_id` + `company_id`, consistent with [`payroll-org-structure.md`](./payroll-org-structure.md) (company as payroll/tax boundary).

---

## 4. Data model (strict tables)

### `platform_wage_component`

Platform catalog of wage **slots** (earnings, deductions, employer contributions) for a country. Key fields: `country_code`, `code` (unique per country), `component_type`, `category`, `net_effect`, multi-dimensional taxability flags, `calculation_method`, `phase`, `processing_order` (ordering for statutory catalog display; engine semantics are separate), optional balance and ledger metadata, `statutory`, `effective_from` / `effective_to`, `active`.

### `platform_wage_component_template`

Per-country **template_code** (unique with country), `definition_defaults_json` (suggested type, category, phase, **list sort** via `processingOrder`, taxability, balance hints), and **`processing_order_hint`** mirroring that sort for the template row. **Sort keys** use bands of 1000 (`WageComponentSortBand`): Gross Earnings (1000), Gross Deductions (2000), Non-Taxable Earnings (3000), Tax Adjustments (4000), Statutory Deductions (5000), Net Deductions (6000), Employer Contributions (7000), System Calculations (8000, net wage last at ~8010). Offsets within a band (e.g. 1010, 1020) leave room for new templates. Canonical values live in `WageComponentSortOrder` and are applied on template save, tenant instantiation, and company catalog provisioning. They control UI list order (wage components, employee payroll inputs); engine calculation order still follows dependencies and phases.

### `platform_country_tax_rule`

Country tax / social parameter rows: `rule_code`, `effective_from` / `effective_to`, `parameters_json`.

#### 4.1 `parameters_json` — Suriname (v2)

**Seeding strategy:** one `platform_country_tax_rule` row per logical tariff (`rule_code`), each with compact JSON (stays within `VARCHAR(4000)`). Templates that need a single FK continue to reference **`SR_WAGE_TAX_DEFAULT`** (`52000000-0000-0000-0000-000000000001`). Additional rules use deterministic UUIDs `…000002`–`…00000c` (Liquibase `data-m25-platform-country-tax-rules-sr-1.xml`).

| Field | Meaning |
|-------|--------|
| `v` | Schema version; **2** for current Suriname payloads. |
| `legacyTariffTypeId` | Legacy tariff type id from country **740** exports (traceability). |
| `freq` | Accrual basis from legacy: **`YEAR`** or **`MONTH`**. |
| `kind` | **`MARGINAL_RATES`** (rows with `i`, `pct`, `min`, optional `max`), **`FLAT_RATE`** (`pct`), **`THRESHOLD_AMOUNT`** (`amount`), **`AMOUNT_BAND`** (`min`, `max`), **`PER_CHILD_MONTHLY`** (`perChild`, `maxAmount` — **1008** gross = `children × perChild`; **1023** / wage tax uses `min(gross, maxAmount)` per Art. 10(h); standing **quantity** on **1008** = number of children), **`LEGACY_SERVICE_YEAR_TABLE`** (`rows` with `i`, `pct`, `lo`, `hi` in **years**; last row may omit `hi`), **`PLACEHOLDER`** (no rates; awaiting data). |
| `source` | Provenance tag (e.g. `legacy-740-2024`). |
| `rows` | Ladder rows; amounts are legacy numeric values as in source exports (engine interprets currency/scale later). |

**Engine:** `SurinameTaxRuleResolutionService` selects one row per `rule_code` using `PayrollContext#countryRulesAsOf` (typically pay-period **end**; `TenantPayrollFormulaPreviewService` passes `tenant_pay_period.end_date`). When `countryRulesAsOf` is null, Suriname defaults to **UTC today**. `SurinameCountryRuleProvider` exposes **`sr.resolvedTaxRulesJson`** (full snapshot), **`sr.resolvedTaxRuleCount`**, and **`sr.primaryTaxRuleId`** / **`sr.primaryTaxRuleCode`** from the resolved `SR_WAGE_TAX_DEFAULT` row. **Platform operators** maintain definitions via **`GET/POST/PUT/PATCH /api/v1/platform/country-tax-rules`** and the tenant web shell at **`/app/platform-country-tax-rules`** (platform superadmin only). Parsing marginal tax from JSON for phased calculation remains future work.

### `tenant_wage_component`

Tenant-owned definition; optional `platform_template_id` FK. Same behavioral columns as platform rows where applicable (calculation, phase, taxability, balance hints). Includes `calculation_method`, `percentage_base`, and `formula_expression` (see §11).

### `tenant_wage_component_transaction`

Runtime input: employee, pay period, optional pay period run, tenant component, quantity/rate/amount, `manual_override`, remarks.

### `tenant_payroll_result_line`

Immutable **output** line per pay-period run, employee, and component reference (`component_source` = `PLATFORM` | `TENANT`, `component_ref_id` = UUID of the respective definition).

### `tenant_wage_component_balance` / `tenant_wage_component_balance_transaction`

Running balance header and append-only history (loan, reserve, etc.); populated by future balance services after payroll finalization.

---

## 5. Security

- All tenant persistence is scoped by `tenant_id` (and `company_id` where applicable); same enforcement pattern as [`security.md`](./security.md) for future `GET/POST/PUT` wage-component APIs.
- Planned privileges: **`WAGE_COMPONENT_VIEW`**, **`WAGE_COMPONENT_MANAGE`** (already in `DefinedPrivilege` and Liquibase).
- Platform maintenance of statutory rows (when exposed) follows **platform superadmin** gating per [`platform-settings.md`](./platform-settings.md); tenants receive **read-only** projection of active statutory components for their company’s `payroll_country`.

---

## 6. Audit (future APIs)

When mutating APIs ship, follow [`audit.md`](./audit.md): append-only `audit_event`, minimal PII in `metadata_json`, stable `action_code` values (to be listed alongside DTOs). Suggested `resource_type` values are registered in `AuditResourceTypes`: `PLATFORM_WAGE_COMPONENT`, `PLATFORM_WAGE_COMPONENT_TEMPLATE`, `PLATFORM_COUNTRY_TAX_RULE`, `TENANT_WAGE_COMPONENT`, `TENANT_WAGE_COMPONENT_TRANSACTION`, `TENANT_PAYROLL_RESULT_LINE`, `TENANT_WAGE_COMPONENT_BALANCE`, `TENANT_WAGE_COMPONENT_BALANCE_TRANSACTION`.

---

## 7. Engine architecture

### 7.1 Components (current)

| Type | Responsibility |
|------|----------------|
| `PayrollEngine` | Single entry: `PayrollRunResult calculate(PayrollContext)`. |
| `DefaultPayrollEngine` | Delegates to `PayrollRunOrchestrator`. |
| `PayrollRunOrchestrator` | Runs phase handlers in order (Phase 1). |
| `PayrollRunState` | Mutable state for one calculation pass. |
| Phase handlers | `ContextPhaseHandler`, `GrossAndBasesPhaseHandler`, `StatutoryPhaseHandler` (stub), `NetAndAccumulatorsPhaseHandler` (stub). |
| `CountryRuleProvider` | Enriches `CountryRuleContext` with hints (SR: resolved tax JSON). See [`payroll-engine-country.md`](./payroll-engine-country.md). |
| `CountryRuleProviderRegistry` | Lookup by ISO-2. |
| `PayrollBaseAccumulator` | Maps evaluated amounts + `tenant_wage_component_base_effect` → per-employee base code totals. |

**Processing flow (target):** context → tenant component amounts → **calculation bases** → statutory rules → NET + persist → ledger/balances. Legacy `taxable_*` / `net_effect` are deprecated in favor of base-effect rows.

**Phased delivery:** [`../product/PAYROLL-ENGINE-ROADMAP.md`](../product/PAYROLL-ENGINE-ROADMAP.md). **Phase 1 spec:** [`../product/PAYROLL-ENGINE-PHASE-1.md`](../product/PAYROLL-ENGINE-PHASE-1.md).

### 7.2 Four-phase pipeline (Phase 1 — implemented)

Aligned with [ADR-PE-001](../decisions/ADR-PE-001-payroll-execution-model.md):

```text
PayrollContext
    → PayrollRunOrchestrator
        → CONTEXT          (country provider, variables, statutory catalog count)
        → GROSS_AND_BASES  (tenant evaluation + PayrollBaseAccumulator)
        → STATUTORY        (stub in P1; SR tax in Phase 2)
        → NET_AND_ACCUMULATORS (stub in P1; persist/YTD in Phases 4–5)
    → PayrollRunResult
```

| Phase | Enum | Phase 1 behavior |
|-------|------|------------------|
| 1 | `CONTEXT` | `CountryRuleProvider.contribute`; fill `PayrollRunState.variables` |
| 2 | `GROSS_AND_BASES` | Existing tenant formula evaluation + `employeeBaseTotals` |
| 3 | `STATUTORY` | No-op (no change to preview `items`) |
| 4 | `NET_AND_ACCUMULATORS` | No-op |

**New types (Phase 1 implementation):** `PayrollRunOrchestrator`, `PayrollRunPhase`, `PayrollRunState`, `PayrollPhaseHandler` + four handlers under `payroll.engine.phase`. Details: [`../product/PAYROLL-ENGINE-PHASE-1.md`](../product/PAYROLL-ENGINE-PHASE-1.md) §4–5.

**API:** `POST .../formula-preview` response **unchanged** (`items`, `employeeBaseTotals` only).

### 7.3 Acceptance criteria — Phase 1 (orchestrator)

| ID | Criterion |
|----|-----------|
| AC-PE1-1 | Single `calculate()` executes phases 1→2→3→4 in order |
| AC-PE1-2 | Formula preview response shape unchanged |
| AC-PE1-3 | No country ISO literals in orchestrator / `DefaultPayrollEngine` |
| AC-PE1-4 | `PayrollEngine` bean remains `DefaultPayrollEngine` |
| AC-PE1-5 | Statutory stub does not add preview line items |

Regression data: [`../product/PAYROLL-GOLDEN-SCENARIO-SR.md`](../product/PAYROLL-GOLDEN-SCENARIO-SR.md).

### 7.4 Later phases (reference only)

| Roadmap phase | Engine change |
|---------------|---------------|
| 2 | `STATUTORY` computes SR wage tax from `LOONBELASTING` base |
| 3 | Topological order via `*_wage_component_dependency` |
| 4 | Persist `tenant_payroll_result_line` |
| 5 | NET closure + YTD |
| 6–7 | Formula validate API + UI |

---

## 8. Implementation snapshot

| Area | Location |
|------|-----------|
| DDL wrapper | `backend/src/main/resources/db/changelog/ddl/schema-wage-components.xml` |
| Per-table DDL | `backend/src/main/resources/db/changelog/ddl/create-table-*.xml` (platform/tenant wage component family) |
| Changelog master | `backend/src/main/resources/db/changelog/db.changelog-master.yaml` |
| Seed + privileges | `backend/src/main/resources/db/changelog/dml/data-m13-*.xml` |
| SR country tax rules (multi-row) | `backend/src/main/resources/db/changelog/dml/data-m25-platform-country-tax-rules-sr-1.xml` |
| Domain / JPA | `backend/src/main/java/com/wagepayroll/domain/wagecomponent/` |
| Payroll enums | `backend/src/main/java/com/wagepayroll/payroll/model/` |
| Engine | `backend/src/main/java/com/wagepayroll/payroll/engine/` |
| Formula (validate + evaluate + preview in engine) | `backend/src/main/java/com/wagepayroll/payroll/formula/` |
| Country tax resolution (SR) | `backend/src/main/java/com/wagepayroll/payroll/country/SurinameTaxRuleResolutionService.java` |
| Platform country tax rules (SuperAdmin API) | `backend/src/main/java/com/wagepayroll/api/PlatformCountryTaxRulesController.java`, `PlatformCountryTaxRuleAdminService` |
| Smoke test | `backend/src/test/java/com/wagepayroll/payroll/DefaultPayrollEngineIT.java` |

Liquibase for this module follows the repo **greenfield** assumption (empty DB + full changelog); see `docs/guides/LIQUIBASE-RULES.md` §1.

---

## 9. Acceptance criteria (M13)

| ID | Criterion |
|----|-----------|
| AC-1 | Liquibase applies all wage-component DDL changesets without error on test profile. |
| AC-2 | Suriname statutory components and templates exist after migrate; `platform_country_tax_rule` has at least one SR placeholder row. |
| AC-3 | `WAGE_COMPONENT_VIEW` / `WAGE_COMPONENT_MANAGE` exist in the privilege catalog and stay in sync with `DefinedPrivilege` (`PrivilegeCatalogSyncIT`). |
| AC-4 | `DefaultPayrollEngine` resolves three active SR statutory definitions in tests. |
| AC-5 | `SurinameCountryRuleProvider` contributes primary tax rule hints when a rule row is present. |

---

## 10. Open questions (deferred)

- **Suriname:** statutory brackets and thresholds are **seeded** (M25, `parameters_json` v2 per §4.1); **payroll engine** still must apply them in phased tax calculation (not only hints).
- Whether tenant **custom** components (no template) are allowed in v1 or template-only. **Resolved:** template-only; `platform_template_id` is required for create; update and active patch require a template link (`TEMPLATE_REQUIRED` when missing).
- Platform UI for editing statutory rows vs. Liquibase-only operations until compliance review completes.

---

## 11. Formula editor (planned)

This section is the **repository contract** for a tenant-facing (and later platform-facing) **formula editor** and for the **formula evaluation** step of the payroll engine. It aligns with FDD fields `calculationMethod`, `percentageBase`, and `formulaExpression`, and with the summary under **Formula editor and expression contract** in [`../features/payroll-wage-component-engine-design.md`](../features/payroll-wage-component-engine-design.md) §3.1. Runtime inputs and materialization of recurring lines belong to [`employee-periodic-payroll-transactions.md`](./employee-periodic-payroll-transactions.md) and [`../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md`](../prompts/AGENT-GUIDE-EMPLOYEE-PERIODIC-PAYROLL-TRANSACTIONS.md).

### 11.1 Goals

- Let tenants define earnings/deductions whose **amount** is derived from **named payroll inputs** (e.g. periodic salary rate, hours worked, hourly rate) and arithmetic, without arbitrary code execution.
- Keep definitions **auditable**: stored text must parse to a deterministic structure, validate on save, and evaluate the same way on recalculation.
- Reuse existing columns: `calculation_method` (`CalculationMethod` enum), `percentage_base`, `formula_expression` on `tenant_wage_component` and `platform_wage_component`.

### 11.2 Non-goals (v1)

- Embedded scripting languages (JavaScript, Groovy), database callbacks, or network calls from formulas.
- Unbounded cross-component references without explicit dependency edges — Phase 3 adds `component("CODE").amount` with graph validation ([`payroll-component-dependencies.md`](./payroll-component-dependencies.md)).

### 11.3 Representation

**Canonical form (v1 recommendation):** store a **versioned JSON document** in `formula_expression` (or migrate to a dedicated `TEXT` / JSON column if the 500-character limit becomes binding). Example top-level shape (illustrative, not final schema until implemented):

```json
{
  "version": 1,
  "kind": "expr",
  "root": { "op": "mul", "left": { "ref": "transaction.quantity" }, "right": { "ref": "transaction.rate" } }
}
```

**Alternative acceptable for early delivery:** a **strict expression DSL** (identifiers, literals, `+ - * / ( )`, and a fixed set of functions such as `min`, `max`, `round`) stored as a single line in `formula_expression`, with **no** `eval`—only a hand-written parser in the backend.

The UI may offer **presets** (e.g. “Periodic rate”, “Quantity × rate”) that emit the same canonical JSON or DSL string.

### 11.4 Variable catalog (bindings)

Formulas may reference **only identifiers** from an allowlist exposed to the editor and enforced in `PUT` validation. Initial catalog (names illustrative until aligned with compensation and transaction DTOs):

| Binding | Meaning |
|---------|--------|
| `compensation.periodic_rate` | Periodic base amount from employee compensation for the pay context. |
| `compensation.hourly_rate` | Hourly rate: `wage_amount` for `PER_HOUR`, else `periodic_rate ÷ contract hours per period` from linked work time. |
| `compensation.is_hourly` | `1` when compensation is per hour, else `0` (for `if(...)` base-salary style formulas). |
| `transaction.quantity` | Quantity on the materialized `tenant_wage_component_transaction` (e.g. hours, units). |
| `transaction.rate` | Rate on the same transaction (e.g. hourly rate). |
| `transaction.amount` | Stored line amount when the formula is not the sole source (use sparingly; avoid circular definitions). |
| `definition.default_amount` | `default_amount` on the component definition when applicable. |

Country-specific extensions may add identifiers only through **documented** engine/context contracts (e.g. tax-rule outputs on `CountryRuleContext`), not ad-hoc free text.

### 11.5 `CalculationMethod` interaction

| Method | Role |
|--------|------|
| `MANUAL_INPUT` | Amount comes from operator input / override; `formula_expression` typically empty. |
| `FIXED_AMOUNT` | Default or fixed configuration; formula optional. |
| `HOURLY` | **Preset** for “quantity × rate” (may be implemented as a fixed formula or as dedicated engine branch; behavior must match §11.4). |
| `PERCENTAGE` | Requires `percentage_base` to name the base; may combine with a small formula for caps if allowed later. |
| `FORMULA` | Requires a valid parsed `formula_expression` per §11.3. |

Product choice: either keep `HOURLY` as a shorthand that writes the standard mul expression, or implement it only in the engine; document the chosen behavior in release notes when the engine ships.

### 11.6 API and validation

- **`POST /api/v1/pay-periods/{id}/formula-preview`** (`PAY_PERIOD_VIEW`): body `{ "employeeIds": ["uuid", ...] }` returns `{ "items": [...] }` with evaluated tenant wage lines for that pay period (same engine path as `DefaultPayrollEngine` previews).
- **Tenant and platform** mutating endpoints must **reject** invalid formulas: parse failure, unknown identifier, excessive depth/size, or disallowed ops.
- Error responses should return a stable machine-readable code and a human-readable message (for inline editor feedback).
- **Length:** today `formula_expression` is `VARCHAR(500)` in DDL; if canonical JSON exceeds this, add a Liquibase changeset to widen or add `formula_expression_json` / `TEXT` before shipping rich formulas.

### 11.7 UI (shipped on platform templates)

- **Platform template** create/edit **Definition** tab: [`PlatformDefinitionDefaultsEditor`](../../frontend/src/components/payroll/PlatformDefinitionDefaultsEditor.tsx) — calculation method selector, [`WageComponentFormulaEditor`](../../frontend/src/components/wage-components/WageComponentFormulaEditor.tsx) (token bar, Monaco, validate panel), collapsible **Advanced** full JSON.
- **Presets:** periodic rate, hours × rate, default amount, **composite example** (multi-term formula referencing `component("1001")`, `component("1002")`, `compensation.periodic_rate`, `transaction.*`).
- **Dependencies** tab: declare every `component("CODE")` prerequisite before save (`FORMULA_MISSING_DEPENDENCY`).
- **Tenant** wage component edit: formula read-only; validate panel only.

#### Composite formula example (operator entry)

Business rule:

`(10% of component A) + (20% of component B) × (10% periodic wage) + (hour rate × overtime hours)`

DSL (codes `1001` / `1002` for A / B):

```text
component("1001").amount * 0.10
+ (component("1002").amount * 0.20) * (compensation.periodic_rate * 0.10)
+ transaction.rate * transaction.quantity
```

Set `calculationMethod` to `FORMULA`, paste via editor or **Composite example** preset, add Dependencies on `1001` and `1002`, validate with mock amounts, ensure pay-period transactions supply `transaction.quantity` / `transaction.rate` for overtime.

#### Criteria-based formula rules (template 1001)

When `formulaMode` is `CRITERIA_RULES`, `definition_defaults_json` (platform) and tenant `formula_expression` store a JSON wrapper:

```json
{
  "formulaMode": "CRITERIA_RULES",
  "formulaRules": [
    {
      "criteriaType": "WAGE_TYPE",
      "itemKey": "PER_HOUR",
      "itemLabel": "Per hour",
      "formulaExpression": "transaction.quantity * transaction.rate"
    }
  ],
  "defaultFormulaExpression": "compensation.periodic_rate"
}
```

At payroll time `FormulaRuleResolver` picks the **first matching rule** (list order) using employee wage type, department code, or job code; if none match, evaluates `defaultFormulaExpression`. Legacy plain-string formulas remain supported.

Platform editor: **Calculation rules** UI (`WageComponentCriteriaFormulaEditor`) on the Definition tab. Department/job rules store **codes** (e.g. `OPS`), not UUIDs.

### 11.8 Engine evaluation

- **`FormulaEvaluationContext`** (`com.wagepayroll.payroll.formula`): immutable numeric bindings `compensationPeriodicRate`, `transactionQuantity`, `transactionRate`, `transactionAmount`, `definitionDefaultAmount` (missing → zero for arithmetic).
- **`WageComponentFormulaEvaluator`**: evaluates DSL or JSON v1 payloads; final scale **4** decimals using `java.math.RoundingMode` mapped from `rounding_strategy`.
- **`PayrollContext`** includes optional **`payPeriodId`** and optional **`countryRulesAsOf`** (calendar date for versioned `platform_country_tax_rule` selection; formula preview sets this from the pay period’s **end date**). When set together with a non-empty employee list, **`DefaultPayrollEngine`** loads period transactions and compensation, evaluates **FORMULA** (via evaluator), **HOURLY** (quantity × rate), **FIXED_AMOUNT** (default amount), and **MANUAL_INPUT** (transaction amount); **PERCENTAGE** lines are left to future tax/base logic. Results are returned as **`PayrollRunResult#evaluatedComponentAmounts`** (`EvaluatedComponentAmount` per employee × tenant component).
- Country rule hints and phased posting remain future work; this slice is deterministic preview math only.
- Unit tests: `WageComponentFormulaValidatorTest`, `WageComponentFormulaEvaluatorTest`, `DefaultPayrollEngineIT`.

---

## 12. Full design reference

The long-form **feature design** (concepts, phases, balance behavior, ledger, extensibility, phased priorities) is the product/engineering narrative titled *Payroll Wage Component Engine — Feature Design Document* (sections 1–16). This module doc is the **repository implementation contract** for what is shipped in code and Liquibase; the FDD remains the conceptual checklist for future milestones (formula engine, multi-country, recalculation, and so on). **§11** narrows the formula editor and evaluation contract for implementation.
