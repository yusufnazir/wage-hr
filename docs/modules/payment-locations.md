# Module: Payment Locations

**Feature slug:** `payment-locations`
**Related:** [`payroll-org-structure.md`](./payroll-org-structure.md) (`tenant_company` — payment locations are scoped to a company), [`platform-bank-templates.md`](./platform-bank-templates.md) (`tenant_bank_template` — referenced by BANK_ACCOUNT locations), [`security.md`](./security.md), [`audit.md`](./audit.md)

---

## 1. Objective

Allow a company to define one or more **payment locations** — the disbursement channels (cash desks or company bank accounts) from which employee wages are paid. This gives payroll operators a structured, auditable reference point for how and where payroll money leaves the company, and lays the ground for employee pay-method assignment in a later milestone.

---

## 2. Scope

**Included:**
- Tenant-scoped CRUD for `tenant_payment_location` records, scoped to a company
- Two payment location types: `CASH` and `BANK_ACCOUNT`
- Currency (ISO-4217) on every payment location
- For `BANK_ACCOUNT` type: reference to a `tenant_bank_template` (identifying the bank + format) and the company's own account number at that bank
- Activate / deactivate (soft flag; no hard delete)
- Privilege enforcement: `PAYMENT_LOCATION_VIEW`, `PAYMENT_LOCATION_MANAGE`
- Audit logging on create, update, activate, deactivate
- Backend persistence + REST API
- Tenant web UI: list per company, create, edit, activate/deactivate
- Liquibase DDL + DML (privileges, navigation)

**Excluded:**
- Employee-to-payment-location assignment (a subsequent feature)
- Payroll disbursement execution or bank connectivity
- Validation of the account number against a live bank system
- Platform-level (superadmin) management of payment locations (these are fully tenant-managed)
- Mobile UI

---

## 3. Actors

| Actor | Privilege | Capability |
|---|---|---|
| Tenant Payroll Admin | `PAYMENT_LOCATION_MANAGE` | Full CRUD — create, edit, activate/deactivate payment locations |
| Tenant Payroll Operator | `PAYMENT_LOCATION_VIEW` | View payment locations (read-only) |
| SuperAdmin (tenant context) | All, via same enforcement path | Access only through audited privilege enforcement — no bypass |

---

## 4. User Flows

### 4.1 List Payment Locations

1. User navigates to **Company → Payment Locations** within a company context.
2. System displays a paginated list of payment locations for the selected company.
3. Each row shows: Name, Type (Cash / Bank Account), Currency, Bank Name (blank for CASH), Account Number masked (blank for CASH), Status (Active / Inactive), Actions.
4. List defaults to **Active only**; a toggle reveals inactive records.
5. User can filter by type (All / Cash / Bank Account).

### 4.2 Create a CASH Payment Location

1. User clicks **+ Add Payment Location**.
2. A form opens. User selects **Type = Cash**.
3. Required fields presented: Name, Currency (ISO-4217 searchable dropdown).
4. Bank-specific fields are hidden when Type = Cash.
5. On submit: system validates required fields → inserts record → audit event `PAYMENT_LOCATION_CREATED` → list refreshes with new entry highlighted.
6. On validation failure: inline field-level errors shown; no record created.

### 4.3 Create a BANK ACCOUNT Payment Location

1. User clicks **+ Add Payment Location**.
2. User selects **Type = Bank Account**.
3. Required fields presented: Name, Currency, Bank Template (dropdown of active `tenant_bank_template` records for the current company), Account Number.
4. Selecting a Bank Template auto-populates a read-only hint showing the bank name, SWIFT/BIC, and account number format for reference.
5. On submit: system validates all required fields; if `account_number_format` is present on the selected bank template, the account number is validated against it.
6. On success: record inserted; audit event `PAYMENT_LOCATION_CREATED`; list refreshes.
7. On validation failure: inline errors shown; no record created.

### 4.4 Edit a Payment Location

1. User clicks **Edit** on a payment location row (requires `PAYMENT_LOCATION_MANAGE`).
2. Form pre-populates with existing values.
3. **`payment_type` is read-only after creation** (changing type would invalidate linked bank data).
4. For BANK_ACCOUNT type: all editable fields (name, currency, bank template, account number) are shown; bank template dropdown is re-selectable.
5. On success: record updated; audit event `PAYMENT_LOCATION_UPDATED`.

### 4.5 Deactivate / Activate a Payment Location

1. User clicks **Deactivate** (or **Activate**) on a payment location row.
2. Confirmation dialog shown: *"Deactivate '[Name]'? This payment location will no longer be available for payroll operations."*
3. On confirm: `active` flag toggled; audit event `PAYMENT_LOCATION_DEACTIVATED` or `PAYMENT_LOCATION_ACTIVATED`.
4. Deactivated locations remain visible to users with manage privilege via the "Show inactive" toggle.

