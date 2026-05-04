# Liquibase Versioned Migration Spec (AI-Safe)

---

## 1. Purpose


This document defines a **strict versioned migration system built on top of Liquibase**.

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

All DML MUST use:

```
CustomDataTaskChange
```

---

### 4.2 Rules

* No raw SQL in DML changeSets
* No inline insert/update/delete operations in XML/YAML
* All DML logic must be implemented in Java

---

### 4.3 Base Class Contract

```java
public abstract class CustomDataTaskChange implements CustomTaskChange {

    public abstract void handleUpdate() throws Exception;

    protected void setData(PreparedStatement ps, int index, Object value) {
        // Handles null safety and type conversion
    }
}
```

---

### 4.4 Implementation Rules

* One class per entity or cohesive domain concern
* Must be stateless
* Must be idempotent where possible
* Must support safe re-execution behavior

---

### 4.5 Example Implementation

```java
public class DataPermission extends CustomDataTaskChange {

    private String id;
    private String code;
    private String description;
    private String category;

    @Override
    public void handleUpdate() {
        // insert or update logic
    }
}
```

---

### 4.6 Example ChangeSet

```xml
<changeSet id="data-permission-1" author="system">
    <customChange class="com.example.liquibase.DataPermission">
        <param name="id"><![CDATA[1]]></param>
        <param name="code"><![CDATA[dashboard.view]]></param>
        <param name="description"><![CDATA[View dashboard]]></param>
        <param name="category"><![CDATA[dashboard]]></param>
    </customChange>
</changeSet>
```

---

## 5. DDL (Schema) Rules

### 5.1 Rules

* Schema changes MUST use versioned changeSets
* No modification of previous schema changeSets
* Every structural change must increment version

---

### 5.2 Schema Custom Change

Schema changes must also use custom Java classes:

```
SchemaCustomChange
```

---

### 5.3 Example

```xml
<changeSet id="schema-user-1" author="system">
    <customChange class="com.example.liquibase.SchemaAddColumn">
        <param name="tableName"><![CDATA[user]]></param>
        <param name="columnName"><![CDATA[tenant_id]]></param>
        <param name="type"><![CDATA[UUID]]></param>
    </customChange>
</changeSet>
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

This ensures migrations are safe to run against both fresh and existing databases without duplicates or skipped updates.

### 11.2 Version Increment = New Changeset

The changeset ID always ends with `-<versionnumber>` (e.g. `data-m11-platform-bank-templates-seed-1`).

* Liquibase tracks executed changesets by ID — once run, a changeset is **never re-executed**
* To change data that was already seeded, **increment the version** to create a new changeset ID
* The new changeset runs against all environments (including those that already have the old data)
* The new changeset's Java class must still apply upsert logic so it is safe on fresh databases too

Example:

```
data-m11-platform-bank-templates-seed-1   ← original seed (ran, immutable)
data-m11-platform-bank-templates-seed-2   ← correction/addition (new, will run)
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

### 11.4 What NOT to do

* Do **not** use `if (COUNT(*) >= N) return` guards that skip the entire changeset — this causes missed updates when a newer version of the data is needed
* Do **not** modify an already-executed changeset to fix data — always create a new versioned changeset
* Do **not** use `onFail="MARK_RAN"` preConditions on seed data — the upsert pattern makes them unnecessary

---

## 11. Environment Rules

* No secrets in changeSets
* All environment-specific values must use parameters
* Never hardcode credentials or tenant IDs

---

## 12. AI Generation Rules (CRITICAL)

When generating migrations, AI MUST:

* Always generate a NEW version for any change
* Never modify executed changeSets
* Always preserve UUID for logical grouping
* Always increment version sequentially
* Always use CustomDataTaskChange for DML
* Always enforce tenant rules

---

## 13. Mandatory Checklist (Per PR)

* [ ] Versioned changeSets used correctly
* [ ] No modification of executed changeSets
* [ ] tenant_id enforced everywhere
* [ ] Privileges assigned to SuperAdmin
* [ ] No raw SQL in DML
* [ ] Custom Java classes used
* [ ] Liquibase validate passes
* [ ] Documentation updated
