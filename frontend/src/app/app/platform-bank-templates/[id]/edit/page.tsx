"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformBankTemplate,
  patchActivatePlatformBankTemplate,
  patchDeactivatePlatformBankTemplate,
  putPlatformBankTemplate,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformBankTemplateEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [countryCode, setCountryCode] = useState("");
  const [name, setName] = useState("");
  const [bankName, setBankName] = useState("");
  const [swiftBic, setSwiftBic] = useState("");
  const [bankCode, setBankCode] = useState("");
  const [accountNumberFormat, setAccountNumberFormat] = useState("");
  const [currencyCode, setCurrencyCode] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"activate" | "deactivate" | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const r = await fetchPlatformBankTemplate(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCountryCode(r.template.countryCode);
      setName(r.template.name);
      setBankName(r.template.bankName ?? "");
      setSwiftBic(r.template.swiftBic ?? "");
      setBankCode(r.template.bankCode ?? "");
      setAccountNumberFormat(r.template.accountNumberFormat ?? "");
      setCurrencyCode(r.template.currencyCode ?? "");
      setActive(r.template.active);
      setLoad("ready");
    })();
  }, [id, me.platformSuperadmin]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await putPlatformBankTemplate(id, {
        name: name.trim(),
        bankName: bankName.trim() || null,
        swiftBic: swiftBic.trim() || null,
        bankCode: bankCode.trim() || null,
        accountNumberFormat: accountNumberFormat.trim() || null,
        currencyCode: currencyCode.trim() ? currencyCode.trim().toUpperCase() : null,
        active,
      });
      router.push("/app/platform-bank-templates");
    } catch {
      setError(t("platformBankTemplates.msg.saveFailed"));
      setBusy(false);
    }
  }

  async function runPatch() {
    if (!confirm) return;
    setBusy(true);
    setError(null);
    try {
      if (confirm === "deactivate") await patchDeactivatePlatformBankTemplate(id);
      else await patchActivatePlatformBankTemplate(id);
      const r = await fetchPlatformBankTemplate(id);
      if (r.ok) setActive(r.template.active);
      setConfirm(null);
    } catch {
      setError(t("platformBankTemplates.error.action"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformBankTemplates.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformBankTemplates.error.notOperator")}</p>
        <Link href="/app/platform-bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformBankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformBankTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    const key =
      load === "forbidden"
        ? "platformBankTemplates.error.forbidden"
        : load === "notFound"
          ? "platformBankTemplates.error.notFound"
          : "platformBankTemplates.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href="/app/platform-bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformBankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      {confirm ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-w-md rounded-lg border border-border bg-surface p-5 shadow-lg">
            <p className="text-sm text-foreground">
              {confirm === "deactivate"
                ? t("platformBankTemplates.confirm.deactivate").replace("{name}", name).replace("{country}", countryCode)
                : t("platformBankTemplates.confirm.activate").replace("{name}", name)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" className="rounded border border-border px-3 py-1.5 text-sm" onClick={() => setConfirm(null)}>
                {t("platformBankTemplates.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busy}
                onClick={() => void runPatch()}
              >
                {confirm === "deactivate"
                  ? t("platformBankTemplates.action.deactivate")
                  : t("platformBankTemplates.action.activate")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformBankTemplates.title.edit")}</h1>
        <Link href="/app/platform-bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformBankTemplates.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <div className="flex flex-wrap gap-2">
        {active ? (
          <button
            type="button"
            className="rounded border border-border px-3 py-1.5 text-sm"
            disabled={busy}
            onClick={() => setConfirm("deactivate")}
          >
            {t("platformBankTemplates.action.deactivate")}
          </button>
        ) : (
          <button
            type="button"
            className="rounded border border-border px-3 py-1.5 text-sm"
            disabled={busy}
            onClick={() => setConfirm("activate")}
          >
            {t("platformBankTemplates.action.activate")}
          </button>
        )}
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.country")}</label>
          <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm font-mono" value={countryCode} readOnly />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={150}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.bankName")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            maxLength={150}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.swiftBic")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={swiftBic}
              onChange={(e) => setSwiftBic(e.target.value.toUpperCase())}
              maxLength={11}
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformBankTemplates.label.bankCode")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={bankCode}
              onChange={(e) => setBankCode(e.target.value)}
              maxLength={30}
            />
          </div>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">
            {t("platformBankTemplates.label.accountNumberFormat")}
          </label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={accountNumberFormat}
            onChange={(e) => setAccountNumberFormat(e.target.value)}
            maxLength={100}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">
              {t("platformBankTemplates.label.currencyCode")}
            </label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={currencyCode}
              onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
              maxLength={3}
            />
          </div>
          <div className="flex items-end gap-2 pb-1">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              {t("platformBankTemplates.label.active")}
            </label>
          </div>
        </div>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformBankTemplates.action.save")}
        </button>
      </form>
    </div>
  );
}