---

## 5. Data Model

### Table: `tenant_payment_location` (strict allowed columns)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | Generated |
| `tenant_id` | UUID | NOT NULL, FK → `tenant.id` | Set from `TenantContext`. Tenant isolation boundary. |
| `company_id` | UUID | NOT NULL, FK → `tenant_company.id` | Company this location belongs to. |
| `name` | VARCHAR(120) | NOT NULL | Human-readable label (e.g. "Main Cash Desk", "Hakrinbank Payroll Account"). Trimmed; 1–120 chars. PII classification: **none**. |
| `payment_type` | VARCHAR(20) | NOT NULL | Discriminator: `CASH` or `BANK_ACCOUNT`. **Immutable after creation.** |
| `currency` | CHAR(3) | NOT NULL | ISO-4217 uppercase (e.g. `SRD`, `USD`). PII classification: **none**. |
| `bank_template_id` | UUID | NULLABLE, FK → `tenant_bank_template.id` | Required when `payment_type = BANK_ACCOUNT`; NULL for CASH. Must belong to same tenant and company. |
| `account_number` | VARCHAR(60) | NULLABLE | Company's own account number at the bank. Required when `payment_type = BANK_ACCOUNT`; NULL for CASH. Trimmed; 1–60 chars. PII classification: **sensitive** (company financial identifier). |
| `active` | BOOLEAN | NOT NULL, DEFAULT true | Soft flag. False = deactivated; excluded from operational dropdowns. |
| `created_at` | TIMESTAMP | NOT NULL | Set on insert. |
| `updated_at` | TIMESTAMP | NOT NULL | Set on insert and update. |

**Indexes:**
- Index on `(tenant_id, company_id)` — primary query pattern.
- Index on `(tenant_id, company_id, active)` — filtered list (active only).
- Index on `bank_template_id` — FK lookup.

**Conceptual relationships:**
- `tenant_payment_location` N:1 → `tenant_company`
- `tenant_payment_location` N:1 → `tenant_bank_template` (nullable; only for `BANK_ACCOUNT` type)

---

## 6. States & Transitions

```
ACTIVE ──(deactivate)──► INACTIVE
INACTIVE ──(activate)──► ACTIVE
```

| State | Meaning |
|---|---|
| `ACTIVE` | Payment location is operational and available for payroll use |
| `INACTIVE` | Soft-deactivated; hidden from operational dropdowns; no hard delete |

- There is no `DRAFT` state — a payment location is active immediately upon creation.
- Hard delete is **not** supported in v1 (records may be referenced in payroll runs in future milestones).

---

## 7. Business Rules

| # | Rule |
|---|---|
| BR-1 | `name` must be unique per company (case-insensitive trim). |
| BR-2 | `payment_type` is immutable after creation. Any API request submitting a differing `payment_type` on an existing record is rejected with HTTP 400. |
| BR-3 | `currency` must be a valid ISO-4217 three-letter uppercase code. The system validates against the platform's known currency list. |
| BR-4 | When `payment_type = CASH`: `bank_template_id` and `account_number` must be NULL. |
| BR-5 | When `payment_type = BANK_ACCOUNT`: both `bank_template_id` and `account_number` are required and non-blank. |
| BR-6 | `bank_template_id` must reference a `tenant_bank_template` that belongs to the same `tenant_id` **and** the same `company_id`. |
| BR-7 | When the selected `tenant_bank_template` has a non-null `account_number_format`, the submitted `account_number` must match that format. The format is applied server-side as a regex. If the format cannot be compiled as a regex, it is treated as informational and validation is skipped. |
| BR-8 | A `tenant_bank_template` that is inactive may NOT be selected when creating or editing a payment location. Existing records that already reference a now-deactivated template retain the link but trigger a UI warning. |
| BR-9 | Only payment locations belonging to the authenticated user's tenant and the requested company are accessible. Cross-tenant and cross-company access is forbidden. |

---

## 8. Edge Cases

