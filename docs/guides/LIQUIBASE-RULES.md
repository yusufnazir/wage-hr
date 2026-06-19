# Liquibase Versioned Migration Spec (AI-Safe)

---

## 1. Purpose


This document defines a **strict versioned migration system built on top of Liquibase**.

**Deployment assumption (this repository):** environments use a **new schema** (empty database + full changelog). Changelog and docs are optimized for that path, not for carrying forward legacy rows or in-place upgrade narratives.

It introduces a logical versioning model where:

* Each database change is identified by a stable entity name and type (schema/data)
* Each evolution of that change is represented by an incremented version
* Each version is an immutable Liquibase changeSet

This system ensures:

* Full auditability
* Deterministic AI-generated migrations
* Controlled schema/data evolution
* Strong multi-tenancy and security enforcement

---

## 2. Core Concept: Versioned Changeset Model

### 2.1 Identity Model

```
changeSet ID = schema-[entity]-[version] (for DDL)
changeSet ID = data-[entity]-[version] (for DML)
```

Example:

```
schema-user-1
schema-user-2
data-user-1
data-user-2
```

---

### 2.2 Semantic Meaning

| Element   | Meaning                                    |
| --------- | ------------------------------------------ |
| schema/data | DDL or DML change                        |
| entity    | Table or domain entity                     |
| version   | Evolution/version of that entity's change  |
| changeSet | Immutable execution snapshot               |

---

### 2.3 Rules of Evolution

A new version MUST be created when:

* Schema changes
* Data changes
* Privilege changes
* Bug fixes in migration logic
* Tenant-related adjustments

---

### 2.4 Immutability Rule (CRITICAL)

* Once executed, a changeSet MUST NEVER be modified
* Any change requires a new versioned changeSet
* History is append-only

---

## 3. Changeset Structure

### 3.1 Folder Organization

```
/db/changelog/
    ddl/   -> schema changes (tables, columns, constraints)
    dml/   -> data changes (seed, updates, privileges)
```

---

### 3.2 Master Changelog

* A single root changelog includes all module changelogs
* Modules are organized per domain feature

---

## 4. DML Execution Model (Custom Engine)

### 4.1 Required Approach

All DML MUST use the **parameterized `CustomDataTaskChange` pattern**:

* One **reusable task class** per entity type (e.g. `DataUpsertPrivilege`, `DataGrantRolePrivilege`)
* Data values are injected as **`<param>` tags in XML** — the XML changeset is the data record
* Task classes contain **zero hardcoded data** — all values arrive via Liquibase setter injection

---

### 4.2 Rules

* No raw SQL in DML changeSets
* No inline insert/update/delete operations in XML/YAML
* All DML logic must be implemented in Java, extending `CustomDataTaskChange`
* **Each changeset inserts/updates exactly one logical row** (one privilege, one nav item, one user, etc.)
* Task classes MUST be idempotent (upsert or insert-if-missing)

---

### 4.3 Base Class Contract

```java
public abstract class CustomDataTaskChange implements CustomTaskChange {

    protected JdbcConnection connection;
    protected Timestamp ts;

    /** Subclasses implement DML here — connection and ts are already set. */
    public abstract void handleUpdate() throws Exception;

    /** Null-safe, type-aware param binding. */
    protected void setData(PreparedStatement ps, int index, Object value) throws SQLException {
        // Handles String, Boolean, Integer, Long, Double, BigDecimal, Timestamp, null
    }

    @Override
    public final void execute(Database database) throws CustomChangeException {
        // wires connection, sets ts, calls handleUpdate(), commits
    }
}
```

---

### 4.4 Implementation Rules

* One class per **entity type or relation** (not per migration)
* The class must declare **private fields + public getters+setters** for every `<param>` Liquibase needs to inject
* Must be stateless across different Liquibase runs
* Must be idempotent: upsert by `id`, or insert-if-missing by composite key

---

### 4.5 Example Task Class

```java
public class DataUpsertPrivilege extends CustomDataTaskChange {

    private String id;
    private String code;
    private String description;

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }
    public void setCode(String code) { this.code = code; }
    public String getCode() { return code; }
    public void setDescription(String description) { this.description = description; }
    public String getDescription() { return description; }

    @Override
    public void handleUpdate() throws Exception {
        // upsert privilege row using this.id, this.code, this.description
    }

    @Override
    public String getConfirmationMessage() {
        return "Upserted privilege " + code;
    }
}
```

---

### 4.6 Example Changeset (parameterized)

Each changeset represents **one row of data**. Use `onValidationFail="MARK_RAN"` on parameterized seed changesets so **local dev** can tolerate checksum drift if you edit a changeset after it already ran on your machine (Liquibase treats the body as part of the checksum).

```xml
<changeSet id="data-scaffold-priv-user-view-1" author="wagepayroll" onValidationFail="MARK_RAN">
    <customChange class="com.wagepayroll.liquibase.task.DataUpsertPrivilege">
        <param name="id" value="20000000-0000-0000-0000-000000000001"/>
        <param name="code" value="USER_VIEW"/>
        <param name="description" value="View users"/>
    </customChange>
</changeSet>
```

