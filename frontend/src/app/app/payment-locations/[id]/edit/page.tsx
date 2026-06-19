"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import {
  fetchTenantBankTemplates,
  fetchTenantCurrencies,
  fetchTenantPaymentLocation,
  putTenantPaymentLocation,
  type TenantBankTemplateRow,
  type TenantCurrencyItem,
  type TenantPaymentLocationRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

export default function TenantPaymentLocationEditPage() {
  const { me } = useTenantAppSession();
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId") ?? "";
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("PAYMENT_LOCATION_MANAGE");

  const [original, setOriginal] = useState<TenantPaymentLocationRow | null>(null);
  const [currencies, setCurrencies] = useState<TenantCurrencyItem[]>([]);
  const [bankTemplates, setBankTemplates] = useState<TenantBankTemplateRow[]>([]);
  const [name, setName] = useState("");
  const [currency, setCurrency] = useState("");
  const [bankTemplateId, setBankTemplateId] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const listHref = paramCompanyId
    ? `/app/payment-locations?companyId=${encodeURIComponent(paramCompanyId)}`
    : "/app/payment-locations";

  useEffect(() => {
    void (async () => {
      const [locResult, cur] = await Promise.all([
        fetchTenantPaymentLocation(params.id),
        fetchTenantCurrencies(),
      ]);
      if (!locResult.ok) {
        setLoadError(locResult.status === 404 ? t("paymentLocations.error.notFound") : t("paymentLocations.error.load"));
        return;
      }
      const loc = locResult.item;
      setOriginal(loc);
      setName(loc.name);
      setCurrency(loc.currency);
      setBankTemplateId(loc.bankTemplateId ?? "");
      setAccountNumber(loc.accountNumberFull ?? "");

      if (cur.ok) {
        const assigned = cur.items.filter((c) => c.assigned);
        setCurrencies(assigned.length > 0 ? assigned : cur.items);
      }

      if (loc.paymentType === "BANK_ACCOUNT" && loc.companyId) {
        const bt = await fetchTenantBankTemplates({ companyId: loc.companyId, page: 0, size: 100 });
        if (bt.ok) {
          setBankTemplates(bt.items.filter((b) => b.active || b.id === (loc.bankTemplateId ?? "")));
        }
      }
    })();
  }, [params.id, t]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canManage || !original) return;
    setError(null);
    setBusy(true);
    try {
      await putTenantPaymentLocation(original.id, {
        name: name.trim(),
        currency: currency.trim().toUpperCase(),
        bankTemplateId: original.paymentType === "BANK_ACCOUNT" ? bankTemplateId || null : null,
        accountNumber: original.paymentType === "BANK_ACCOUNT" ? accountNumber.trim() || null : null,
      });
      showToast("Changes saved.");
      router.push(listHref);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("paymentLocations.error.saveFailed"));
      setBusy(false);
    }
  }

  if (loadError) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title.edit")}</h1>
        <p className="text-sm text-destructive">{loadError}</p>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("paymentLocations.action.backToList")}
        </Link>
      </div>
    );
  }

  if (!original) {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("paymentLocations.state.loading")}</p>
      </div>
    );
  }

  const isCash = original.paymentType === "CASH";

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("paymentLocations.title.edit")}</h1>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("paymentLocations.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5">
        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.name")}</span>
          <input
            type="text"
            maxLength={120}
            required
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={!canManage}
          />
        </label>

        <div className="space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.paymentType")}</span>
          <p className="rounded border border-border bg-surface-raised px-3 py-2 text-sm text-muted">
            {isCash ? t("paymentLocations.type.cash") : t("paymentLocations.type.bankAccount")}
          </p>
        </div>

        <label className="block space-y-1">
          <span className="text-sm text-muted">{t("paymentLocations.label.currency")}</span>
          {currencies.length > 0 ? (
            <select
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              required
              disabled={!canManage}
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
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              disabled={!canManage}
            />
          )}
        </label>

        {!isCash ? (
          <>
            <label className="block space-y-1">
              <span className="text-sm text-muted">{t("paymentLocations.label.bankTemplate")}</span>
              {bankTemplates.length === 0 ? (
                <p className="text-sm text-muted">No banks available.</p>
              ) : (
                <select
                  className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
                  value={bankTemplateId}
                  onChange={(e) => setBankTemplateId(e.target.value)}
                  required
                  disabled={!canManage}
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
                disabled={!canManage}
              />
            </label>
          </>
        ) : null}

        {canManage ? (
          <div className="flex gap-3 pt-1">
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {t("paymentLocations.action.save")}
            </button>
            <Link href={listHref} className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt">
              {t("paymentLocations.action.cancel")}
            </Link>
          </div>
        ) : (
          <Link href={listHref} className="text-sm text-primary underline-offset-2 hover:underline">
            {t("paymentLocations.action.backToList")}
          </Link>
        )}
      </form>
    </div>
  );
}


