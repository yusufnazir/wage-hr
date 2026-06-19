# Phase 1 — Four-phase orchestrator (implementation spec)

**Status:** Implemented (orchestrator + phase handlers; STATUTORY/NET stubs)  
**Roadmap:** [`PAYROLL-ENGINE-ROADMAP.md`](./PAYROLL-ENGINE-ROADMAP.md) Phase 1  
**Authority:** [`../modules/payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md) §7, [`../decisions/ADR-PE-001-payroll-execution-model.md`](../decisions/ADR-PE-001-payroll-execution-model.md)  
**Regression:** [`PAYROLL-GOLDEN-SCENARIO-SR.md`](./PAYROLL-GOLDEN-SCENARIO-SR.md) Phase 1 expectations

---

## 1. Objective

Refactor payroll calculation so every `PayrollEngine.calculate(PayrollContext)` run executes **four explicit phases** in order ([ADR-PE-001](../decisions/ADR-PE-001-payroll-execution-model.md)). **Behavior** for formula preview must remain **backward compatible** with today: same `items` and `employeeBaseTotals` for the demo golden case.

Phases 3 (`STATUTORY`) and 4 (`NET_AND_ACCUMULATORS`) are **stubs** in Phase 1 (no new amounts, no persistence).

---

## 2. Prerequisites

- [ ] ADR-PE-001, ADR-PE-002, ADR-PE-003 marked **Accepted** in [`../decisions/README.md`](../decisions/README.md).
- [ ] Schema preflight: **no Liquibase** required for Phase 1.
- [ ] Read golden scenario § Phase 1 expected outputs.

---

## 3. Package layout (target)

```text
com.wagepayroll.payroll.engine
  PayrollEngine.java                    (unchanged interface)
  DefaultPayrollEngine.java             (delegates to orchestrator)
  PayrollRunOrchestrator.java             (NEW — phase pipeline)
  PayrollRunPhase.java                    (NEW — enum)
  PayrollRunState.java                    (NEW — mutable run state)
  PayrollContext.java                     (unchanged)
  PayrollRunResult.java                   (unchanged record)
  phase/
    PayrollPhaseHandler.java              (NEW — interface)
    ContextPhaseHandler.java              (NEW)
    GrossAndBasesPhaseHandler.java        (NEW — move logic from DefaultPayrollEngine)
    StatutoryPhaseHandler.java            (NEW — stub)
    NetAndAccumulatorsPhaseHandler.java   (NEW — stub)
```

**Bean wiring:** Spring `@Component` handlers injected into `PayrollRunOrchestrator`; `DefaultPayrollEngine` remains the `@Service` implementing `PayrollEngine` for existing injection sites (`TenantPayrollFormulaPreviewService`).

---

## 4. Types

### 4.1 `PayrollRunPhase`

```java
public enum PayrollRunPhase {
  CONTEXT,
  GROSS_AND_BASES,
  STATUTORY,
  NET_AND_ACCUMULATORS
}
```

Ordinal execution order = enum declaration order (document in Javadoc).

### 4.2 `PayrollRunState`

Mutable object created once per `calculate()` call. Suggested fields:

| Field | Type | Set by phase |
|-------|------|--------------|
| `context` | `PayrollContext` | constructor (immutable) |
| `countryRuleContext` | `CountryRuleContext` | CONTEXT |
| `variables` | `Map<String, Object>` | CONTEXT+ (extensible; see §4.3) |
| `resolvedStatutoryComponentCount` | `int` | CONTEXT or GROSS (catalog count) |
| `resolvedTenantComponentCount` | `int` | GROSS_AND_BASES |
| `evaluatedComponentAmounts` | `List<EvaluatedComponentAmount>` | GROSS_AND_BASES (tenant lines only in P1) |
| `employeeBaseTotals` | `Map<UUID, Map<String, BigDecimal>>` | GROSS_AND_BASES |
| `statutoryEvaluatedAmounts` | `List<EvaluatedComponentAmount>` | STATUTORY (empty in P1) |
| `countryHints` | `Map<String, String>` | CONTEXT (copy from `countryRuleContext.hintsView()`) |

**Thread safety:** one state instance per request; not shared across threads.

**Builders:** no Lombok required; plain Java with package-private setters or phase handlers as inner collaborators with accessor methods on state.

### 4.3 `variables` map (initial keys)

Populated in **CONTEXT** for downstream phases and debugging. Values must be JSON-serializable if ever exposed on API (not in Phase 1).

| Key | Type | Source |
|-----|------|--------|
| `payrollCountryIso2` | `String` | context |
| `currencyIso3` | `String` | context |
| `countryRulesAsOf` | `LocalDate` or null | context |
| `tenantId` | `UUID` | context |
| `companyId` | `UUID` | context |
| `payPeriodId` | `UUID` or null | context |
| `employeeIds` | `List<UUID>` | context |

Phase 2+ may add `surinameTaxRulesSnapshot` object reference (not in preview API).

### 4.4 `PayrollPhaseHandler`

```java
public interface PayrollPhaseHandler {
  PayrollRunPhase phase();
  void execute(PayrollRunState state);
}
```

Orchestrator:

```java
for (PayrollRunPhase p : PayrollRunPhase.values()) {
  handlerFor(p).execute(state);
}
```

Handlers must **not** skip phases or call each other directly.

### 4.5 `PayrollRunOrchestrator`

```java
public PayrollRunResult run(PayrollContext context) {
  PayrollRunState state = new PayrollRunState(context);
  for (PayrollPhaseHandler handler : handlersInOrder) {
    handler.execute(state);
  }
  return state.toResult();
}
```

`toResult()` maps to existing `PayrollRunResult` constructor:

- `resolvedStatutoryComponentCount`
- `resolvedTenantComponentCount`
- `evaluatedComponentAmounts` — **tenant only in Phase 1** (statutory list empty; do not merge into `items` until Phase 2 defines API)
- `employeeBaseTotals`

---

## 5. Phase behavior (Phase 1)

### 5.1 CONTEXT

| Step | Action |
|------|--------|
| 1 | Construct `CountryRuleContext` from `PayrollContext` |
| 2 | `countryRuleProviderRegistry.forCountry(iso2).ifPresent(p -> p.contribute(ctx))` |
| 3 | Copy hints to `state.countryHints` |
| 4 | Fill `state.variables` (§4.3) |
| 5 | Set `resolvedStatutoryComponentCount` = count active `platform_wage_component` for country (same query as today) |

**Optional enhancement (allowed):** load `TenantCompanyEntity` / `TenantPayPeriodEntity` once into variables for later phases — avoid duplicate repository reads in GROSS_AND_BASES.

**Forbidden:** country-specific `if ("SR")` — use registry only.

### 5.2 GROSS_AND_BASES

Move **existing** logic from `DefaultPayrollEngine.evaluateTenantComponents` + `PayrollBaseAccumulator.accumulateForEmployees`:

| Step | Action |
|------|--------|
| 1 | If `payPeriodId` null or `employeeIds` empty → leave lists/maps empty (same as today) |
| 2 | Load tenant components `findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc` |
| 3 | Set `resolvedTenantComponentCount` |
| 4 | Evaluate amounts → `evaluatedComponentAmounts` |
| 5 | Run accumulator → `employeeBaseTotals` |

**Ordering:** still `processing_order` until Phase 3 (ADR-PE-003 fallback).

### 5.3 STATUTORY (stub)

| Step | Action |
|------|--------|
| 1 | No-op OR debug log `"STATUTORY phase stub"` |
| 2 | `statutoryEvaluatedAmounts` remains empty |
| 3 | Do **not** append statutory lines to preview `items` yet |

Phase 2 will call `SurinameWageTaxCalculator` here ([`payroll-engine-country.md`](../modules/payroll-engine-country.md)).

### 5.4 NET_AND_ACCUMULATORS (stub)

| Step | Action |
|------|--------|
| 1 | No-op OR debug log |
| 2 | Do not persist `tenant_payroll_result_line` |
| 3 | Do not compute final NET adjustment beyond base accumulator |

---

## 6. API compatibility

### Unchanged contracts

| Surface | Rule |
|---------|------|
| `POST /api/v1/pay-periods/{id}/formula-preview` | Response `data.items` + `data.employeeBaseTotals` only |
| `TenantPayPeriodFormulaPreviewResultDto` | No new fields |
| `EvaluatedComponentAmountDto` | Unchanged |
| `PayrollRunResult` record | Unchanged public shape |

### Internal-only (allowed)

- SLF4J `DEBUG` lines: `Payroll phase {} complete for tenant {} period {}`
- New types under `payroll.engine` not exposed on REST

### Forbidden in Phase 1

- Adding `phasesExecuted`, `countryHints`, or `variables` to preview JSON
- Changing decimal scale on amounts

---

## 7. Tests

| Test class | Purpose |
|------------|---------|
| `PayrollRunOrchestratorTest` | Unit test with test doubles: verify four handlers invoked in order exactly once |
| `DefaultPayrollEngineIT` | Existing tests pass; add golden scenario assertions per [`PAYROLL-GOLDEN-SCENARIO-SR.md`](./PAYROLL-GOLDEN-SCENARIO-SR.md) if demo data present in test DB |
| `GrossAndBasesPhaseHandlerTest` | Optional: extract evaluation logic tests from engine IT |

### Golden assertions (when demo seed loaded)

For employee `5fa00000-0000-4000-8000-000000000006`, period `5fa00000-0000-4000-8000-00000000000c`:

- Component `1001` amount = `18500.0000`
- `employeeBaseTotals[GROSS]` = `18500.0000`
- `employeeBaseTotals[LOONBELASTING]` = `18500.0000`

Use `@Transactional` test profile; skip if seed not present (`@EnabledIf` or conditional assert).

---

## 8. Acceptance criteria

| ID | Criterion | Verification |
|----|-----------|--------------|
| AC-PE1-1 | Phases run CONTEXT → GROSS_AND_BASES → STATUTORY → NET | `PayrollRunOrchestratorTest` |
| AC-PE1-2 | Formula preview JSON unchanged vs pre-refactor baseline | `TenantPayPeriodsIT` + manual golden POST |
| AC-PE1-3 | No `if ("SR")` / `equals("SR")` in `DefaultPayrollEngine` or `PayrollRunOrchestrator` | grep / arch unit test |
| AC-PE1-4 | `DefaultPayrollEngine` still sole `PayrollEngine` bean | Spring context IT |
| AC-PE1-5 | Statutory phase does not alter `items` list size vs today | Compare IT item count |

---

## 9. Implementation checklist (developer)

- [ ] Create `PayrollRunPhase`, `PayrollRunState`, `PayrollPhaseHandler`
- [ ] Create four phase handler classes
- [ ] Create `PayrollRunOrchestrator` with ordered handler list
- [ ] Slim `DefaultPayrollEngine.calculate` to `return orchestrator.run(context)`
- [ ] Run `mvnw test -Dtest=DefaultPayrollEngineIT,TenantPayPeriodsIT,PayrollRunOrchestratorTest`
- [ ] Grep orchestrator package for `"SR"` country literals
- [ ] Update module doc §7.2 “Current implementation” bullet to mention orchestrator (after merge)

---

## 10. Rollback / feature flag

No feature flag required: behavior must match preview. If regression found, revert orchestrator commit; keep phase classes behind package-private until stable.

---

## 11. Document history

| Date | Change |
|------|--------|
| 2026-05-17 | Initial Phase 1 implementation spec |
