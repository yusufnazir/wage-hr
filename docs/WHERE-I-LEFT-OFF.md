# Where I left off

**Last updated:** 2026-06-18

| Field | Value |
|-------|-------|
| **Last completed** | Option B — `SurinameStatutoryContributorTest` P4 / AOV edge cases |
| **Also done (committed)** | Option A — `SurinamePayrollGoldenIT` + P4 demo standing seed |
| **Working on next** | Commit Option B if desired; next Suriname tax scope TBD |
| **Branch** | `main` (uncommitted Option B) |
| **Do not redo** | P4 Phases E–H; Option A golden IT / demo seed |

## P4 — Art. 10 exclusions

Spec: [`docs/modules/suriname-wage-tax-rules.md`](modules/suriname-wage-tax-rules.md) §5.3

| Phase | Templates | Status |
|-------|-----------|--------|
| **E** | **1064** / **1065** | **Live** |
| **F** | **1058** / **1059** | **Live** |
| **G** | **1060** / **1061** | **Live** |
| **H** | **1062** / **1063** | **Live** |

**P4 v1 scope is complete** (all **1058**–**1065** Live). Still out of scope per spec: Art. 10(f) pension withholding exclusion, Belastingdienst approval workflows, evidence storage.

## Test coverage (recent)

| Option | Deliverable | Status |
|--------|-------------|--------|
| **A** | `SurinamePayrollGoldenIT` (baseline + P2 + P4 pairs), `DemoP4ExclusionStandingSeeder`, Art.10 catalog provisioner | Committed & pushed |
| **B** | `SurinameStatutoryContributorTest` — P4 exclusion stacking, pension 2×AOV cap, vacation AOV base, golden AOV premium, missing snapshot | Local (uncommitted) |

## Agent quick start

```
@docs/WHERE-I-LEFT-OFF.md
@docs/modules/suriname-wage-tax-rules.md

Review Option B tests or pick next compliance item from §8 roadmap.
```