| # | Scenario | Expected Behaviour |
|---|---|---|
| EC-1 | User attempts to create a second payment location with the same name in the same company | Rejected with a field-level error: *"A payment location with this name already exists for this company."* |
| EC-2 | User submits `BANK_ACCOUNT` type without selecting a bank template | Rejected: `bank_template_id` is required. |
| EC-3 | User submits `BANK_ACCOUNT` type with an account number that does not match the bank template's `account_number_format` | Rejected with a field-level error referencing the expected format. |
| EC-4 | The `tenant_bank_template` referenced by an existing payment location is later deactivated | The payment location record is NOT affected (retains its FK). The UI shows a warning badge ("Referenced bank template is inactive") on the edit form. No automatic deactivation of the payment location. |
| EC-5 | User attempts to select an inactive bank template when creating or editing a payment location | The inactive template does not appear in the dropdown; submission with its ID is rejected server-side with HTTP 400. |
| EC-6 | Company has no active `tenant_bank_template` records and user tries to create a BANK_ACCOUNT location | The bank template dropdown is empty; UI shows informational message: *"No bank templates available. Configure bank templates first."* Submission is blocked until at least one active bank template exists for the company. |
| EC-7 | User tries to change `payment_type` on an existing record | The field is read-only in the edit form. Any API request with a differing `payment_type` is rejected with HTTP 400. |
| EC-8 | User creates a CASH location with `currency` set to an unsupported ISO code | Rejected server-side with a field-level error: *"Invalid currency code."* |
| EC-9 | A payment location is deactivated while (in a future feature) it is assigned to employees | In v1, deactivation is always permitted. Future milestones must add a guard that prevents deactivation while active assignments exist. |
| EC-10 | Tenant has multiple companies; user queries payment locations for company A using company B's ID | Server enforces tenant + company boundary; returns 404 (company not found in tenant context). Cross-company leakage is not allowed. |

---

## 9. UX Considerations

- **Type selection:** A clear radio or segmented control for `CASH` vs `BANK ACCOUNT` at the top of the create form. Switching the type resets bank-specific fields and shows/hides them dynamically.
- **Bank Template dropdown:** Show bank name + SWIFT/BIC as the display label. On selection, render a read-only summary panel below the dropdown (bank name, SWIFT/BIC, account number format hint) so the user knows what format the account number must follow.
- **Account number field:** Render the format hint from the bank template as placeholder text or a helper label (e.g. *"10-digit numeric"*).
- **Masking:** In the list view, the account number is masked (e.g. `••••••7890`) to avoid inadvertent exposure. The full value is visible only on the edit form.
- **Type badge:** Each list row shows a clear visual badge: `CASH` or `BANK ACCOUNT`.
- **Empty state:** If no payment locations exist, show a guided empty state: *"No payment locations yet. Add your first cash desk or bank account."*
- **Inactive items:** Hidden from the main list behind an explicit "Show inactive" toggle to keep the operational view clean.
- **Confirmation on deactivate:** Modal confirmation required; accidental single-click deactivation prevented.
- **Error feedback:** All server-side validation errors surface as field-level messages. A generic fallback banner covers unexpected errors.

---

## 10. Open Questions

| # | Question | Impact |
|---|---|---|
| OQ-1 | Should `currency` on a payment location be restricted to the company's configured payroll country currencies, or can it be any valid ISO-4217 code? Some companies operate multi-currency payroll. | Affects BR-3 validation scope. |
| OQ-2 | Should `name` uniqueness be enforced case-insensitively at the database level (unique index with a collation function) or only in application logic? | Affects DDL design. |
| OQ-3 | Is `account_number` considered PII under the project's data classification policy? It is a company bank account (not personal), but some jurisdictions treat corporate financial identifiers as sensitive. | Affects PII tagging and any future encryption-at-rest requirements. Currently classified as **sensitive** pending confirmation. |
| OQ-4 | When the referenced `tenant_bank_template` is deactivated, should the system proactively warn on the payment location list (not just the edit form)? | Affects list view design. |
| OQ-5 | Should `account_number_format` validation be a strict reject (format present and value does not match → block save) or a warning-only advisory? | Affects BR-7 and UX for edge cases where the format hint is informational rather than a true regex. |
| OQ-6 | In a future milestone, when employees are assigned to payment locations, should an employee be limited to one location or allowed multiple (e.g. partial salary split)? | No immediate impact, but the data model should be reviewed before employee assignment is designed. |

---

## 11. Acceptance Criteria

