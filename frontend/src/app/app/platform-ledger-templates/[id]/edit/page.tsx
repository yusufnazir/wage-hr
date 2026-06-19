"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformLedgerTemplate,
  patchActivatePlatformLedgerTemplate,
  patchDeactivatePlatformLedgerTemplate,
  putPlatformLedgerTemplate,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformLedgerTemplateEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [countryCode, setCountryCode] = useState("");
  const [code, setCode] = useState("");
  const [descriptionEn, setDescriptionEn] = useState("");
  const [descriptionNl, setDescriptionNl] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"activate" | "deactivate" | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const r = await fetchPlatformLedgerTemplate(id, { locale: me.locale });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : r.status === 404 ? "notFound" : "error");
        return;
      }
      setCountryCode(r.template.countryCode);
      setCode(r.template.code);
      setDescriptionEn(r.template.translations.find((x) => x.locale === "en")?.description ?? "");
      setDescriptionNl(r.template.translations.find((x) => x.locale === "nl")?.description ?? "");
      setActive(r.template.active);
      setLoad("ready");
    })();
  }, [id, me.locale, me.platformSuperadmin]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await putPlatformLedgerTemplate(
        id,
        {
          countryCode: countryCode.trim().toUpperCase(),
          code: code.trim(),
          translations: [
            { locale: "en", description: descriptionEn.trim() },
            { locale: "nl", description: descriptionNl.trim() },
          ],
          active,
        },
        { locale: me.locale },
      );
      router.push("/app/platform-ledger-templates");
    } catch {
      setError(t("platformLedgerTemplates.msg.saveFailed"));
      setBusy(false);
    }
  }

  async function runPatch() {
    if (!confirm) return;
    setBusy(true);
    setError(null);
    try {
      if (confirm === "deactivate") await patchDeactivatePlatformLedgerTemplate(id, { locale: me.locale });
      else await patchActivatePlatformLedgerTemplate(id, { locale: me.locale });
      const r = await fetchPlatformLedgerTemplate(id, { locale: me.locale });
      if (r.ok) setActive(r.template.active);
      setConfirm(null);
    } catch {
      setError(t("platformLedgerTemplates.error.action"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title.edit")}</h1>
        <p className="text-sm text-muted">{t("platformLedgerTemplates.error.notOperator")}</p>
        <Link href="/app/platform-ledger-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformLedgerTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformLedgerTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready") {
    const key =
      load === "forbidden"
        ? "platformLedgerTemplates.error.forbidden"
        : load === "notFound"
          ? "platformLedgerTemplates.error.notFound"
          : "platformLedgerTemplates.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href="/app/platform-ledger-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformLedgerTemplates.action.backToList")}
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
                ? t("platformLedgerTemplates.confirm.deactivate").replace("{code}", code).replace("{country}", countryCode)
                : t("platformLedgerTemplates.confirm.activate").replace("{code}", code)}
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <button type="button" className="rounded border border-border px-3 py-1.5 text-sm" onClick={() => setConfirm(null)}>
                {t("platformLedgerTemplates.action.cancel")}
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground"
                disabled={busy}
                onClick={() => void runPatch()}
              >
                {confirm === "deactivate"
                  ? t("platformLedgerTemplates.action.deactivate")
                  : t("platformLedgerTemplates.action.activate")}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformLedgerTemplates.title.edit")}</h1>
        <Link href="/app/platform-ledger-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformLedgerTemplates.action.backToList")}
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
            {t("platformLedgerTemplates.action.deactivate")}
          </button>
        ) : (
          <button
            type="button"
            className="rounded border border-border px-3 py-1.5 text-sm"
            disabled={busy}
            onClick={() => setConfirm("activate")}
          >
            {t("platformLedgerTemplates.action.activate")}
          </button>
        )}
      </div>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.country")}</label>
          <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm font-mono" value={countryCode} readOnly />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.code")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            maxLength={64}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.descriptionEn")}</label>
          <textarea
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={descriptionEn}
            onChange={(e) => setDescriptionEn(e.target.value)}
            maxLength={500}
            rows={3}
            required
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformLedgerTemplates.label.descriptionNl")}</label>
          <textarea
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={descriptionNl}
            onChange={(e) => setDescriptionNl(e.target.value)}
            maxLength={500}
            rows={3}
            required
          />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          {t("platformLedgerTemplates.label.active")}
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformLedgerTemplates.action.save")}
        </button>
      </form>
    </div>
  );
}
