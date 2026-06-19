# Payroll engine documentation index

Quick navigation for the **spec → repo** documentation set. Implement only after the relevant phase spec and module ADRs are **Accepted**.

| Order | Document | Purpose |
|-------|----------|---------|
| — | [`PAYROLL-ENGINE-ROADMAP.md`](./PAYROLL-ENGINE-ROADMAP.md) | Master phased plan, gaps, principles |
| 0 | [`../decisions/ADR-PE-001`](../decisions/ADR-PE-001-payroll-execution-model.md) | Phases + forward topo |
| 0 | [`../decisions/ADR-PE-002`](../decisions/ADR-PE-002-formula-expression-runtime.md) | DSL + Java calculators |
| 0 | [`../decisions/ADR-PE-003`](../decisions/ADR-PE-003-processing-order-vs-graph.md) | Sort vs graph |
| 0 | [`../decisions/ADR-REVIEW-CHECKLIST.md`](../decisions/ADR-REVIEW-CHECKLIST.md) | Sign-off gate |
| 0 | [`PAYROLL-GOLDEN-SCENARIO-SR.md`](./PAYROLL-GOLDEN-SCENARIO-SR.md) | Demo regression case |
| 1 | [`PAYROLL-ENGINE-PHASE-1.md`](./PAYROLL-ENGINE-PHASE-1.md) | Four-phase orchestrator |
| 2 | [`PAYROLL-ENGINE-PHASE-2.md`](./PAYROLL-ENGINE-PHASE-2.md) | SR wage tax from bases |
| 3 | [`PAYROLL-ENGINE-PHASE-3.md`](./PAYROLL-ENGINE-PHASE-3.md) | Dependency graph |
| 3 | [`../modules/payroll-component-dependencies.md`](../modules/payroll-component-dependencies.md) | DDL + API (schema authority) |
| 4 | [`PAYROLL-ENGINE-PHASE-4.md`](./PAYROLL-ENGINE-PHASE-4.md) | Persist result lines |
| 5 | [`PAYROLL-ENGINE-PHASE-5.md`](./PAYROLL-ENGINE-PHASE-5.md) | NET + YTD |
| 6–7 | [`PAYROLL-ENGINE-PHASE-6-7.md`](./PAYROLL-ENGINE-PHASE-6-7.md) | Validate API + UI |
| 8 | [`PAYROLL-ENGINE-PHASE-8.md`](./PAYROLL-ENGINE-PHASE-8.md) | Ledger posting + balances |

### Module specs (ongoing)

| Module | Slug |
|--------|------|
| [`payroll-wage-component-engine.md`](../modules/payroll-wage-component-engine.md) | Core engine, formulas, components |
| [`payroll-calculation-bases.md`](../modules/payroll-calculation-bases.md) | Base effects |
| [`payroll-engine-country.md`](../modules/payroll-engine-country.md) | Country adapters (SR) |
| [`suriname-wage-tax-rules.md`](../modules/suriname-wage-tax-rules.md) | SR seven wage-tax regimes + audit |
| [`suriname-loonbelasting-art17-bonus-vakantie.md`](../modules/suriname-loonbelasting-art17-bonus-vakantie.md) | Art. 17 vacation/bonus |
| [`../datafiles/README-suriname-tax-data.md`](../datafiles/README-suriname-tax-data.md) | Legacy CSV ↔ `rule_code` index |
| [`pay-periods.md`](../modules/pay-periods.md) | Periods, runs, preview |
| [`payroll-ledger-posting.md`](../modules/payroll-ledger-posting.md) | Ledger + balance (Phase 8) |

### Implementation order

```text
Phase 0 sign-off → Phase 1 → Phase 2 → (3 ∥ 4 after 2) → 5 → 6 → 7 → 8
```