---

### 4.7 Reusable Task Classes (reference)

| Class | Entity | Key params |
|---|---|---|
| `DataUpsertPrivilege` | `privilege` | `id`, `code`, `description` |
| `DataGrantRolePrivilege` | `role_privilege` | `tenantId`, `roleId`, `privilegeId` |
| `DataUpsertTenant` | `tenant` | `id`, `handle`, `name` |
| `DataUpsertUser` | `user_account` | `id`, `email`, `passwordHash` |
| `DataUpsertRole` | `role` | `id`, `tenantId`, `name` |
| `DataGrantMembership` | `membership` | `tenantId`, `userId` |
| `DataGrantUserRole` | `user_role` | `tenantId`, `userId`, `roleId` |
| `DataUpsertNavMenuItem` | `nav_menu_item` | `id`, `path`, `labelKey`, `sortOrder`, `requiredPrivilegeCode`, `requiredPlanFeatureCode` |
| `DataUpsertPlatformSetting` | `platform_setting` | `id`, `key`, `valueText` |
| `DataUpsertTenantSetting` | `tenant_setting` | `id`, `tenantId`, `key`, `valueText` |
| `DataUpsertRoleTemplate` | `role_template` | `id`, `code`, `displayName` |
| `DataGrantRoleTemplatePrivilege` | `role_template_privilege` | `roleTemplateId`, `privilegeCode` |
| `DataSetUserPlatformSuperadmin` | `user_account` | `userId` |

---

## 5. DDL (Schema) Rules

### 5.1 Required Approach

All DDL MUST use **native Liquibase declarative changesets** (e.g., `<createTable>`, `<createIndex>`, `<addForeignKeyConstraint>`).

**Do NOT** wrap DDL in Java custom classes. DDL is declarative and benefits from native Liquibase support.

---

### 5.2 Folder Organization

```
/db/changelog/ddl/
    create-table-*.xml    (one file per logical entity/table group)
    schema-*.xml          (master includes)
```

---

### 5.3 Example: Single Table

```xml
<changeSet id="schema-m5-tenant-company-1" author="wagepayroll">
    <createTable tableName="tenant_company">
        <column name="id" type="VARCHAR(36)">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="tenant_id" type="VARCHAR(36)">
            <constraints nullable="false"/>
        </column>
        <column name="name" type="VARCHAR(120)">
            <constraints nullable="false"/>
        </column>
        <column name="active" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
        <column name="created_at" type="TIMESTAMP">
            <constraints nullable="false"/>
        </column>
        <column name="updated_at" type="TIMESTAMP">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <addForeignKeyConstraint constraintName="fk_tenant_company_tenant"
                             baseTableName="tenant_company" baseColumnNames="tenant_id"
                             referencedTableName="tenant" referencedColumnNames="id"/>
</changeSet>

<changeSet id="schema-m5-tenant-company-idx-1" author="wagepayroll">
    <createIndex indexName="idx_tenant_company_tenant_active" tableName="tenant_company">
        <column name="tenant_id"/>
        <column name="active"/>
    </createIndex>
</changeSet>
```

---

### 5.4 Rules

* Use `<createTable>` for table creation, **never raw SQL**
* Use `<createIndex>` for indexes; separate changesets per index (for clarity)
* Use `<addForeignKeyConstraint>` for FKs (can be in same changeset as table or separate)
* DDL changesets normally omit `onValidationFail="MARK_RAN"`; add it only for narrow dev/test cases if needed
* One file per entity group (e.g., `create-table-payroll-org-structure.xml` for company + department + job + employee)
* Versioning: `schema-[entity]-[version]`, e.g., `schema-m5-tenant-company-1`

---

### 5.5 Master Changelog

The master DDL changelog includes all individual files:

```xml
<databaseChangeLog>
    <include file="create-table-payroll-org-structure.xml" relativeToChangelogFile="true"/>
    <include file="create-table-work-time.xml" relativeToChangelogFile="true"/>
    <include file="create-table-bank-templates.xml" relativeToChangelogFile="true"/>
    <!-- ... etc ... -->
</databaseChangeLog>
```

---

## 6. Multi-Tenancy Rules

* Every table/entity MUST include an `id` column as the primary key
* Every table MUST include `tenant_id` unless explicitly global
* All DML operations MUST respect tenant isolation
* No cross-tenant data access is allowed

---

## 7. Security & Privilege Rules

### 7.1 Privilege System

* Privileges are data-driven (not hardcoded)
* Defined via Liquibase DML changesets

---

### 7.2 SuperAdmin Rule

* SuperAdmin has ALL privileges
* BUT still goes through privilege validation (no bypass)

---

### 7.3 Privilege Catalog Model

* Global privilege catalog (defined by system)
* Tenant roles assign privileges from the global catalog
* Tenant admin can only assign existing catalog codes

---

### 7.4 Required Rule per ChangeSet

