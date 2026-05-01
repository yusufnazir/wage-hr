"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchPlatformMailTemplate,
  putPlatformMailTemplate,
  type MailTemplateDetail,
  type MailTemplateLocalePayload,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

const LOCALES = ["en", "nl"] as const;
type LocaleTab = (typeof LOCALES)[number];

type LoadState = "loading" | "ready" | "forbidden" | "error";

type LocaleFields = { subject: string; bodyHtml: string };

export default function PlatformMailTemplateEditPage() {
  const params = useParams();
  const id = typeof params.id === "string" ? params.id : "";
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [item, setItem] = useState<MailTemplateDetail | null>(null);
  const [tab, setTab] = useState<LocaleTab>("en");
  const [byLocale, setByLocale] = useState<Record<string, LocaleFields>>({});
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!id) {
      return;
    }
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformMailTemplate(id);
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      setItem(null);
      return;
    }
    setItem(r.item);
    const next: Record<string, LocaleFields> = {};
    for (const loc of r.item.locales) {
      next[loc.locale] = { subject: loc.subject, bodyHtml: loc.bodyHtml };
    }
    setByLocale(next);
    setActive(r.item.active);
    setLoad("ready");
  }, [id]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const previewSrcDoc = useMemo(() => {
    const body = byLocale[tab]?.bodyHtml ?? "";
    return body.trim() ? body : "<p style=\"font-family:system-ui\">(empty)</p>";
  }, [byLocale, tab]);

  async function onSave() {
    if (!item) {
      return;
    }
    setBusy(true);
    setMsg(null);
    const locales: MailTemplateLocalePayload[] = LOCALES.map((loc) => ({
      locale: loc,
      subject: byLocale[loc]?.subject ?? "",
      bodyHtml: byLocale[loc]?.bodyHtml ?? "",
    }));
    try {
      await putPlatformMailTemplate(item.id, {
        ifUpdatedAt: item.updatedAt,
        active,
        locales,
      });
      setMsg(t("mailTemplates.edit.msg.saved"));
      await reload();
    } catch (e) {
      const m = e instanceof Error ? e.message : "";
      if (m.includes("409")) {
        setMsg(t("mailTemplates.edit.msg.conflict"));
        await reload();
      } else {
        setMsg(t("mailTemplates.edit.msg.saveFailed"));
      }
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.edit.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.edit.error.notOperator")}</p>
        <Link href="/app/platform-mail-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("mailTemplates.edit.back")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.edit.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.edit.error.forbidden")}</p>
        <Link href="/app/platform-mail-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("mailTemplates.edit.back")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.edit.title")}</h1>
        <p className="text-sm text-muted">{t("mailTemplates.edit.error.load")}</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          {t("mailTemplates.list.action.retry")}
        </button>
      </div>
    );
  }

  if (load === "loading" || !item) {
    return (
      <div className="mx-auto max-w-5xl">
        <p className="text-sm text-muted">{t("mailTemplates.edit.state.loading")}</p>
      </div>
    );
  }

  const msgIsSuccess = msg === t("mailTemplates.edit.msg.saved");

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="platform-mail-template-edit-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-lg font-semibold text-foreground">{t("mailTemplates.edit.title")}</h1>
          <p className="mt-1 font-mono text-xs text-muted">
            {item.code} · {item.contentVersion}
          </p>
        </div>
        <Link href="/app/platform-mail-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("mailTemplates.edit.back")}
        </Link>
      </div>

      {msg ? (
        <p
          className={`text-sm ${msgIsSuccess ? "text-foreground" : "text-red-600 dark:text-red-400"}`}
          data-testid="mail-template-edit-msg"
        >
          {msg}
        </p>
      ) : null}

      <label className="flex items-center gap-2 text-sm text-foreground">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} data-testid="mail-template-active" />
        <span>{t("mailTemplates.edit.label.active")}</span>
      </label>

      <div className="border-b border-border">
        <nav className="-mb-px flex flex-wrap gap-2" aria-label="Locales">
          {LOCALES.map((loc) => (
            <button
              key={loc}
              type="button"
              data-testid={`mail-template-tab-${loc}`}
              className={`border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                tab === loc
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted hover:border-border hover:text-foreground"
              }`}
              onClick={() => setTab(loc)}
            >
              {t("mailTemplates.edit.tab.locale").replace("{locale}", loc)}
            </button>
          ))}
        </nav>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("mailTemplates.edit.label.subject")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
              value={byLocale[tab]?.subject ?? ""}
              onChange={(e) =>
                setByLocale((s) => ({
                  ...s,
                  [tab]: { subject: e.target.value, bodyHtml: s[tab]?.bodyHtml ?? "" },
                }))
              }
              data-testid={`mail-template-subject-${tab}`}
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("mailTemplates.edit.label.body")}</span>
            <textarea
              className="min-h-[280px] w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-xs text-foreground shadow-sm"
              spellCheck={false}
              value={byLocale[tab]?.bodyHtml ?? ""}
              onChange={(e) =>
                setByLocale((s) => ({
                  ...s,
                  [tab]: { subject: s[tab]?.subject ?? "", bodyHtml: e.target.value },
                }))
              }
              data-testid={`mail-template-body-${tab}`}
            />
          </label>
        </div>
        <div className="space-y-2">
          <h2 className="text-sm font-medium text-foreground">{t("mailTemplates.edit.section.preview")}</h2>
          <iframe
            title="preview"
            className="h-[min(480px,70vh)] w-full rounded-md border border-border bg-white shadow-sm dark:bg-white"
            sandbox=""
            srcDoc={previewSrcDoc}
            data-testid="mail-template-preview"
          />
        </div>
      </div>

      <button
        type="button"
        disabled={busy}
        className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
        data-testid="mail-template-save"
        onClick={() => void onSave()}
      >
        {busy ? t("mailTemplates.edit.state.saving") : t("mailTemplates.edit.action.save")}
      </button>
    </div>
  );
}
