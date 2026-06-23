"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantBankTemplates,
  fetchTenantCompanies,
  fetchTenantCurrencies,
  postTenantPaymentLocation,
  type TenantBankTemplateRow,
  type TenantCompanyItem,
  type TenantCurrencyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

export default function TenantPaymentLocationNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId") ?? "";
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAYMENT_LOCATION_MANAGE");
  const canOpenCurrencies =
    me.privileges.includes("TENANT_CURRENCY_EDIT") || me.privileges.includes("TENANT_CURRENCY_VIEW");

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [currencies, setCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [bankTemplates, setBankTemplates] = useState<TenantBankTemplateRow[]>([]);
  const [companyId, setCompanyId] = useState(paramCompanyId);
  const [name, setName] = useState("");
  const [paymentType, setPaymentType] = useState<"CASH" | "BANK_ACCOUNT">("CASH");
  const [currency, setCurrency] = useState("");
  const [bankTemplateId, setBankTemplateId] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const listHref = companyId
    ? `/app/payment-locations?companyId=${encodeURIComponent(companyId)}`
    : "/app/payment-locations";

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      const [cr, cur] = await Promise.all([
        fetchTenantCompanies({ page: 0, size: 100, active: true }),
        fetchTenantCurrencies(),
      ]);
      if (!cr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      setCompanyId((prev) => prev || cr.items[0]?.id || "");
      if (cur.ok) {
        const assigned = cur.items.filter((c) => c.assigned);
        setCurrencies(assigned.length > 0 ? assigned : cur.items);
      }
      setLoad("ready");
    })();
  }, [canManage]);

  useEffect(() => {
    if (!canManage || !companyId) {
      setBankTemplates([]);
      setBankTemplateId("");
      return;
    }
    void (async () => {
      const r = await fetchTenantBankTemplates({ companyId, page: 0, size: 100 });
      if (!r.ok) {
        setBankTemplates([]);
        setBankTemplateId("");
        return;
      }
      const active = r.items.filter((bt) => bt.active);
      setBankTemplates(active);
      setBankTemplateId((prev) => {
        if (prev && active.some((bt) => bt.id === prev)) return prev;
        return active[0]?.id ?? "";
      });
    })();
  }, [canManage, companyId]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canManage) return;
    setError(null);
    setBusy(true);
    try {
      await postTenantPaymentLocation({
        companyId,
        name: name.trim(),
        paymentType,
        currency: currency.trim().toUpperCase(),
        bankTemplateId: paymentType === "BANK_ACCOUNT" ? bankTemplateId || null : null,
        accountNumber: paymentType === "BANK_ACCOUNT" ? accountNumber.trim() || null : null,
      });
      showToast(`"${name.trim()}" created successfully.`);
      router.push(listHref);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("paymentLocations.error.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title.new")}</h1>
        <p className="text-sm text-muted">{t("paymentLocations.error.forbidden")}</p>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("paymentLocations.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-3xl text-sm text-muted">{t("paymentLocations.state.loading")}</p>;
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title.new")}</h1>
        <p className="text-sm text-muted">{t("paymentLocations.error.load")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title.new")}</h1>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("paymentLocations.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5">
        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.company")}</span>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={companyId}
            onChange={(e) => setCompanyId(e.target.value)}
            required
          >
            {companies.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.name")}</span>
          <input
            type="text"
            maxLength={120}
            required
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        <div className="space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.paymentType")}</span>
          <div className="flex gap-4 pt-1">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="paymentType"
                value="CASH"
                checked={paymentType === "CASH"}
                onChange={() => setPaymentType("CASH")}
              />
              {t("paymentLocations.type.cash")}
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="radio"
                name="paymentType"
                value="BANK_ACCOUNT"
                checked={paymentType === "BANK_ACCOUNT"}
                onChange={() => setPaymentType("BANK_ACCOUNT")}
              />
              {t("paymentLocations.type.bankAccount")}
            </label>
          </div>
        </div>

        <div className="space-y-1">
          <label className="block space-y-1">
            <span className="text-sm text-muted">{t("paymentLocations.label.currency")}</span>
            {currencies.length > 0 ? (
              <select
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                value={currency}
                onChange={(e) => setCurrency(e.target.value)}
                required
              >
                <option value="">— Select currency —</option>
                {currencies.map((c) => (
                  <option key={c.id} value={c.code}>{c.code} — {c.displayName}</option>
                ))}
              </select>
            ) : (
              <input
                type="text"
                maxLength={3}
                required
                placeholder="e.g. SRD"
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              />
            )}
          </label>
          <p className="text-xs text-muted">
            {t("paymentLocations.hint.moreCurrencies")}{" "}
            {canOpenCurrencies ? (
              <>
                {t("paymentLocations.hint.enableMoreCurrencies")}{" "}
                <Link href="/app/tenant-currencies" className="font-medium text-primary underline-offset-4 hover:underline">
                  {t("paymentLocations.action.openCurrencies")}
                </Link>
              </>
            ) : (
              t("paymentLocations.hint.enableMoreCurrencies")
            )}
          </p>
        </div>

        {paymentType === "BANK_ACCOUNT" ? (
          <>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("paymentLocations.label.bankTemplate")}</span>
              {bankTemplates.length === 0 ? (
                <div
                  role="status"
                  className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-3 text-sm text-foreground"
                >
                  <p>No banks are set up for this company yet.</p>
                  <p className="mt-1 text-muted">
                    Banks are seeded from the platform catalog when the company is created. If the list is empty, open
                    Banks to review them or contact your platform operator.
                  </p>
                  {me.privileges.includes("BANK_TEMPLATE_VIEW") && companyId ? (
                    <Link
                      href={`/app/bank-templates?companyId=${encodeURIComponent(companyId)}`}
                      className="mt-2 inline-flex font-medium text-primary underline-offset-4 hover:underline"
                    >
                      Go to Banks
                    </Link>
                  ) : null}
                </div>
              ) : (
                <select
                  className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                  value={bankTemplateId}
                  onChange={(e) => setBankTemplateId(e.target.value)}
                  required
                >
                  <option value="">— Select bank —</option>
                  {bankTemplates.map((bt) => (
                    <option key={bt.id} value={bt.id}>
                      {bt.bankName ?? bt.platformTemplateName}{bt.swiftBic ? ` (${bt.swiftBic})` : ""}
                    </option>
                  ))}
                </select>
              )}
            </label>

            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("paymentLocations.label.accountNumber")}</span>
              <input
                type="text"
                maxLength={60}
                required
                className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
                value={accountNumber}
                onChange={(e) => setAccountNumber(e.target.value)}
              />
            </label>
          </>
        ) : null}

        <div className="flex gap-3 pt-1">
          <button
            type="submit"
            disabled={busy || (paymentType === "BANK_ACCOUNT" && bankTemplates.length === 0)}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
          >
            {t("paymentLocations.action.create")}
          </button>
          <Link href={listHref} className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
            {t("paymentLocations.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
