"""Emit Liquibase fragment for platform_ledger_template legacy SR seed."""
import xml.sax.saxutils as xu

rows = [
    (1, "1000", "Overtime"),
    (2, "1100", "Vacation allowance"),
    (3, "1200", "Bonus"),
    (4, "1300", "Child allowance"),
    (5, "1400", "Lump sum"),
    (6, "1500", "Jubilee"),
    (7, "1600", "Extra earnings"),
    (8, "1700", "AOV premium"),
    (9, "1800", "AOV overtime"),
    (10, "1900", "AOV vacation allowance"),
    (11, "2000", "AOV bonus"),
    (12, "2100", "AOV child allowance"),
    (13, "2200", "AOV lump sum"),
    (14, "2300", "AOV extra earnings"),
    (15, "2400", "Wage tax"),
    (16, "2500", "Wage tax overtime"),
    (17, "2600", "Wage tax vacation allowance"),
    (18, "2700", "Wage tax bonus"),
    (19, "2800", "Wage tax child allowance"),
    (20, "2900", "Wage tax lump sum"),
    (21, "3000", "Wage tax extra earnings"),
    (22, "3100", "Netwage"),
    (23, "3200", "Rounding"),
    (24, "3300", "Gross wages"),
    (26, "3500", "Loan"),
    (27, "3600", "Savings"),
    (28, "3700", "Wages in kind"),
    (29, "3800", "Non taxable allowance"),
    (30, "3900", "Tax deductible"),
    (31, "4000", "Non taxable deduction"),
    (32, "4100", "Tax credit"),
    (33, "4200", "Tax deductions"),
    (34, "4300", "FVO employers premium"),
    (35, "4400", "FVO employees premium"),
    (36, "4500", "Employer retirement Contribution"),
    (37, "4600", "Employee retirement Contribution (Taxable)"),
    (38, "4700", "Employee retirement Contribution (Non-Taxable)"),
    (40, "4900", "APF Employer Contribution"),
    (41, "5000", "APF Employee Contribution"),
]


def uid(oid: int) -> str:
    return f"53100000-0000-0000-0000-{oid:012d}"


def insert(oid: int, code: str, desc: str) -> str:
    d = xu.escape(desc)
    return f"""        <insert tableName="platform_ledger_template">
            <column name="id" value="{uid(oid)}"/>
            <column name="country_code" value="SR"/>
            <column name="code" value="{code}"/>
            <column name="description" value="{d}"/>
            <column name="active" valueBoolean="true"/>
            <column name="created_at" valueComputed="NOW()"/>
            <column name="updated_at" valueComputed="NOW()"/>
        </insert>"""


def main() -> None:
    parts = ['    <changeSet id="data-m15-seed-platform-ledger-legacy-sr-1" author="wagepayroll">']
    parts.append(
        "        <comment>Suriname platform ledger templates migrated from legacy general_ledger_templates (country 740 to SR); see docs/datafiles/Payroll data - general_ledger_templates.csv</comment>"
    )
    for oid, code, name in rows:
        parts.append(insert(oid, code, name))
    parts.append("    </changeSet>")
    print("\n".join(parts))


if __name__ == "__main__":
    main()
