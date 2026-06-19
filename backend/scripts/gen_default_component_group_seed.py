#!/usr/bin/env python3
"""Generates data-m29-platform-default-component-group-sr-1.xml."""

from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/db/changelog/dml/data-m29-platform-default-component-group-sr-1.xml"

ITEMS = [
    ("001", "51000000-0000-0000-0000-000000000001", "Base salary", "Basissalaris"),
    ("01b", "51000000-0000-0000-0000-000000000006", "Vacation allowance", "Vakantietoeslag"),
    ("01c", "51000000-0000-0000-0000-000000000007", "Bonus", "Bonus"),
    ("01d", "51000000-0000-0000-0000-000000000008", "Child allowance", "Kinderbijslag"),
    ("01e", "51000000-0000-0000-0000-000000000009", "Lump sum", "Eenmalig"),
    ("01f", "51000000-0000-0000-0000-00000000000b", "Extra earnings", "Extra verdiensten"),
    ("002", "51000000-0000-0000-0000-000000000004", "Taxable income", "Belastbaar inkomen"),
    ("003", "51000000-0000-0000-0000-000000000005", "Tax exempt", "Belastingvrij"),
    ("004", "51000000-0000-0000-0000-00000000000c", "AOV premium", "AOV premie"),
    ("005", "51000000-0000-0000-0000-00000000000d", "AOV overtime", "AOV overwerk"),
    ("006", "51000000-0000-0000-0000-00000000000e", "AOV vacation allowance", "AOV vakantietoeslag"),
    ("007", "51000000-0000-0000-0000-00000000000f", "AOV bonus", "AOV bonus"),
    ("008", "51000000-0000-0000-0000-000000000010", "AOV child allowance", "AOV kinderbijslag"),
    ("009", "51000000-0000-0000-0000-000000000011", "AOV lump sum", "AOV eenmalig"),
    ("00a", "51000000-0000-0000-0000-000000000012", "AOV extra earnings", "AOV extra verdiensten"),
    ("00b", "51000000-0000-0000-0000-000000000013", "Wage tax", "Loonbelasting"),
    ("00c", "51000000-0000-0000-0000-000000000014", "Wage tax overtime", "Loonbelasting overwerk"),
    ("00d", "51000000-0000-0000-0000-000000000015", "Wage tax vacation allowance", "Loonbelasting vakantietoeslag"),
    ("00e", "51000000-0000-0000-0000-000000000016", "Wage tax bonus", "Loonbelasting bonus"),
    ("00f", "51000000-0000-0000-0000-000000000017", "Wage tax child allowance", "Loonbelasting kinderbijslag"),
    ("010", "51000000-0000-0000-0000-000000000018", "Wage tax lump sum", "Loonbelasting eenmalig"),
    ("011", "51000000-0000-0000-0000-000000000019", "Wage tax extra earnings", "Loonbelasting extra verdiensten"),
    ("012", "51000000-0000-0000-0000-00000000001a", "Net wage", "Netto loon"),
    ("013", "51000000-0000-0000-0000-00000000001b", "Rounding", "Afronding"),
    ("014", "51000000-0000-0000-0000-000000000022", "Tax deductions", "Belastingaftrek"),
    ("015", "51000000-0000-0000-0000-000000000024", "Deductible costs", "Aftrekbare kosten"),
    ("016", "51000000-0000-0000-0000-000000000025", "FVO employers premium", "FVO werkgeverspremie"),
    ("017", "51000000-0000-0000-0000-000000000026", "FVO employees premium", "FVO werknemerspremie"),
    ("018", "51000000-0000-0000-0000-00000000002a", "Free medical care", "Gratis medische zorg"),
    ("019", "51000000-0000-0000-0000-00000000002b", "APF employer contribution", "APF werkgeversbijdrage"),
    ("01a", "51000000-0000-0000-0000-00000000002c", "APF employee contribution", "APF werknemersbijdrage"),
]


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace('"', "&quot;")


