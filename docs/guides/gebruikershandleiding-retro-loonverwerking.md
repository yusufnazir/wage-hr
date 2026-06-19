# Gebruikershandleiding: Retro loonverwerking

**Versie:** concept (markup voor review)  
**Doelgroep:** Payroll-medewerkers en HR-beheerders  
**Taal:** Nederlands  
**Applicatie:** Wage Payroll — tenant webomgeving  

---

## Over dit document

Deze handleiding legt uit hoe u een **retro loonverwerking** uitvoert: het **corrigeren of alsnog verwerken van de loonadministratie voor een eerdere betaalperiode**. Denk aan situaties zoals een verkeerd basissalaris, een gemiste toelage, een te late bonus, of een aanpassing die **met terugwerkende kracht** moet gelden.

In de applicatie is er geen aparte knop “Retro”. U werkt met de bestaande onderdelen **Betaalperioden**, **Lonen per werknemer**, en **Loonrun uitvoeren**.

> **Let op:** Definitieve loonruns worden vastgelegd. Een retro-actie is een **bewuste correctie** op een bestaande periode. Controleer altijd de berekende bedragen (inclusief de berekeningslog) voordat u definitief doorzet.

---

## Inhoudsopgave

1. [Wanneer gebruikt u retro?](#1-wanneer-gebruikt-u-retro)
2. [Wat heeft u nodig?](#2-wat-heeft-u-nodig)
3. [Begrippen](#3-begrippen)
4. [Overzicht van de workflow](#4-overzicht-van-de-workflow)
5. [Stap-voor-stap: retro voor één werknemer](#5-stap-voor-stap-retro-voor-één-werknemer)
6. [Stap-voor-stap: retro voor het hele bedrijf](#6-stap-voor-stap-retro-voor-het-hele-bedrijf)
7. [Scenario’s](#7-scenarios)
8. [Controleren met de berekeningslog](#8-controleren-met-de-berekeningslog)
9. [Veelgestelde vragen](#9-veelgestelde-vragen)
10. [Fouten en oplossingen](#10-fouten-en-oplossingen)
11. [Screenshot-lijst](#11-screenshot-lijst)

---

## 1. Wanneer gebruikt u retro?

Gebruik retro loonverwerking wanneer:

- Een **eerdere betaalperiode** al is verwerkt, maar de bedragen onjuist waren.
- Een **doorlopende instructie** (vast maandbedrag, uren × tarief) met terugwerkende ingangsdatum moet gelden.
- U **één maand** wilt corrigeren zonder de doorlopende regel voor toekomstige perioden te wijzigen (handmatige overschrijving op de periodetransactie).
- U een **tussentijdse correctie** nodig heeft vóór een nieuwe definitieve loonrun (interim run).

**Niet bedoeld voor:**

- Gewone maandverwerking van de **huidige** actieve periode (gebruik dan de normale loonrun).
- Wijzigingen aan platform-belastingtarieven (dat doet de platformbeheerder; de engine kiest automatisch de juiste regels op basis van de **einddatum** van de betaalperiode).

---

## 2. Wat heeft u nodig?

### Rechten (privileges)

Minimaal nodig, afhankelijk van wat u doet:

| Activiteit | Benodigd recht |
|------------|----------------|
| Betaalperioden bekijken | `PAY_PERIOD_VIEW` |
| Betaalperiode-status wijzigen (bijv. Open) | `PAY_PERIOD_MANAGE` |
| Loonruns aanmaken / definitief maken | `PAY_PERIOD_RUN_MANAGE` |
| Doorlopende instructies beheren | `EMPLOYEE_PAYROLL_STANDING_MANAGE` |
| Bedragen berekenen (preview) | `PAY_PERIOD_VIEW` |
| Bedrijfskalender (huidig jaar / periode) | `COMPANY_MANAGE` |

Vraag uw beheerder om de juiste rol als een stap niet beschikbaar is.

### Voorwaarden in het systeem

- Het **bedrijf** heeft een ingevulde **bedrijfskalender** (huidig jaar en huidige periode).
- De **betaalperiode** die u wilt corrigeren **bestaat** (gegenereerd via *Betaalperioden regenereren* indien nodig).
- De betaalperiode heeft status **Open** (niet *Gesloten*) voordat u opnieuw kunt loonrunnen.
- De **werknemer** is gekoppeld aan het juiste bedrijf en heeft actieve looncomponenten.

---

## 3. Begrippen

| Begrip | Betekenis |
|--------|-----------|
| **Betaalperiode** | Een loontijdvak (bijv. maand 2 / 2026) met begin- en einddatum. |
| **Actieve betaalperiode** | De periode die hoort bij het **huidige jaar** en **huidige periode** op het bedrijfsrecord. Veel schermen openen standaard deze periode. |
| **Doorlopende instructie** | Vaste loonregel per werknemer (bijv. basissalaris) met ingangsdatum; geldt elke betaalperiode tot de einddatum. |
| **Periodetransactie** | Het concrete bedrag voor **één** betaalperiode en **één** looncomponent. |
| **Handmatige overschrijving** | Aanpassing die **alleen voor die periode** geldt; wordt niet overschreven bij opnieuw genereren. |
| **Periodetransacties genereren** | Zet doorlopende instructies om naar periodetransacties (materialisatie). |
| **Bedragen berekenen** | Payroll-engine preview: berekent alle componenten en kan de **berekeningslog** tonen. |
| **Loonrun** | Definitieve verwerking: resultaatregels en boekingen worden opgeslagen. |
| **Interim run** | Tussentijdse run voor correcties; **Final run** is de definitieve run. |

---

## 4. Overzicht van de workflow

```text
┌─────────────────────────────────────────────────────────────────┐
│ 1. Kies de te corrigeren betaalperiode                          │
│    (bedrijfskalender terugzetten + periode Open)                │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. Pas looninvoer aan                                           │
│    • doorlopende instructie (terugwerkende ingangsdatum)        │
│    • of handmatige overschrijving op periodetransactie          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. Periodetransacties genereren                                 │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Bedragen berekenen + berekeningslog controleren              │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. Loonrun uitvoeren (eventueel interim, daarna definitief)     │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. Resultaten controleren; bedrijfskalender terug naar actueel  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. Stap-voor-stap: retro voor één werknemer

Gebruik dit pad als **één werknemer** in een eerdere periode gecorrigeerd moet worden.

### Stap 5.1 — Ga naar de werknemer

1. Open **Werknemers** in het menu.
2. Zoek en open de betreffende werknemer.
3. Ga naar het tabblad **Looninvoer werknemer** (of **Lonen per werknemer** in het payroll-menu).

<!-- SCREENSHOT: retro-01-werknemer-looninvoer-tab.png -->
> **Screenshot 1 — Werknemer met tabblad Looninvoer**  
> Toon: werknemersdetail met het tabblad *Looninvoer werknemer* geselecteerd.  
> Bestandsnaam suggestie: `screenshots/retro-01-werknemer-looninvoer-tab.png`

---

### Stap 5.2 — Zet de bedrijfskalender op de te corrigeren periode

De looninvoer-schermen werken tegen de **actieve betaalperiode** van het bedrijf (huidig jaar + huidige periode).

1. Ga naar **Bedrijven** → bewerk het bedrijf van de werknemer.
2. Zet **Huidig jaar** en **Huidige periode** op de periode die u wilt corrigeren (bijv. jaar `2026`, periode `2`).
3. Sla op.

<!-- SCREENSHOT: retro-02-bedrijf-kalender.png -->
> **Screenshot 2 — Bedrijfskalender aanpassen**  
> Toon: bewerk-bedrijf-scherm met velden *Huidig jaar* en *Huidige periode* ingevuld voor de retro-periode.  
> Bestandsnaam: `screenshots/retro-02-bedrijf-kalender.png`

4. Controleer bij **Betaalperioden** dat de gekozen periode status **Open** heeft.  
   - Staat de periode op **Gesloten**? Wijzig de status naar **Open** (vereist `PAY_PERIOD_MANAGE`).

<!-- SCREENSHOT: retro-03-betaalperiode-open.png -->
> **Screenshot 3 — Betaalperiode op Open**  
> Toon: lijst betaalperioden met de doelperiode (bijv. 2026 / periode 2) en status *Open*.  
> Bestandsnaam: `screenshots/retro-03-betaalperiode-open.png`

---

### Stap 5.3 — Kies hoe u de correctie invoert

Er zijn twee gangbare manieren:

#### Optie A — Doorlopende instructie met terugwerkende ingangsdatum

Gebruik dit als de correctie **structureel** is (bijv. salaris was vanaf 1 januari verkeerd).

1. Open of maak een **doorlopende instructie** voor de juiste looncomponent (bijv. `[1001] Basissalaris`).
2. Stel **Ingangsdatum** in op de datum vanaf wanneer het nieuwe bedrag geldt (kan in het verleden liggen).
3. Vul het bedrag in, of schakel **Bedrag overschrijven** / **Factor overschrijven** in indien nodig.
4. Sla op.

<!-- SCREENSHOT: retro-04-doorlopende-instructie.png -->
> **Screenshot 4 — Doorlopende instructie met ingangsdatum**  
> Toon: detailpaneel met component, ingangsdatum in het verleden, en nieuw bedrag.  
> Bestandsnaam: `screenshots/retro-04-doorlopende-instructie.png`

#### Optie B — Alleen deze periode corrigeren (handmatige overschrijving)

Gebruik dit als alleen **één betaalperiode** afwijkt.

1. Ga naar het onderdeel **Looncomponenttransacties in de periode**.
2. Klik **Periodetransacties genereren** als er nog geen regels zijn.
3. Selecteer de transactie van de betreffende component.
4. Pas bedrag, hoeveelheid of tarief aan.
5. Zet **Handmatige overschrijving** aan en sla op.

<!-- SCREENSHOT: retro-05-handmatige-overschrijving.png -->
> **Screenshot 5 — Handmatige overschrijving op periodetransactie**  
> Toon: periodetransactie-detail met aangepast bedrag en badge/veld *Overschrijving* actief.  
> Bestandsnaam: `screenshots/retro-05-handmatige-overschrijving.png`

> **Tip:** Handmatige overschrijvingen worden **niet** overschreven als u opnieuw *Periodetransacties genereren* uitvoert. Doorlopende instructies wél (tenzij u overschrijving gebruikt).

---

### Stap 5.4 — Periodetransacties genereren

1. Klik **Periodetransacties genereren** (of *Generate period inputs*).
2. Bekijk het resultaat onder **Resultaat laatste materialisatie** (aangemaakt / bijgewerkt / overgeslagen wegens handmatige overschrijving).

<!-- SCREENSHOT: retro-06-materialisatie-resultaat.png -->
> **Screenshot 6 — Resultaat materialisatie**  
> Toon: samenvatting na genereren (aangemaakt, bijgewerkt, overgeslagen).  
> Bestandsnaam: `screenshots/retro-06-materialisatie-resultaat.png`

---

### Stap 5.5 — Bedragen berekenen en controleren

1. Klik **Bedragen berekenen**.
2. Controleer de samenvatting (periode, datumbereik, netto loon).
3. Open **Berekeningslog bekijken** om te zien hoe elk component is berekend.
4. Download desnoods de log via **Log downloaden**.

<!-- SCREENSHOT: retro-07-berekenen-en-log.png -->
> **Screenshot 7 — Bedragen berekenen en berekeningslog**  
> Toon: knoppen *Bedragen berekenen*, *Berekeningslog bekijken*, en een fragment van de log met componentregels.  
> Bestandsnaam: `screenshots/retro-07-berekenen-en-log.png`

---

### Stap 5.6 — Definitieve loonrun (indien nodig)

Als de preview klopt en de periode definitief verwerkt moet worden:

1. Ga naar **Loonrun uitvoeren** (*Run payroll*).
2. Controleer dat de **Actieve betaalperiode** de retro-periode is.
3. Doorloop de stappen:
   - **Invoer voorbereiden** → Periodetransacties genereren (indien nog niet gedaan)
   - **Berekeningen bekijken** → Preview voor geselecteerde werknemers
   - **Loonrun uitvoeren** → Definitief maken
   - **Resultaten bekijken**

<!-- SCREENSHOT: retro-08-loonrun-workflow.png -->
> **Screenshot 8 — Loonrun uitvoeren (workflow)**  
> Toon: het scherm *Loonrun uitvoeren* met de vier stappen en de actieve retro-periode zichtbaar.  
> Bestandsnaam: `screenshots/retro-08-loonrun-workflow.png`

<!-- SCREENSHOT: retro-09-bevestiging-loonrun.png -->
> **Screenshot 9 — Bevestiging definitieve loonrun**  
> Toon: bevestigingsdialoog vóór definitief maken.  
> Bestandsnaam: `screenshots/retro-09-bevestiging-loonrun.png`

---

### Stap 5.7 — Bedrijfskalender terugzetten

Na de retro-verwerking:

1. Zet **Huidig jaar** en **Huidige periode** op de **werkelijk actuele** periode terug.
2. Controleer dat normale maandverwerking weer op de juiste periode staat.

<!-- SCREENSHOT: retro-10-kalender-terug.png -->
> **Screenshot 10 — Kalender terug naar actuele periode**  
> Toon: bedrijfsrecord met huidige periode weer op “nu”.  
> Bestandsnaam: `screenshots/retro-10-kalender-terug.png`

---

## 6. Stap-voor-stap: retro voor het hele bedrijf

Gebruik dit als **meerdere werknemers** in dezelfde eerdere periode gecorrigeerd moeten worden.

1. **Bedrijfskalender** en **betaalperiode Open** — zelfde als §5.2.
2. Pas **doorlopende instructies** per werknemer aan, of gebruik bulk-materialisatie via **Loonrun uitvoeren**:
   - Selecteer alle betrokken werknemers.
   - **Periodetransacties genereren** voor het bedrijf.
   - **Preview amounts** / bedragen preview.
   - **Loonrun uitvoeren** definitief.
3. Overweeg een **Interim run** op de betaalperiode als er al een eerdere definitieve run was (zie §7.2).

<!-- SCREENSHOT: retro-11-loonrun-alle-werknemers.png -->
> **Screenshot 11 — Loonrun met meerdere werknemers geselecteerd**  
> Toon: werknemerslijst met selectievakjes en preview-totalen.  
> Bestandsnaam: `screenshots/retro-11-loonrun-alle-werknemers.png`

---

## 7. Scenario’s

### 7.1 — Basissalaris was te laag in periode 2/2026

| Stap | Actie |
|------|--------|
| 1 | Kalender → periode 2 / 2026; periode Open |
| 2 | Doorlopende instructie `[1001]` met juist bedrag en ingangsdatum 1 feb 2026 |
| 3 | Periodetransacties genereren |
| 4 | Bedragen berekenen; log controleren (o.a. belasting `[1019]`) |
| 5 | Loonrun definitief |
| 6 | Kalender terug naar actuele periode |

### 7.2 — Periode had al een definitieve run; u wilt corrigeren

1. Zet betaalperiode op **Open**.
2. Maak op het scherm **Betaalperioden** een nieuwe run van type **Tussentijds** (*Interim*) indien u de historie van runs wilt scheiden.
3. Pas invoer aan, preview, en voer opnieuw **Loonrun uitvoeren** uit.
4. Vergelijk resultaatregels met de vorige run.

<!-- SCREENSHOT: retro-12-interim-run.png -->
> **Screenshot 12 — Tussentijdse (interim) run aanmaken**  
> Toon: betaalperiode-detail met runs-panel en nieuwe run type *Tussentijds*.  
> Bestandsnaam: `screenshots/retro-12-interim-run.png`

### 7.3 — Alleen vakantie-uitkering in één maand anders

1. Gebruik **handmatige overschrijving** op component `[1006]` voor die periode.
2. Laat de doorlopende instructie ongewijzigd voor andere maanden.
3. Bereken opnieuw; controleer in de log de loonbelastingregels voor speciale uitkeringen (`1020` overuren, `1021` vakantie, `1022` bonus, `1024` eenmalige uitkering, `1025` extra inkomen, `1048` jubileum) indien van toepassing.

### 7.4 — Terugwerkende wijziging in belastingtarieven (platform)

De payroll-engine kiest belastingregels op basis van de **einddatum van de betaalperiode**. Als de Belastingdienst tarieven met terugwerkende kracht wijzigt:

- De **platformbeheerder** voert nieuwe regels in met de juiste **Ingangsdatum** (`effective from`).
- U hoeft in de tenant-app meestal **geen aparte retro-instelling** te doen: herbereken de periode opnieuw nadat de nieuwe regels actief zijn.
- Controleer in de berekeningslog of de verwachte tarieven en aftrekposten (belastingvrij, beroepskosten) zijn toegepast.

> Dit scenario vereist geen aparte tenant-screenshot tenzij u het platform-scherm *Belastingtarieven per land* wilt documenteren voor interne beheerders.

---

## 8. Controleren met de berekeningslog

Bij retro is de **berekeningslog** essentieel:

- Elke looncomponent staat in **verwerkingsvolgorde**.
- Per regel ziet u **factor**, **bedrag**, en **hoe het bedrag is bepaald**.
- Voor samengestelde bedragen (bijv. `[1004] Belastbaar inkomen`) toont de log uit **welke componenten** het totaal is opgebouwd.
- Bij formule-componenten ziet u welke **andere componenten** als input zijn gebruikt.

**Aanbevolen controlelijst vóór definitieve loonrun:**

- [ ] Juiste betaalperiode en datumbereik in de samenvatting
- [ ] Basissalaris en vaste toelagen kloppen met de correctie
- [ ] Belastbaar inkomen en loonbelasting (`1019`) zijn plausibel
- [ ] Speciale loonbelasting (`1020`–`1025`, `1048`) klopt wanneer van toepassing
- [ ] Netto loon (`1026`) komt overeen met verwachting
- [ ] Handmatige overschrijvingen zijn bewust en gedocumenteerd in *Opmerkingen*

<!-- SCREENSHOT: retro-13-berekeningslog-detail.png -->
> **Screenshot 13 — Fragment berekeningslog met onderbouwing**  
> Toon: logregel voor belastbaar inkomen of loonbelasting met onderliggende componenten.  
> Bestandsnaam: `screenshots/retro-13-berekeningslog-detail.png`

---

## 9. Veelgestelde vragen

### Wat is het verschil tussen doorlopende instructie en handmatige overschrijving?

| | Doorlopende instructie | Handmatige overschrijving |
|--|----------------------|---------------------------|
| **Geldigheid** | Van ingangsdatum t/m einddatum (elke periode) | Alleen de geselecteerde betaalperiode |
| **Bij opnieuw genereren** | Wordt bijgewerkt (tenzij override op instructie) | Blijft behouden |
| **Typisch gebruik** | Structurele salariswijziging | Eenmalige correctie |

### Kan ik een gesloten periode corrigeren?

Ja, mits u de status wijzigt naar **Open** en u de rechten heeft. Zonder Open-status blokkeert *Loonrun uitvoeren* met de melding dat de periode gesloten is.

### Wordt de bedrijfskalender automatisch verder gezet na een retro-run?

Bij een **normale** definitieve loonrun op de actieve periode kan de kalender **automatisch doorschuiven** naar de volgende periode. Na een **retro** op een eerdere periode: controleer altijd of de kalender nog klopt en zet deze zo nodig handmatig terug (§5.7).

### Verlies ik historische gegevens bij een retro?

Eerdere resultaatregels en runs blijven in principe bestaan. Een nieuwe run voegt **nieuwe** resultaten toe. Documenteer intern welke run leidend is voor rapportage en afdracht.

### Moet ik altijd via Loonrun uitvoeren, of is Bedragen berekenen genoeg?

- **Bedragen berekenen** op het werknemersscherm: geschikt voor **controle** en om transacties op te slaan in de periode (met manage-recht).
- **Loonrun uitvoeren** (definitief): nodig voor **officiële payroll-resultaten**, boekingen en netto-uitbetaling over het hele bedrijf.

---

## 10. Fouten en oplossingen

| Probleem | Mogelijke oorzaak | Oplossing |
|----------|-------------------|-----------|
| “Geen betaalperiode gevonden” | Kalender niet ingevuld of perioden niet gegenereerd | Bedrijf kalender invullen; *Betaalperioden regenereren* |
| “Periode is gesloten” | Status `CLOSED` | Status wijzigen naar `OPEN` |
| Bedrag verandert niet na aanpassing instructie | Handmatige overschrijving op transactie | Overschrijving wissen of transactie handmatig bijwerken |
| Verkeerd bedrag ondanks juiste instructie | Oude transactie niet ververst | Opnieuw *Periodetransacties genereren* |
| Geen knop *Bedragen berekenen* | Ontbrekend recht | `PAY_PERIOD_VIEW` / standing manage vragen |
| Belasting lijkt onverwacht | Speciale uitkeringen, art. 17, belastingvrij | Berekeningslog en §7.3 / §7.4 raadplegen |

---

## 11. Screenshot-lijst

Lever de onderstaande afbeeldingen aan in map `docs/guides/screenshots/` (of een map die u met de PDF-tool deelt). Vul de placeholders in dit document daarna in met de echte bestandsnamen.

| # | Bestandsnaam | Onderwerp |
|---|--------------|-----------|
| 1 | `retro-01-werknemer-looninvoer-tab.png` | Werknemer → tabblad Looninvoer |
| 2 | `retro-02-bedrijf-kalender.png` | Bedrijf bewerken: huidig jaar/periode |
| 3 | `retro-03-betaalperiode-open.png` | Betaalperiodenlijst, status Open |
| 4 | `retro-04-doorlopende-instructie.png` | Doorlopende instructie met ingangsdatum |
| 5 | `retro-05-handmatige-overschrijving.png` | Periodetransactie met overschrijving |
| 6 | `retro-06-materialisatie-resultaat.png` | Resultaat na genereren |
| 7 | `retro-07-berekenen-en-log.png` | Berekenen + log-knoppen |
| 8 | `retro-08-loonrun-workflow.png` | Loonrun uitvoeren — workflow |
| 9 | `retro-09-bevestiging-loonrun.png` | Bevestiging definitieve run |
| 10 | `retro-10-kalender-terug.png` | Kalender terug naar actueel |
| 11 | `retro-11-loonrun-alle-werknemers.png` | Meerdere werknemers geselecteerd |
| 12 | `retro-12-interim-run.png` | Interim run aanmaken |
| 13 | `retro-13-berekeningslog-detail.png` | Log met onderbouwing bedragen |

**Optioneel (beheerders):**

| # | Bestandsnaam | Onderwerp |
|---|--------------|-----------|
| 14 | `retro-14-nav-payroll-menu.png` | Navigatiemenu Payroll-groep |
| 15 | `retro-15-platform-belastingtarieven.png` | Platform: belastingtarieven per land |

---

## Volgende stap: PDF

Wanneer de screenshots klaar zijn:

1. Plaats de PNG-bestanden in `docs/guides/screenshots/`.
2. Vervang in dit document de screenshot-placeholders door echte afbeeldingen, bijvoorbeeld:  
   `![Werknemer looninvoer](screenshots/retro-01-werknemer-looninvoer-tab.png)`
3. Geef door dat de PDF gegenereerd mag worden — dan kan een printbare versie (bijv. via Pandoc of een HTML-export) worden aangemaakt.

---

*Einde concept-markup.*