| ID | Criterion |
|---|---|
| AC-1 | A user with `PAYMENT_LOCATION_MANAGE` privilege can create a CASH payment location with name and currency; it appears active in the list immediately. |
| AC-2 | A user with `PAYMENT_LOCATION_MANAGE` privilege can create a BANK_ACCOUNT payment location with name, currency, a valid active bank template, and an account number; it appears active in the list. |
| AC-3 | Creating a BANK_ACCOUNT location without a bank template or without an account number is rejected with a descriptive field-level error. |
| AC-4 | Creating a CASH location with a bank template or account number populated is rejected server-side with HTTP 400. |
| AC-5 | `payment_type` cannot be changed after creation; any API attempt to alter it on an existing record returns HTTP 400. |
| AC-6 | `name` must be unique per company (case-insensitive); a duplicate submission returns a field-level error. |
| AC-7 | An invalid ISO-4217 currency code is rejected with a field-level error. |
| AC-8 | Submitting an account number that does not match the bank template's `account_number_format` (when the format is a valid regex) is rejected with a field-level error showing the expected format. |
| AC-9 | Selecting an inactive bank template is prevented in the UI (not in dropdown) and rejected server-side with HTTP 400 if submitted via API. |
| AC-10 | A user with `PAYMENT_LOCATION_VIEW` privilege can list and view payment locations but all write operations return HTTP 403. |
| AC-11 | A user without any relevant privilege cannot access the payment location endpoints (HTTP 403). |
| AC-12 | Deactivating a payment location removes it from the default active list; it reappears when the "Show inactive" toggle is enabled. |
| AC-13 | Reactivating an inactive payment location restores it to the active list. |
| AC-14 | Payment locations from Company A are not accessible via Company B's endpoint, even within the same tenant. |
| AC-15 | Payment locations from Tenant A are not accessible by users of Tenant B under any circumstances. |
| AC-16 | All create, update, activate, and deactivate actions produce a corresponding audit log entry with actor, timestamp, and company context. |
| AC-17 | The paginated list endpoint returns items within the standard `ApiResponse` envelope with `items`, `page`, `size`, `totalElements`, `totalPages`. |
| AC-18 | In the list view, the account number is masked for BANK_ACCOUNT locations; the full value is only visible in the edit form. |

---

## 12. API Reference

### Base path
```
/api/v1/tenant/payment-locations
```

### Endpoints

| Method | Path | Privilege | Description |
|--------|------|-----------|-------------|
| `GET` | `/` | `PAYMENT_LOCATION_VIEW` | List locations for a company. Params: `companyId` (required), `page`, `size`, `active` (boolean filter) |
| `GET` | `/{id}` | `PAYMENT_LOCATION_VIEW` | Get a single location by ID (returns `accountNumberFull`) |
| `POST` | `/` | `PAYMENT_LOCATION_MANAGE` | Create a payment location → 201 |
| `PUT` | `/{id}` | `PAYMENT_LOCATION_MANAGE` | Update name, currency, bankTemplateId, accountNumber |
| `PATCH` | `/{id}/activate` | `PAYMENT_LOCATION_MANAGE` | Activate |
| `PATCH` | `/{id}/deactivate` | `PAYMENT_LOCATION_MANAGE` | Deactivate |

### Request body — Create (`POST /`)
```json
{
  "companyId": "uuid",
  "name": "Main Cash Desk",
  "paymentType": "CASH",
  "currency": "SRD",
  "bankTemplateId": null,
  "accountNumber": null
}
```

### Request body — Update (`PUT /{id}`)
```json
{
  "name": "Main Cash Desk",
  "currency": "SRD",
  "bankTemplateId": null,
  "accountNumber": null
}
```
> `paymentType` is immutable and must not be included in update requests.

### Response shape (list)
```json
{
  "data": {
    "items": [ { ... } ],
    "page": 0,
    "size": 20,
    "totalElements": 5,
    "totalPages": 1
  },
  "meta": { "requestId": "..." }
}
```

### Row DTO fields
| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID | |
| `companyId` | UUID | |
| `name` | string | |
| `paymentType` | `CASH` \| `BANK_ACCOUNT` | Immutable after create |
| `currency` | string | ISO-4217, 3 chars uppercase |
| `bankTemplateId` | UUID \| null | BANK_ACCOUNT only |
| `bankTemplateName` | string \| null | Resolved from bank template |
| `bankName` | string \| null | Resolved from bank template |
| `swiftBic` | string \| null | Resolved from bank template |
| `accountNumberFormat` | string \| null | Regex from bank template |
| `accountNumberMasked` | string \| null | ••••last4 (list endpoint) |
| `accountNumberFull` | string \| null | Full value (get-by-id endpoint only) |
| `active` | boolean | |
| `createdAt` | ISO-8601 datetime | |
| `updatedAt` | ISO-8601 datetime | |

### Privilege IDs
| Privilege | UUID |
|-----------|------|
| `PAYMENT_LOCATION_VIEW` | `20000000-0000-0000-0000-000000000033` |
| `PAYMENT_LOCATION_MANAGE` | `20000000-0000-0000-0000-000000000034` |

### Navigation
- Nav item ID: `50000000-0000-0000-0000-000000000018`
- Path: `/app/payment-locations`
- Label key: `nav.payment_locations`
- Required privilege: `PAYMENT_LOCATION_VIEW`
- Sort order: 46

### Audit action codes
| Event | Code |
|-------|------|
| Create | `PAYMENT_LOCATION_CREATED` |
| Update | `PAYMENT_LOCATION_UPDATED` |
| Activate | `PAYMENT_LOCATION_ACTIVATED` |
| Deactivate | `PAYMENT_LOCATION_DEACTIVATED` |

Resource type: `TENANT_PAYMENT_LOCATION`

