"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from "react";

import { TenantWageComponentFormulaEditor } from "@/components/wage-components/TenantWageComponentFormulaEditor";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantLedgers,
  fetchTenantWageComponent,
  putTenantWageComponent,
  type TenantLedgerRow,
  type TenantWageComponentItem,
  type TenantWageComponentPutPayload,
} from "@/lib/api";
import {
  buildStoredFormulaExpression,
  formulaEditorStateFromStored,
  type FormulaEditorState,
} from "@/lib/wage-component-definition";
import { navLabel } from "@/messages/nav";

function deriveCodeSuffix(code: string, templateCode: string | null | undefined): string {
  if (!templateCode) return "";
  const tc = templateCode;
  if (code.toUpperCase() === tc.toUpperCase()) return "";
  const p = tc + "_";
  if (code.toUpperCase().startsWith(p.toUpperCase())) return code.slice(p.length);
  return code;
}

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error" | "legacy";

type EditForm = {
  name: string;
  codeSuffix: string;
  debitTenantLedgerId: string;
  creditTenantLedgerId: string;
  printOnPayslip: boolean;
  active: boolean;
};

const TABS = ["general", "definition", "rules", "ledgers"] as const;
type TabId = (typeof TABS)[number];

const tabButtonClass = (active: boolean) =>
  `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
    active
      ? "bg-primary/15 text-primary ring-1 ring-primary/40"
      : "text-muted hover:bg-surface-alt hover:text-foreground"
  }`;

function itemToForm(item: TenantWageComponentItem): EditForm {
  const suffix = deriveCodeSuffix(item.code, item.templateCode);
  return {
    name: item.name,
    codeSuffix: suffix,
    debitTenantLedgerId: item.debitTenantLedgerId ?? "",
    creditTenantLedgerId: item.creditTenantLedgerId ?? "",
    printOnPayslip: item.printOnPayslip,
    active: item.active,
  };
}

function ReadOnlyField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted">{label}</dt>
      <dd className="mt-0.5 text-sm text-foreground">{children}</dd>
    </div>
  );
}

function yesNo(value: boolean, t: (key: string) => string): string {
  return value ? t("wageComponents.value.yes") : t("wageComponents.value.no");
}

