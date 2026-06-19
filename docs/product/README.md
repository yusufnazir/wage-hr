# Product documentation hub

Human-maintained **scope, sequencing, and progress** for wage-payroll. Methodology guides stay in [`../guides/README.md`](../guides/README.md); per-slice specs live in [`../modules/`](../modules/) as you implement.

| File | Purpose |
|------|---------|
| [BUILD-CHECKLIST.md](./BUILD-CHECKLIST.md) | Ordered milestones and checkboxes — primary execution tracker |
| [MODULE-INDEX.md](./MODULE-INDEX.md) | Planned `docs/modules/{slug}.md` slices — create each doc when starting that slice |
| [PAYROLL-ENGINE-DOCS-INDEX.md](./PAYROLL-ENGINE-DOCS-INDEX.md) | **Start here** — links to all payroll engine phase specs + ADRs |
| [PAYROLL-ENGINE-ROADMAP.md](./PAYROLL-ENGINE-ROADMAP.md) | Generic payroll engine: spec → repo phases (DAG, 4-phase run, SR tax, persist, YTD, UI) — **document before implement** |
| [PAYROLL-GOLDEN-SCENARIO-SR.md](./PAYROLL-GOLDEN-SCENARIO-SR.md) | Demo tenant golden case for engine regression (provisional) |
| [PAYROLL-ENGINE-PHASE-1.md](./PAYROLL-ENGINE-PHASE-1.md) | Phase 1 orchestrator — implementation spec (ready after ADR sign-off) |
| [PAYROLL-ENGINE-PHASE-2.md](./PAYROLL-ENGINE-PHASE-2.md) | Phase 2 SR wage tax from bases (after Phase 1) |
| [PAYROLL-ENGINE-PHASE-3.md](./PAYROLL-ENGINE-PHASE-3.md) | Phase 3 dependency graph |
| [PAYROLL-ENGINE-PHASE-4.md](./PAYROLL-ENGINE-PHASE-4.md) | Phase 4 persist result lines |
| [PAYROLL-ENGINE-PHASE-5.md](./PAYROLL-ENGINE-PHASE-5.md) | Phase 5 NET + YTD |
| [PAYROLL-ENGINE-PHASE-6-7.md](./PAYROLL-ENGINE-PHASE-6-7.md) | Phases 6–7 validate API + UI (shipped) |
| [PAYROLL-ENGINE-PHASE-8.md](./PAYROLL-ENGINE-PHASE-8.md) | Phase 8 ledger posting + balances (shipped) |
| [../decisions/README.md](../decisions/README.md) | Payroll engine ADRs (PE-001 … PE-003) |
| [../decisions/ADR-REVIEW-CHECKLIST.md](../decisions/ADR-REVIEW-CHECKLIST.md) | Phase 0→1 review gate |

**Authority order:** [`docs/prompts/PROJECT-CONTEXT.md`](../prompts/PROJECT-CONTEXT.md) (architecture contract) overrides older generated architecture until you re-run Phase 1 and refresh `docs/output/ARCHITECTURE-DEFINITION.md`.

**Decisions:** All current product choices live under *Resolved decisions* in [BUILD-CHECKLIST.md](./BUILD-CHECKLIST.md) (including gating, inbox, retention).
