# Suriname — bonus en vakantietoelage (Wet Loonbelasting)

**Bron:** [Wet Loonbelasting](https://belastingdienst.sr/wp-content/uploads/2024/10/Wet-Loonbelasting.pdf) (o.a. art. 10, 14, 17, 17a).  
**Doel in Wage Payroll:** de **toepassingswijze** modelleren (niet per se exacte SRD-bedragen uit de wetstekst van 2024).

---

## 1. Twee lagen: vrijstelling vs. inhouding

| Laag | Wat | Waar in de wet |
|------|-----|----------------|
| **A. Niet tot loon / vrijgesteld deel** | Deel van vakantie-uitkering en bonus dat **geen** (volledige) normale loonbelasting-grondslag vormt | Art. 10 lid 1 onder i en j (historische caps); tariefgroepen 9/10 in de legacy-tabel (sinds 2025-07 o.a. jaarlijks drempelbedrag in `SR_TAX_FREE_VACATION_YEAR` / `SR_TAX_FREE_BONUS_YEAR`) |
| **B. Loonbelasting over het belaste deel** | Inhouding over het deel dat wél belast wordt, met de **bijzondere-beloningenmethode** (“label”) | **Art. 17** (niet art. 17a — dat is een **ander** regime op verzoek) |

De payslip toont daarom typisch:

1. **Bruto** vakantie (1006) en bonus (1007) — volledige uitbetaling.
2. **Loonbelasting vakantie** (1021) en **loonbelasting bonus** (1022) — berekend volgens art. 17 over het **belaste** deel.
3. **Normale loonbelasting** (1019) — over regulier periodiek loon (label-loon), na algemene belastingvrij (1005 / art. 14).

---

## 2. Art. 10 — vrijstelling (conceptueel)

**Vakantie (i):** vakantie-uitkeringen tot per jaar het loon over **één maand**, max. (in de wetstekst) SRD 10.016; bij kortere periode **naar evenredigheid**.

**Bonus (j):** gratificaties/bonussen (zonder andere bijzondere regeling) tot eveneens **één maandloon**, zelfde maximum; evenredigheid bij kortere periode.

**In de engine (beoogd):**

- `belastbaar_deel = max(0, uitbetaald_bedrag − vrijgesteld_deel)`
- `vrijgesteld_deel = min(uitbetaald, maandloon_referentie, jaar_drempel_regel_9_of_10)`  
  Maandloonreferentie = periodiek loon van de uitbetalingsperiode (zelfde als `compensation.periodic_rate` bij maandlonen).

Tariefregels 9/10 in `platform_country_tax_rule` zijn **jaardrempels** (bijv. 19.500 sinds 2025-07); die kunnen naast de wetsteekst-cap worden gebruikt als **configureerbare** vrijstelling (FiscLe/legacy), niet als vervanging van art. 10 zonder beleidskeuze.

---

## 3. Art. 17 — labelmethode (bijzondere beloning)

**Lid 1:** Een beloning over een tijdvak dat **meerdere normale loontijdvakken** bestrijkt, wordt **afzonderlijk** belast.

**Lid 2 — rekenstappen** (vereenvoudigd):

| Stap | Beschrijving |
|------|----------------|
| **a** | Deel de bijzondere beloning door **N** = aantal normale loontijdvakken waar de uitkering betrekking op heeft. Tel dat bedrag per “slice” op bij het **label-loon** van het loontijdvak waarin wordt uitbetaald. |
| **b** | Bepaal loonbelasting over dat **samengestelde** loon volgens de **tijdvaktabel** (progressieve tabel op maandbasis / geannualiseerd volgens engine-beleid). |
| **c** | Trek daarvan de loonbelasting af die alleen over het **normale tijdvakloon** (label) verschuldigd zou zijn. |
| **d** | Verschil × **N** = loonbelasting over de bijzondere beloning. |
| **e** | **N** = aantal loontijdvakken waarop de uitkering betrekking heeft (vaak 12 bij jaarlijkse vakantie/bonus). |

**Implementatie:** `SurinameWageTaxCalculator.computeArt17BijzondereBeloningTax(...)`.

**Belangrijk:** Dit is **niet** hetzelfde als het volledige vakantie-/bonusbedrag in de maandelijkse `LOONBELASTING`-basis stoppen en één keer `SR_WAGE_TAX_DEFAULT` draaien — dat zou te veel belasting geven (hoge schijf over het hele bedrag in één periode).

---

## 4. Art. 17a — niet verwarren met vakantie/bonus in de praktijk

Art. **17a** geldt op **verzoek** voor **uitkeringen ineens** zonder betrekking op een bepaalde periode, met een **eigen** (lagere) progressieve tabel (5% / 15% / 25% / 35%).

- **Niet** de standaardmethode voor periodieke vakantietoelage of jaarbonus onder de label.
- In de catalogus: aparte templates (bijv. lump sum 1009) en regel `SR_PAYMENTS_AT_ONCE_YEAR` (tarief type 2).

---

## 5. Mapping naar wage components

| Template | Rol | Engine-fase |
|----------|-----|-------------|
| **1006** Vacation allowance | Bruto uitkering | Gross — volledig bedrag, NET+ |
| **1007** Bonus | Bruto uitkering | Gross — volledig bedrag, NET+ |
| **1011** Extra earnings | Bruto uitkering (art. 17, **geen** art. 10 vrijstelling) | Gross — volledig bedrag, NET+ |
| **1021** Wage tax vacation allowance | Inhouding art. 17 over belast vakantiedeel | Statutory / derived — `SUR_WAGE_TAX_VACATION_ALLOWANCE` |
| **1022** Wage tax bonus | Inhouding art. 17 over belast bonusdeel | Statutory / derived — `SUR_WAGE_TAX_BONUS` |
| **1025** Wage tax extra earnings | Inhouding art. 17 over volledig extra-earnings bedrag | Statutory / derived — `SUR_WAGE_TAX_EXTRA_EARNINGS` |

**UI:** Employee payroll inputs shows **Factor = N** (default **12** loontijdvakken) on gross earners **1006**, **1007**, and **1011** after preview; wage-tax lines **1021**, **1022**, **1025** show **no** factor (amount only). Returned as `employeeArt17AttributionPeriods` on formula-preview.
| **1014** AOV vacation allowance | AOV over bruto vakantie-uitkering (1006) | Derived — `SUR_AOV_VACATION_ALLOWANCE` (`SR_AOV_PREMIUM_MONTH`) |
| **1015** AOV bonus | AOV over bruto bonus (1007) | Derived — `SUR_AOV_BONUS` |
| **1012** AOV premium | AOV over regulier loon (AOV-base minus 1006/1007) | Platform `SOCIAL_PREMIUM_EE` / tenant 1012 |
| **1019** Wage tax | Normale loonbelasting over label-loon | Statutory — `SR_WAGE_TAX_DEFAULT` op aangepaste `LOONBELASTING` **zonder** dubbeltelling van 1021/1022 |

**Base effects (beoogd):**

- 1006/1007: `GROSS` + `NET` volledig; **geen** volledige `LOONBELASTING` voor het vrijgestelde deel.
- Alleen het **belaste** deel gaat de art.-17-berekening in (inputs voor 1021/1022), niet opnieuw door de normale 1019-pijplijn.

**Regels in DB:** `SR_TAX_FREE_VACATION_YEAR`, `SR_TAX_FREE_BONUS_YEAR` (drempels); primaire ladder `SR_WAGE_TAX_DEFAULT` voor art. 17 stappen b–c.

---

## 6. Engine-status

| Onderdeel | Status |
|-----------|--------|
| Bruto 1006/1007 in payroll | Ja |
| Vrijstelling (art. 10 + regels 9/10) | `SurinameSpecialRemunerationSupport` — `min(uitbetaling, maandloon, jaar-drempel)` |
| Art. 17 → **1021** / **1022** | `SurinameTenantDerivedComponentService` — tenant derived lines |
| **1014** / **1015** AOV op vakantie/bonus | `SurinameTenantDerivedComponentService` — `SR_AOV_PREMIUM_MONTH` op bruto 1006/1007 |
| Normale **1012** AOV | `SurinameStatutoryContributor` — AOV-base minus vakantie en bonus (zelfde splitsing als label-loon) |
| Normale **1019** (platform `WAGE_TAX`) | `SurinameStatutoryContributor` — alleen **label-loon** (`LOONBELASTING` − 1006 − 1007) |
| **1004** taxable income | Toont label-loon, niet brutale som met vakantie/bonus |
| **1005** belastingvrij | Berekend over label-loon |

Code: `SurinameSpecialRemunerationSupport`, `SurinameWageTaxCalculator.computeArt17BijzondereBeloningTax`.

---

## 7. Acceptatie / tests (voor implementatie)

1. Werknemer met vast maandloon L, jaarlijkse vakantie V, N=12: handmatig art. 17-stappen vs. `computeArt17BijzondereBeloningTax`.
2. V &lt; vrijstelling → 1021 = 0; V &gt; vrijstelling → alleen over `(V − vrijstelling)`.
3. Normale 1019 ongewijzigigd bij uitbetaling vakantie in dezelfde periode als label-loon L (geen dubbele belasting over V via `LOONBELASTING`).

Zie ook: [`payroll-engine-country.md`](./payroll-engine-country.md) §5.4, [`suriname-wage-tax-rules.md`](./suriname-wage-tax-rules.md), [`README-suriname-tax-data.md`](../datafiles/README-suriname-tax-data.md).