def main() -> None:
    parts: list[str] = [
        """<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="data-m29-platform-default-component-group-sr-1" author="wagepayroll">
        <preConditions onFail="MARK_RAN">
            <sqlCheck expectedResult="0">SELECT COUNT(*) FROM platform_component_group_template WHERE id = '54000000-0000-0000-0000-000000000001'</sqlCheck>
        </preConditions>
        <comment>SR default component group: tenant wage components and grouping on company create.</comment>

        <insert tableName="platform_component_group_template">
            <column name="id" value="54000000-0000-0000-0000-000000000001"/>
            <column name="platform_country_id" valueComputed="(SELECT id FROM platform_country WHERE iso_alpha2 = 'SR')"/>
            <column name="sort_order" valueNumeric="0"/>
            <column name="active" valueBoolean="true"/>
            <column name="created_at" valueComputed="NOW()"/>
            <column name="updated_at" valueComputed="NOW()"/>
        </insert>
        <insert tableName="platform_component_group_template_locale">
            <column name="id" value="53900000-0000-0000-0000-000000000001"/>
            <column name="platform_component_group_template_id" value="54000000-0000-0000-0000-000000000001"/>
            <column name="locale" value="en"/>
            <column name="name" value="Default components"/>
            <column name="description" value="Standard payroll wage components provisioned for new companies."/>
        </insert>
        <insert tableName="platform_component_group_template_locale">
            <column name="id" value="53900000-0000-0000-0000-000000000002"/>
            <column name="platform_component_group_template_id" value="54000000-0000-0000-0000-000000000001"/>
            <column name="locale" value="nl"/>
            <column name="name" value="Standaard componenten"/>
            <column name="description" value="Standaard looncomponenten voor nieuwe bedrijven."/>
        </insert>

        <insert tableName="platform_component_header_template">
            <column name="id" value="54100000-0000-0000-0000-000000000001"/>
            <column name="platform_component_group_template_id" value="54000000-0000-0000-0000-000000000001"/>
            <column name="sort_order" valueNumeric="0"/>
            <column name="created_at" valueComputed="NOW()"/>
            <column name="updated_at" valueComputed="NOW()"/>
        </insert>
        <insert tableName="platform_component_header_template_locale">
            <column name="id" value="54500000-0000-0000-0000-000000000001"/>
            <column name="platform_component_header_template_id" value="54100000-0000-0000-0000-000000000001"/>
            <column name="locale" value="en"/>
            <column name="name" value="Components"/>
        </insert>
        <insert tableName="platform_component_header_template_locale">
            <column name="id" value="54500000-0000-0000-0000-000000000002"/>
            <column name="platform_component_header_template_id" value="54100000-0000-0000-0000-000000000001"/>
            <column name="locale" value="nl"/>
            <column name="name" value="Componenten"/>
        </insert>
"""
    ]
    for idx, (suffix, tpl_id, en, nl) in enumerate(ITEMS):
        item_id = f"54200000-0000-0000-0000-000000000{suffix}"
        loc_en = f"54400000-0000-0000-0000-0000000{idx * 2 + 1:03x}"
        loc_nl = f"54400000-0000-0000-0000-0000000{idx * 2 + 2:03x}"
        parts.append(
            f"""        <insert tableName="platform_component_item_template">
            <column name="id" value="{item_id}"/>
            <column name="platform_component_header_template_id" value="54100000-0000-0000-0000-000000000001"/>
            <column name="platform_wage_component_template_id" value="{tpl_id}"/>
            <column name="sort_order" valueNumeric="{idx}"/>
            <column name="created_at" valueComputed="NOW()"/>
            <column name="updated_at" valueComputed="NOW()"/>
        </insert>
        <insert tableName="platform_component_item_template_locale">
            <column name="id" value="{loc_en}"/>
            <column name="platform_component_item_template_id" value="{item_id}"/>
            <column name="locale" value="en"/>
            <column name="name" value="{esc(en)}"/>
        </insert>
        <insert tableName="platform_component_item_template_locale">
            <column name="id" value="{loc_nl}"/>
            <column name="platform_component_item_template_id" value="{item_id}"/>
            <column name="locale" value="nl"/>
            <column name="name" value="{esc(nl)}"/>
        </insert>
"""
        )
    parts.append("    </changeSet>\n\n</databaseChangeLog>\n")
    OUT.write_text("".join(parts), encoding="utf-8")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
