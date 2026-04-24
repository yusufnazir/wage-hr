# Privilege model

Foundational security model for multi-tenant systems. Backend is the authority; frontend/mobile only reflect permissions.

## Definition

- **Privilege** = **ACTION** + **RESOURCE** (conceptually; naming convention is UPPER_SNAKE per action and resource).
- Examples: `USER_CREATE`, `USER_VIEW`, `ORDER_APPROVE`.
- **No hardcoded role checks** in business logic for authorization; use privilege checks (roles map to privilege sets).

## SuperAdmin

- SuperAdmin has **all** privileges that exist in the system.
- SuperAdmin MUST still go through the **same privilege validation** as every other user (no bypass, no hidden code paths).
- Every new feature MUST **register** its privileges so SuperAdmin receives them in the catalog.

## Privilege pools

- **Global pool** — defined by SuperAdmin: master list of privileges that may exist in the system.
- **Tenant pool** — subset assigned to a tenant; tenant admins assign roles from this pool.
- **Constraint:** tenant-allowed privileges ⊆ global pool (tenant cannot invent privileges outside what SuperAdmin exposed).

## Tenant admin

- May create roles, assign privileges from the **allowed tenant pool**, and define tenant-specific pools only where permitted by global policy.
- Cannot override SuperAdmin-defined global constraints.

## Enforcement

- **Backend:** every API endpoint MUST check privileges (or equivalent authorization) before executing work.
- **Frontend/mobile:** UI may hide or disable actions based on permissions; this is **never** sufficient alone — backend must deny as well.
- **Auth abuse:** login and sensitive auth endpoints MUST have rate limiting / brute-force protection with explicit, non-leaky responses when limited.

## Data model (reference)

Typical tables (names may vary by implementation): `users`, `roles`, `privileges`, `role_privileges`, `user_roles`, `privilege_pools` (global + tenant-scoped definitions).

## Liquibase

- Privilege and role schema changes follow [LIQUIBASE-RULES.md](./LIQUIBASE-RULES.md); seed/DML via approved custom changesets where required.
