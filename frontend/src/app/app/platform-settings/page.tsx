"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { fetchPlatformSettings, patchPlatformSettings, postPlatformMailTest, type PlatformSettingEntry } from "@/lib/api";
import { navLabel } from "@/messages/nav";

const DATE_FORMATS = ["yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "ISO-8601"] as const;
const DATE_FORMAT_CUSTOM = "__custom__" as const;

const GENERAL_KEYS = ["platform.application_name", "platform.base_url", "platform.date_format"] as const;
const MINIO_KEYS = [
  "storage.minio.endpoint",
  "storage.minio.access_key",
  "storage.minio.secret_key",
  "storage.minio.bucket",
] as const;
const MAIL_KEYS = ["mail.api.base_url", "mail.api.project_key", "mail.api.username", "mail.api.password"] as const;

type TabId = "general" | "minio" | "mail";

type LoadState = "loading" | "ready" | "forbidden" | "error";

function buildValuesFromEntries(entries: PlatformSettingEntry[]): Record<string, string> {
  const m = new Map(entries.map((e) => [e.key, e.value ?? ""]));
  const v: Record<string, string> = {};
  for (const k of [...GENERAL_KEYS, ...MINIO_KEYS, ...MAIL_KEYS]) {
    v[k] = m.get(k) ?? "";
  }
  if (!v["platform.application_name"]?.trim()) {
    v["platform.application_name"] = m.get("platform.product_name") ?? "";
  }
  return v;
}

const TAB_KEYS: Record<TabId, string> = {
  general: "platformSettings.tab.general",
  minio: "platformSettings.tab.minio",
  mail: "platformSettings.tab.mail",
};

export default function PlatformSettingsPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);
  const [tab, setTab] = useState<TabId>("general");
  const [load, setLoad] = useState<LoadState>("loading");
  const [values, setValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [mailTestTo, setMailTestTo] = useState("");

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformSettings();
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setValues(buildValuesFromEntries(r.entries));
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function saveKeys(keys: readonly string[]) {
    setBusy(true);
    setMsg(null);
    try {
      const entries: PlatformSettingEntry[] = keys.map((key) => ({ key, value: values[key] ?? "" }));
      await patchPlatformSettings(entries);
      const r = await fetchPlatformSettings();
      if (!r.ok) {
        setMsg(t("platformSettings.msg.savedReload"));
      } else {
        setValues(buildValuesFromEntries(r.entries));
        setMsg(t("platformSettings.msg.saved"));
      }
    } catch (e) {
      setMsg(e instanceof Error ? e.message : t("platformSettings.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
  }

  async function sendMailTest() {
    setBusy(true);
    setMsg(null);
    try {
      await postPlatformMailTest(mailTestTo.trim());
      setMsg(t("platformSettings.msg.testSent"));
    } catch (e) {
      setMsg(e instanceof Error ? e.message : t("platformSettings.msg.testFailed"));
    } finally {
      setBusy(false);
    }
  }

  async function saveMailAndSendTest() {
    setBusy(true);
    setMsg(null);
    try {
      const entries: PlatformSettingEntry[] = MAIL_KEYS.map((key) => ({ key, value: values[key] ?? "" }));
      await patchPlatformSettings(entries);
      await postPlatformMailTest(mailTestTo.trim());
      const r = await fetchPlatformSettings();
      if (r.ok) {
        setValues(buildValuesFromEntries(r.entries));
      }
      setMsg(t("platformSettings.msg.savedAndTestSent"));
    } catch (e) {
      setMsg(e instanceof Error ? e.message : t("platformSettings.msg.saveAndTestFailed"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_settings")}</h1>
        <p className="text-sm text-muted">{t("platformSettings.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_settings")}</h1>
        <p className="text-sm text-muted">{t("platformSettings.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_settings")}</h1>
        <p className="text-sm text-muted">{t("platformSettings.error.load")}</p>
        <button
          type="button"
          className="text-sm font-medium text-primary underline-offset-4 hover:underline"
          onClick={() => void reload()}
        >
          {t("platformSettings.action.retry")}
        </button>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformSettings.state.loading")}</p>
      </div>
    );
  }

  const tabs: TabId[] = ["general", "minio", "mail"];
  const msgIsSuccess =
    msg === t("platformSettings.msg.saved") ||
    msg === t("platformSettings.msg.savedReload") ||
    msg === t("platformSettings.msg.testSent") ||
    msg === t("platformSettings.msg.savedAndTestSent");
  const currentDateFormat = (values["platform.date_format"] ?? "yyyy-MM-dd").trim();
  const dateFormatSelectValue = (DATE_FORMATS as readonly string[]).includes(currentDateFormat)
    ? currentDateFormat
    : DATE_FORMAT_CUSTOM;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("nav.platform_settings")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
      <p className="text-sm text-muted">{t("platformSettings.helper.intro")}</p>

      <div className="border-b border-border">
        <nav className="-mb-px flex flex-wrap gap-2" aria-label="Settings sections">
          {tabs.map((id) => (
            <button
              key={id}
              type="button"
              data-testid={`platform-settings-tab-${id}`}
              className={`border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                tab === id
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted hover:border-border hover:text-foreground"
              }`}
              onClick={() => setTab(id)}
            >
              {t(TAB_KEYS[id])}
            </button>
          ))}
        </nav>
      </div>

      {msg ? (
        <p
          className={`text-sm ${msgIsSuccess ? "text-foreground" : "text-red-600 dark:text-red-400"}`}
          data-testid="platform-settings-msg"
        >
          {msg}
        </p>
      ) : null}

      {tab === "general" ? (
        <section className="space-y-4 rounded-lg border border-border bg-surface p-6 shadow-sm">
          <h2 className="text-sm font-medium text-foreground">{t("platformSettings.section.application")}</h2>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.applicationName")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
              value={values["platform.application_name"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "platform.application_name": e.target.value }))}
              autoComplete="off"
              data-testid="platform-settings-application-name"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.baseUrl")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
              placeholder="https://app.example.com"
              value={values["platform.base_url"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "platform.base_url": e.target.value }))}
              autoComplete="off"
              data-testid="platform-settings-base-url"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.dateFormat")}</span>
            <select
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
              value={dateFormatSelectValue}
              onChange={(e) => {
                const v = e.target.value;
                if (v === DATE_FORMAT_CUSTOM) {
                  setValues((s) => ({
                    ...s,
                    "platform.date_format":
                      (s["platform.date_format"] ?? "").trim() &&
                      !(DATE_FORMATS as readonly string[]).includes((s["platform.date_format"] ?? "").trim())
                        ? (s["platform.date_format"] ?? "")
                        : "dd-MM-yyyy",
                  }));
                  return;
                }
                setValues((s) => ({ ...s, "platform.date_format": v }));
              }}
              data-testid="platform-settings-date-format"
            >
              {DATE_FORMATS.map((df) => (
                <option key={df} value={df}>
                  {df}
                </option>
              ))}
              <option value={DATE_FORMAT_CUSTOM}>{t("platformSettings.option.dateFormatCustom")}</option>
            </select>
          </label>
          {dateFormatSelectValue === DATE_FORMAT_CUSTOM ? (
            <label className="block space-y-1">
              <span className="text-xs font-medium text-muted">
                {t("platformSettings.label.dateFormatCustomPattern")}
              </span>
              <input
                className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
                placeholder="dd-MM-yyyy"
                value={values["platform.date_format"] ?? ""}
                onChange={(e) => setValues((s) => ({ ...s, "platform.date_format": e.target.value }))}
                autoComplete="off"
                data-testid="platform-settings-date-format-custom"
              />
            </label>
          ) : null}
          <button
            type="button"
            disabled={busy}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
            data-testid="platform-settings-save-general"
            onClick={() => void saveKeys(GENERAL_KEYS)}
          >
            {busy ? t("platformSettings.state.saving") : t("platformSettings.action.saveGeneral")}
          </button>
        </section>
      ) : null}

      {tab === "minio" ? (
        <section className="space-y-4 rounded-lg border border-border bg-surface p-6 shadow-sm">
          <h2 className="text-sm font-medium text-foreground">{t("platformSettings.section.minio")}</h2>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.minioEndpoint")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              placeholder="http://127.0.0.1:9000"
              value={values["storage.minio.endpoint"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "storage.minio.endpoint": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.minioAccessKey")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["storage.minio.access_key"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "storage.minio.access_key": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.minioSecretKey")}</span>
            <input
              type="password"
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["storage.minio.secret_key"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "storage.minio.secret_key": e.target.value }))}
              autoComplete="new-password"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.minioBucket")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["storage.minio.bucket"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "storage.minio.bucket": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <button
            type="button"
            disabled={busy}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
            data-testid="platform-settings-save-minio"
            onClick={() => void saveKeys(MINIO_KEYS)}
          >
            {busy ? t("platformSettings.state.saving") : t("platformSettings.action.saveMinio")}
          </button>
        </section>
      ) : null}

      {tab === "mail" ? (
        <section className="space-y-4 rounded-lg border border-border bg-surface p-6 shadow-sm">
          <h2 className="text-sm font-medium text-foreground">{t("platformSettings.section.mail")}</h2>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.mailBaseUrl")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              placeholder="https://api.mailprovider.com/v1"
              value={values["mail.api.base_url"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "mail.api.base_url": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.mailProjectKey")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["mail.api.project_key"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "mail.api.project_key": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.mailUsername")}</span>
            <input
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["mail.api.username"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "mail.api.username": e.target.value }))}
              autoComplete="off"
            />
          </label>
          <label className="block space-y-1">
            <span className="text-xs font-medium text-muted">{t("platformSettings.label.mailPassword")}</span>
            <input
              type="password"
              className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm text-foreground shadow-sm"
              value={values["mail.api.password"] ?? ""}
              onChange={(e) => setValues((s) => ({ ...s, "mail.api.password": e.target.value }))}
              autoComplete="new-password"
            />
          </label>
          <button
            type="button"
            disabled={busy}
            className="inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
            data-testid="platform-settings-save-mail"
            onClick={() => void saveKeys(MAIL_KEYS)}
          >
            {busy ? t("platformSettings.state.saving") : t("platformSettings.action.saveMail")}
          </button>

          <div className="border-t border-border pt-4">
            <p className="mb-3 text-xs text-muted">{t("platformSettings.helper.mailTest")}</p>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-muted">{t("platformSettings.label.mailTestTo")}</span>
              <input
                type="email"
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm"
                placeholder="recipient@example.com"
                value={mailTestTo}
                onChange={(e) => setMailTestTo(e.target.value)}
                autoComplete="off"
                data-testid="platform-settings-mail-test-to"
              />
            </label>
            <button
              type="button"
              disabled={busy || !mailTestTo.trim()}
              className="mt-3 inline-flex items-center justify-center rounded-md border border-border bg-background px-4 py-2 text-sm font-medium text-foreground shadow-sm hover:bg-muted/30 disabled:opacity-50"
              data-testid="platform-settings-send-mail-test"
              onClick={() => void sendMailTest()}
            >
              {busy ? t("platformSettings.state.sendingTest") : t("platformSettings.action.sendMailTest")}
            </button>
            <button
              type="button"
              disabled={busy || !mailTestTo.trim()}
              className="mt-3 ml-2 inline-flex items-center justify-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground shadow-sm hover:opacity-90 disabled:opacity-50"
              data-testid="platform-settings-save-mail-and-test"
              onClick={() => void saveMailAndSendTest()}
            >
              {busy ? t("platformSettings.state.sendingTest") : t("platformSettings.action.saveMailAndSendTest")}
            </button>
          </div>
        </section>
      ) : null}
    </div>
  );
}
