"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  deleteTenantBankTemplate,
  fetchTenantBankTemplate,
  patchActivateTenantBankTemplate,
  patchDeactivateTenantBankTemplate,
  putTenantBankTemplate,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function TenantBankTemplateEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const companyId = searchParams.get("companyId") ?? "";
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("BANK_TEMPLATE_VIEW");
  const canManage = me.privileges.includes("BANK_TEMPLATE_MANAGE");

  const [load, setLoad] = useState<LoadState>("loading");
  const [countryCode, setCountryCode] = useState("");
  const [platformSourceId, setPlatformSourceId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [bankName, setBankName] = useState("");
  const [swiftBic, setSwiftBic] = useState("");
  const [bankCode, setBankCode] = useState("");
  const [accountNumberFormat, setAccountNumberFormat] = useState("");
  const [currencyCode, setCurrencyCode] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"activate" | "deactivate" | "delete" | null>(null);

  const listHref = companyId ? `/app/bank-templates?companyId=${encodeURIComponent(companyId)}` : "/app/bank-templates";

  useEffect(() => {
    if (!canView) {
      setLoad("forbidden");
      return;
    }
    void (async () => {
      const r = await fetchTenantBankTemplate(id);
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCountryCode(r.template.countryCode);
      setPlatformSourceId(r.template.platformBankTemplateId);
      setName(r.template.name);
      setBankName(r.template.bankName ?? "");
      setSwiftBic(r.template.swiftBic ?? "");
      setBankCode(r.template.bankCode ?? "");
      setAccountNumberFormat(r.template.accountNumberFormat ?? "");
      setCurrencyCode(r.template.currencyCode ?? "");
      setActive(r.template.active);
      setLoad("ready");
    })();
  }, [canView, id]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canManage) return;
    setBusy(true);
    setError(null);
    try {
      await putTenantBankTemplate(id, {
        name: name.trim(),
        bankName: bankName.trim() || null,
        swiftBic: swiftBic.trim() || null,
        bankCode: bankCode.trim() || null,
        accountNumberFormat: accountNumberFormat.trim() || null,
        currencyCode: currencyCode.trim() ? currencyCode.trim().toUpperCase() : null,
        active,
      });
      router.push(listHref);
    } catch {
      setError(t("bankTemplates.msg.saveFailed"));
      setBusy(false);
    }
  }

  async function runPatch() {
    if (!confirm || !canManage) return;
    setBusy(true);
    setError(null);
    try {
      if (confirm === "deactivate") {
        await patchDeactivateTenantBankTemplate(id);
        const r = await fetchTenantBankTemplate(id);
        if (r.ok) setActive(r.template.active);
        setConfirm(null);
      } else if (confirm === "activate") {
        await patchActivateTenantBankTemplate(id);
        const r = await fetchTenantBankTemplate(id);
        if (r.ok) setActive(r.template.active);
        setConfirm(null);
      } else {
        await deleteTenantBankTemplate(id);
        router.push(listHref);
      }
    } catch {
      setError(confirm === "delete" ? t("bankTemplates.msg.deleteFailed") : t("bankTemplates.error.action"));
    } finally {
      setBusy(false);
    }
  }

  if (!canView) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title.edit")}</h1>
        <p className="text-sm text-muted">{t("bankTemplates.error.forbidden")}</p>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("bankTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    const key =
      load === "forbidden"
        ? "bankTemplates.error.forbidden"
        : load === "notFound"
          ? "bankTemplates.error.notFound"
          : "bankTemplates.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      {confirm && canManage ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-w-md rounded-lg border border-border bg-surface p-5 shadow-lg">
            <p className="text-sm text-foreground">
              {confirm === "deactivate"
                ? t("bankTemplates.confirm.deactivate").replace("{name}", name)
                : confirm === "activate"
                  ? t("bankTemplates.confirm.activate").replace("{name}", name)
                  : t("bankTemplates.confirm.delete").replace("{name}", name)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" className="rounded border border-border px-3 py-1.5 text-sm" onClick={() => setConfirm(null)}>
                {t("bankTemplates.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busy}
                onClick={() => void runPatch()}
              >
                {confirm === "deactivate"
                  ? t("bankTemplates.action.deactivate")
                  : confirm === "activate"
                    ? t("bankTemplates.action.activate")
                    : t("bankTemplates.action.delete")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title.edit")}</h1>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      {canManage ? (
        <div className="flex flex-wrap gap-2">
          {active ? (
            <button
              type="button"
              className="rounded border border-border px-3 py-1.5 text-sm"
              disabled={busy}
              onClick={() => setConfirm("deactivate")}
            >
              {t("bankTemplates.action.deactivate")}
            </button>
          ) : (
            <button
              type="button"
              className="rounded border border-border px-3 py-1.5 text-sm"
              disabled={busy}
              onClick={() => setConfirm("activate")}
            >
              {t("bankTemplates.action.activate")}
            </button>
          )}
          <button
            type="button"
            className="rounded border border-destructive px-3 py-1.5 text-sm text-destructive"
            disabled={busy}
            onClick={() => setConfirm("delete")}
          >
            {t("bankTemplates.action.delete")}
          </button>
        </div>
      ) : null}

      <form
        onSubmit={(e) => void onSubmit(e)}
        className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm"
      >
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.country")}</label>
          <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm font-mono" value={countryCode} readOnly />
        </div>
        {platformSourceId ? (
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.platformTemplateId")}</label>
            <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm font-mono" value={platformSourceId} readOnly />
          </div>
        ) : null}
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.name")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={150}
            required
            readOnly={!canManage}
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.bankName")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            maxLength={150}
            readOnly={!canManage}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.swiftBic")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={swiftBic}
              onChange={(e) => setSwiftBic(e.target.value.toUpperCase())}
              maxLength={11}
              readOnly={!canManage}
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.bankCode")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={bankCode}
              onChange={(e) => setBankCode(e.target.value)}
              maxLength={30}
              readOnly={!canManage}
            />
          </div>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.accountNumberFormat")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={accountNumberFormat}
            onChange={(e) => setAccountNumberFormat(e.target.value)}
            maxLength={100}
            readOnly={!canManage}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.currencyCode")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono uppercase"
              value={currencyCode}
              onChange={(e) => setCurrencyCode(e.target.value.toUpperCase())}
              maxLength={3}
              readOnly={!canManage}
            />
          </div>
          <div className="flex items-end gap-2 pb-1">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} disabled={!canManage} />
              {t("bankTemplates.label.active")}
            </label>
          </div>
        </div>
        {canManage ? (
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
          >
            {t("bankTemplates.action.save")}
          </button>
        ) : null}
      </form>
    </div>
  );
}