Every privilege-related change MUST:

* Insert privilege
* Assign to SuperAdmin
* Be included in SAME versioned changeSet

---

## 8. Structure & Naming Rules

schema-[entity]-[version]
data-[entity]-[version]
### 8.1 Naming Convention

All changeSet IDs must use:

DDL:
    schema-[entity]-[version]
DML:
    data-[entity]-[version]

---

schema-user-1
data-user-1
data-user-2
### 8.2 Example

```
schema-user-1
schema-user-2
data-user-1
data-user-2
```

---

## 9. Validation Rules

Before applying any migration:

* Liquibase validate must pass
* No duplicate schema/data/entity/version combinations
* tenant_id must be enforced
* No privilege bypass paths exist
* No raw SQL in DML
* All custom classes must compile

---

## 10. Rollback Strategy

* Rollbacks must be explicitly defined for destructive changes
* If rollback is not possible:

  * must be documented in changeSet
  * must include mitigation strategy

---

## 11. DML Upsert Convention (Seed & Reference Data)

### 11.1 Rule

All DML custom task classes that insert seed or reference data **MUST** use upsert logic:

* If a row with the given `id` already exists → **UPDATE** it
* If no row with that `id` exists → **INSERT** it

This keeps seed tasks **idempotent** (handy for tests and local re-runs) while the canonical path remains a **fresh** `DATABASECHANGELOG`.

### 11.2 Version Increment = New Changeset

The changeset ID always ends with `-<versionnumber>` (e.g. `data-m11-platform-bank-templates-seed-1`).

* Liquibase tracks executed changesets by ID — once run, a changeset is **never re-executed**
* To change seeded data in a way that must run again, **increment the version** to create a new changeset ID
* Prefer **folding fixes into the current DDL or seed** (single linear changelog) rather than stacking parallel “patch” files

Example:

```
data-m11-platform-bank-templates-seed-1   ← idempotent custom seed (upsert in Java)
```

### 11.3 Upsert Pattern

```java
private static void upsert(Connection c, String id, ..., Timestamp ts) throws Exception {
    try (PreparedStatement check = c.prepareStatement(
            "SELECT COUNT(*) FROM my_table WHERE id = ?")) {
        check.setString(1, id);
        try (ResultSet rs = check.executeQuery()) {
            rs.next();
            if (rs.getInt(1) > 0) {
                // UPDATE — row already exists
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE my_table SET col1 = ?, col2 = ?, updated_at = ? WHERE id = ?")) {
                    ps.setString(1, val1);
                    ps.setString(2, val2);
                    ps.setTimestamp(3, ts);
                    ps.setString(4, id);
                    ps.executeUpdate();
                }
                return;
            }
        }
    }
    // INSERT — row does not exist
    try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO my_table (id, col1, col2, created_at, updated_at) VALUES (?,?,?,?,?)")) {
        ps.setString(1, id);
        ps.setString(2, val1);
        ps.setString(3, val2);
        ps.setTimestamp(4, ts);
        ps.setTimestamp(5, ts);
        ps.executeUpdate();
    }
}
```

### 11.4 `onValidationFail="MARK_RAN"` on seed changesets

All DML changesets that use parameterized `CustomDataTaskChange` tasks MUST set `onValidationFail="MARK_RAN"`
on the `<changeSet>` element:

```xml
<changeSet id="data-scaffold-priv-user-view-1" author="wagepayroll" onValidationFail="MARK_RAN">
```

**Why:** When you edit a parameterized seed’s XML after it ran locally, Liquibase sees a checksum mismatch. `MARK_RAN` marks the changeset as satisfied without re-running it, so your dev database does not block startup. A **new** database always executes the current XML once.

**Exception:** Pure-SQL native changesets (e.g. `<update>` backfills) do not need this attribute
because Liquibase manages their checksum natively.

### 11.5 What NOT to do

* Do **not** use `if (COUNT(*) >= N) return` guards that skip the entire changeset — this causes missed updates when a newer version of the data is needed
* Do **not** modify an already-executed changeset to fix data — always create a new versioned changeset
* Do **not** embed data directly in Java task classes — all seed values MUST come from `<param>` tags in the XML

---

## 12. Environment Rules

* No secrets in changeSets
* All environment-specific values must use parameters
* Never hardcode credentials or tenant IDs

---

## 13. AI Generation Rules (CRITICAL)

When generating migrations, AI MUST:

* Always generate a NEW version for any change
* Never modify executed changeSets
* Always preserve UUID for logical grouping
* Always increment version sequentially
* Always use CustomDataTaskChange for DML
* Always enforce tenant rules

---

## 14. Mandatory Checklist (Per PR)

* [ ] Versioned changeSets used correctly
* [ ] No modification of executed changeSets
* [ ] tenant_id enforced everywhere
* [ ] Privileges assigned to SuperAdmin
* [ ] No raw SQL in DML
* [ ] Custom Java classes used
* [ ] Liquibase validate passes
* [ ] Documentation updated
