"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformLedgerTemplates,
  fetchPlatformWageComponentTemplate,
  putPlatformWageComponentTemplateLedgerLinks,
  type PlatformLedgerTemplateRow,
  type PlatformWageComponentTemplateRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "notFound" | "error";

export default function PlatformWageComponentTemplateLedgerPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [template, setTemplate] = useState<PlatformWageComponentTemplateRow | null>(null);
  const [ledgers, setLedgers] = useState<PlatformLedgerTemplateRow[]>([]);
  const [debitId, setDebitId] = useState<string>("");
  const [creditId, setCreditId] = useState<string>("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!me.platformSuperadmin) return;
    void (async () => {
      const tr = await fetchPlatformWageComponentTemplate(id);
      if (!tr.ok) {
        setLoad(tr.status === 403 ? "forbidden" : tr.status === 404 ? "notFound" : "error");
        return;
      }
      setTemplate(tr.template);
      setDebitId(tr.template.debitPlatformLedgerTemplateId ?? "");
      setCreditId(tr.template.creditPlatformLedgerTemplateId ?? "");
      const lr = await fetchPlatformLedgerTemplates({
        page: 0,
        size: 200,
        country: tr.template.countryCode,
        active: true,
        locale: me.locale,
      });
      if (!lr.ok) {
        setLoad("error");
        return;
      }
      setLedgers(lr.items);
      setLoad("ready");
    })();
  }, [id, me.locale, me.platformSuperadmin]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!template) return;
    setBusy(true);
    setError(null);
    try {
      await putPlatformWageComponentTemplateLedgerLinks(id, {
        debitPlatformLedgerTemplateId: debitId.trim() ? debitId.trim() : null,
        creditPlatformLedgerTemplateId: creditId.trim() ? creditId.trim() : null,
      });
      router.push("/app/platform-wage-component-templates");
    } catch {
      setError(t("platformWageComponentTemplates.msg.saveFailed"));
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.editLedger")}</h1>
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.error.notOperator")}</p>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-lg">
        <p className="text-sm text-muted">{t("platformWageComponentTemplates.state.loading")}</p>
      </div>
    );
  }

  if (load !== "ready" || !template) {
    const key =
      load === "forbidden"
        ? "platformWageComponentTemplates.error.forbidden"
        : load === "notFound"
          ? "platformWageComponentTemplates.error.notFound"
          : "platformWageComponentTemplates.error.load";
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-muted">{t(key)}</p>
        <Link href="/app/platform-wage-component-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("platformWageComponentTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformWageComponentTemplates.title.editLedger")}</h1>
        <div className="flex flex-wrap gap-3 text-sm">
          <Link
            href={`/app/platform-wage-component-templates/${id}/edit`}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            {t("platformWageComponentTemplates.link.fullEditor")}
          </Link>
          <Link href="/app/platform-wage-component-templates" className="font-medium text-primary underline-offset-4 hover:underline">
            {t("platformWageComponentTemplates.action.backToList")}
          </Link>
        </div>
      </div>

      <p className="text-sm text-muted">{t("platformWageComponentTemplates.helper.ledger")}</p>

      <div className="rounded border border-border bg-surface p-4 text-sm">
        <div>
          <span className="text-muted">{t("platformWageComponentTemplates.label.template")}: </span>
          <span className="font-mono text-xs">{template.templateCode}</span>
          <span className="text-muted"> — </span>
          <span>{template.name}</span>
        </div>
        <div className="mt-1 text-xs text-muted">
          {template.countryCode} / {template.phaseHint ?? "—"}
        </div>
      </div>

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("platformWageComponentTemplates.label.debitLedgerTemplate")}</span>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2"
            value={debitId}
            onChange={(e) => setDebitId(e.target.value)}
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
          <span className="text-muted">{t("platformWageComponentTemplates.label.creditLedgerTemplate")}</span>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2"
            value={creditId}
            onChange={(e) => setCreditId(e.target.value)}
          >
            <option value="">—</option>
            {ledgers.map((l) => (
              <option key={l.id} value={l.id}>
                {l.code} — {l.description}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={busy}
          className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
        >
          {t("platformWageComponentTemplates.action.save")}
        </button>
      </form>
    </div>
  );
}