export default function WageComponentEditPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [activeTab, setActiveTab] = useState<TabId>("general");
  const [item, setItem] = useState<TenantWageComponentItem | null>(null);
  const [form, setForm] = useState<EditForm | null>(null);
  const [formulaState, setFormulaState] = useState<FormulaEditorState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ledgers, setLedgers] = useState<TenantLedgerRow[]>([]);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  useEffect(() => {
    void (async () => {
      setLoad("loading");
      const r = await fetchTenantWageComponent(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      if (!r.item.platformTemplateId) {
        setLoad("legacy");
        return;
      }
      setItem(r.item);
      setForm(itemToForm(r.item));
      setFormulaState(formulaEditorStateFromStored(r.item.formulaExpression));
      const lr = await fetchTenantLedgers(r.item.companyId);
      if (lr.ok) {
        setLedgers(
          lr.items.filter((x) => x.active || x.id === r.item.debitTenantLedgerId || x.id === r.item.creditTenantLedgerId),
        );
      } else {
        setLedgers([]);
      }
      setLoad("ready");
    })();
  }, [id]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form || !item || !formulaState) return;
    if (!form.name?.trim()) {
      setError(t("wageComponents.error.nameRequired"));
      setActiveTab("general");
      return;
    }

    let formulaExpression: string | null | undefined;
    if (item.calculationMethod === "FORMULA") {
      formulaExpression = buildStoredFormulaExpression(formulaState);
      if (formulaExpression != null && formulaExpression.length > 500) {
        setError(t("wageComponents.error.formulaTooLong"));
        setActiveTab("definition");
        return;
      }
    }

    setBusy(true);
    setError(null);
    try {
      const payload: TenantWageComponentPutPayload = {
        companyId: item.companyId,
        name: form.name.trim(),
        codeSuffix: form.codeSuffix.trim() === "" ? null : form.codeSuffix.trim(),
        debitTenantLedgerId: form.debitTenantLedgerId?.trim() ? form.debitTenantLedgerId.trim() : null,
        creditTenantLedgerId: form.creditTenantLedgerId?.trim() ? form.creditTenantLedgerId.trim() : null,
        printOnPayslip: form.printOnPayslip,
        active: form.active,
      };
      if (item.calculationMethod === "FORMULA") {
        payload.formulaExpression = formulaExpression ?? null;
      }
      await putTenantWageComponent(id, payload);
      showToast(t("wageComponents.msg.saved"));
      router.push("/app/wage-components");
    } catch (err) {
      const msg = err instanceof Error ? err.message : t("wageComponents.msg.saveFailed");
      setError(msg.includes("FORMULA_TOO_LONG") ? t("wageComponents.error.formulaTooLong") : msg);
      if (msg.includes("FORMULA")) setActiveTab("definition");
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.action.edit")}</h1>
        <p className="text-sm text-muted">{t("wageComponents.error.forbidden")}</p>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("wageComponents.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-4xl text-sm text-muted">{t("wageComponents.state.loading")}</p>;
  }

  if (load === "legacy") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">{t("wageComponents.error.legacyNotSupported")}</p>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("wageComponents.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load !== "ready" || !form || !item || !formulaState) {
    const message =
      load === "notFound" ? t("wageComponents.error.notFound") : load === "forbidden" ? t("wageComponents.error.forbidden") : t("wageComponents.error.load");
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">{message}</p>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("wageComponents.action.backToList")}
        </Link>
      </div>
    );
  }

  const tc = item.templateCode ?? "";
  const showFormula = item.calculationMethod === "FORMULA";

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.action.edit")}</h1>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("wageComponents.action.backToList")}
        </Link>
      </div>

      <div className="rounded border border-border bg-surface-alt/40 px-4 py-3 text-sm">
        <span className="text-muted">{t("wageComponents.label.code")}: </span>
        <span className="font-mono text-xs">{item.code}</span>
        {tc ? (
          <>
            <span className="mx-3 text-muted">|</span>
            <span className="text-muted">{t("wageComponents.label.template")}: </span>
            <span className="font-mono text-xs">{tc}</span>
          </>
        ) : null}
        <span className="mx-3 text-muted">|</span>
        <span className="font-medium text-foreground">{form.name}</span>
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="rounded-lg border border-border bg-surface">
        <div
          className="flex flex-wrap gap-2 border-b border-border px-4 pt-4 pb-2"
          role="tablist"
          aria-label={t("wageComponents.action.edit")}
        >
          {TABS.map((tid) => (
            <button
              key={tid}
              type="button"
              role="tab"
              aria-selected={activeTab === tid}
              className={tabButtonClass(activeTab === tid)}
              onClick={() => setActiveTab(tid)}
            >
              {t(`wageComponents.tab.${tid}`)}
            </button>
          ))}
        </div>

        <div className="min-h-[12rem] space-y-4 p-6">
          {activeTab === "general" ? (
            <div className="space-y-6">
              <div>
                <h2 className="text-sm font-semibold text-muted">{t("wageComponents.section.fromTemplate")}</h2>
                <dl className="mt-4 grid gap-4 sm:grid-cols-2">
                  <ReadOnlyField label={t("wageComponents.label.componentType")}>{item.componentType}</ReadOnlyField>
                  <ReadOnlyField label={t("wageComponents.label.category")}>{item.category}</ReadOnlyField>
                  <ReadOnlyField label={t("wageComponents.label.netEffect")}>{item.netEffect}</ReadOnlyField>
                  <ReadOnlyField label={t("wageComponents.label.phase")}>{item.phase}</ReadOnlyField>
                  <ReadOnlyField label={t("wageComponents.label.calculationMethod")}>{item.calculationMethod}</ReadOnlyField>
                  <ReadOnlyField label={t("wageComponents.label.processingOrder")}>
                    {item.processingOrder}
                    <span className="mt-1 block text-xs text-muted">{t("wageComponents.helper.processingOrder")}</span>
                  </ReadOnlyField>
                  {item.description ? (
                    <ReadOnlyField label={t("wageComponents.label.description")}>{item.description}</ReadOnlyField>
                  ) : null}
                  {item.defaultAmount ? (
                    <ReadOnlyField label={t("wageComponents.label.defaultAmount")}>{item.defaultAmount}</ReadOnlyField>
                  ) : null}
                </dl>
              </div>

              <div className="border-t border-border pt-6">
                <h2 className="text-sm font-semibold text-foreground">{t("wageComponents.section.tenant")}</h2>
                <div className="mt-4 space-y-4">
                  <label className="block space-y-1 text-sm">
                    <span className="text-muted">{t("wageComponents.label.name")}</span>
                    <input
                      required
                      className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                      value={form.name}
                      onChange={(e) => setForm((f) => (f ? { ...f, name: e.target.value } : f))}
                    />
                  </label>

                  <label className="block space-y-1 text-sm">
                    <span className="text-muted">{t("wageComponents.label.codeSuffix")}</span>
                    <input
                      className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
                      value={form.codeSuffix}
                      onChange={(e) => setForm((f) => (f ? { ...f, codeSuffix: e.target.value } : f))}
                      placeholder={tc ? `${tc}_…` : ""}
                    />
                    <span className="block text-xs text-muted">{t("wageComponents.hint.codeSuffix")}</span>
                  </label>

                  <fieldset className="grid gap-2 text-sm sm:grid-cols-2">
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={form.printOnPayslip}
                        onChange={(e) => setForm((f) => (f ? { ...f, printOnPayslip: e.target.checked } : f))}
                      />
                      {t("wageComponents.label.printOnPayslip")}
                    </label>
                    <label className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={form.active}
                        onChange={(e) => setForm((f) => (f ? { ...f, active: e.target.checked } : f))}
                      />
                      {t("wageComponents.label.active")}
                    </label>
                  </fieldset>
                </div>
              </div>
            </div>
          ) : null}

          {activeTab === "definition" ? (
            <div className="space-y-4">
              <ReadOnlyField label={t("wageComponents.label.calculationMethod")}>{item.calculationMethod}</ReadOnlyField>

              {showFormula ? (
                <>
                  <p className="text-xs text-muted">{t("wageComponents.formula.criteria.tenantEditable")}</p>
                  <TenantWageComponentFormulaEditor
                    calculationMethod={item.calculationMethod}
                    percentageBase={item.percentageBase}
                    roundingStrategy={item.roundingStrategy}
                    formulaState={formulaState}
                    onFormulaStateChange={setFormulaState}
                    t={t}
                  />
                </>
              ) : item.calculationMethod === "PERCENTAGE" && item.percentageBase ? (
                <ReadOnlyField label={t("wageComponents.label.percentageBase")}>
                  <span className="font-mono text-xs">{item.percentageBase}</span>
                </ReadOnlyField>
              ) : item.calculationMethod === "HOURLY" ? (
                <p className="text-sm text-muted">{t("wageComponents.formula.hintHourly")}</p>
              ) : null}
            </div>
          ) : null}

          {activeTab === "rules" ? (
            <div className="space-y-4">
              <p className="text-xs text-muted">{t("wageComponents.helper.taxFromTemplate")}</p>
              <dl className="grid gap-4 sm:grid-cols-2">
                <ReadOnlyField label={t("wageComponents.label.taxableWageTax")}>{yesNo(item.taxableWageTax, t)}</ReadOnlyField>
                <ReadOnlyField label={t("wageComponents.label.taxableSocialSecurity")}>
                  {yesNo(item.taxableSocialSecurity, t)}
                </ReadOnlyField>
                <ReadOnlyField label={t("wageComponents.label.taxablePension")}>{yesNo(item.taxablePension, t)}</ReadOnlyField>
                <ReadOnlyField label={t("wageComponents.label.taxableVacationReserve")}>
                  {yesNo(item.taxableVacationReserve, t)}
                </ReadOnlyField>
                {item.recurrence ? (
                  <ReadOnlyField label={t("wageComponents.label.recurrence")}>{item.recurrence}</ReadOnlyField>
                ) : null}
                {item.countryRuleKey ? (
                  <ReadOnlyField label={t("wageComponents.label.countryRuleKey")}>
                    <span className="font-mono text-xs">{item.countryRuleKey}</span>
                  </ReadOnlyField>
                ) : null}
              </dl>
              <TemplateTaxNote item={item} t={t} />
            </div>
          ) : null}

          {activeTab === "ledgers" ? (
            <div className="space-y-4">
              <p className="text-xs text-muted">{t("wageComponents.helper.ledger")}</p>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block space-y-1 text-sm">
                  <span className="text-muted">{t("wageComponents.label.debitLedger")}</span>
                  <select
                    className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                    value={form.debitTenantLedgerId ?? ""}
                    onChange={(e) => setForm((f) => (f ? { ...f, debitTenantLedgerId: e.target.value } : f))}
                  >
                    <option value="">—</option>
                    {ledgers.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.code} — {l.description}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block space-y-1 text-sm">
                  <span className="text-muted">{t("wageComponents.label.creditLedger")}</span>
                  <select
                    className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
                    value={form.creditTenantLedgerId ?? ""}
                    onChange={(e) => setForm((f) => (f ? { ...f, creditTenantLedgerId: e.target.value } : f))}
                  >
                    <option value="">—</option>
                    {ledgers.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.code} — {l.description}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-surface-alt/30 px-6 py-4">
          <div>{error ? <p className="text-sm text-destructive">{error}</p> : null}</div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {t("wageComponents.action.save")}
            </button>
            <Link
              href="/app/wage-components"
              className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
            >
              {t("wageComponents.action.cancel")}
            </Link>
          </div>
        </div>
      </form>
    </div>
  );
}

function TemplateTaxNote({ item, t }: { item: TenantWageComponentItem; t: (key: string) => string }) {
  if (item.platformCountryTaxRuleId) {
    return (
      <div className="rounded-md border border-border bg-background/80 p-3 text-xs leading-relaxed text-muted">
        <p className="font-medium text-foreground">{t("wageComponents.templatePreview.taxHeading")}</p>
        <p className="mt-1">{t("wageComponents.templatePreview.taxBody")}</p>
      </div>
    );
  }
  return <p className="text-xs text-muted">{t("wageComponents.templatePreview.noTaxRule")}</p>;
}
