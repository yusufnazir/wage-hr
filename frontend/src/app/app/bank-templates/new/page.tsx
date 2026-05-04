"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantBankTemplateCatalog,
  fetchTenantCompanies,
  postTenantBankTemplate,
  type TenantBankTemplateCatalogRow,
  type TenantCompanyItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type ValidationIssue = {
  fieldId: string;
  message: string;
};

export default function TenantBankTemplateNewPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId") ?? "";
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canManage = me.privileges.includes("BANK_TEMPLATE_MANAGE");

  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [catalog, setCatalog] = useState<TenantBankTemplateCatalogRow[]>([]);
  const [companyId, setCompanyId] = useState(paramCompanyId);
  const [platformTemplateId, setPlatformTemplateId] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [currencyCode, setCurrencyCode] = useState("");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [validationIssues, setValidationIssues] = useState<ValidationIssue[]>([]);

  const selectedTemplate = useMemo(
    () => catalog.find((x) => x.id === platformTemplateId) ?? null,
    [catalog, platformTemplateId],
  );

  const listHref = companyId ? `/app/bank-templates?companyId=${encodeURIComponent(companyId)}` : "/app/bank-templates";

  useEffect(() => {
    if (!canManage) return;
    void (async () => {
      const r = await fetchTenantCompanies({ page: 0, size: 100, active: true });
      if (!r.ok) {
        setError(t("bankTemplates.error.load"));
        return;
      }
      setCompanies(r.items);
      setCompanyId((prev) => prev || r.items[0]?.id || "");
    })();
  }, [canManage, t]);

  useEffect(() => {
    if (!canManage || !companyId) {
      setCatalog([]);
      setPlatformTemplateId("");
      return;
    }
    void (async () => {
      const r = await fetchTenantBankTemplateCatalog(companyId);
      if (!r.ok) {
        setCatalog([]);
        setPlatformTemplateId("");
        return;
      }
      setCatalog(r.items);
      setPlatformTemplateId((prev) => {
        if (prev && r.items.some((x) => x.id === prev)) return prev;
        return r.items[0]?.id ?? "";
      });
      if (!currencyCode && r.items[0]?.currencyCode) setCurrencyCode(r.items[0].currencyCode);
    })();
  }, [canManage, companyId]);

  useEffect(() => {
    if (selectedTemplate?.currencyCode && !currencyCode) {
      setCurrencyCode(selectedTemplate.currencyCode);
    }
  }, [selectedTemplate, currencyCode]);

  function focusField(fieldId: string) {
    const el = document.getElementById(fieldId) as HTMLElement | null;
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    if ("focus" in el) (el as HTMLInputElement | HTMLSelectElement).focus();
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canManage) return;

    const issues: ValidationIssue[] = [];
    if (!companyId) issues.push({ fieldId: "bank-template-company", message: "Company is required." });
    if (!platformTemplateId) issues.push({ fieldId: "bank-template-platform", message: "Platform template is required." });
    if (!accountNumber.trim()) issues.push({ fieldId: "bank-template-account-number", message: "Account number is required." });
    if (issues.length > 0) {
      setValidationIssues(issues);
      setError(null);
      focusField(issues[0].fieldId);
      return;
    }

    setBusy(true);
    setError(null);
    setValidationIssues([]);
    try {
      await postTenantBankTemplate({
        companyId,
        platformBankTemplateId: platformTemplateId,
        accountNumber: accountNumber.trim(),
        currencyCode: currencyCode.trim() ? currencyCode.trim().toUpperCase() : null,
        active,
      });
      router.push(listHref);
    } catch {
      setError(t("bankTemplates.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title.new")}</h1>
        <p className="text-sm text-muted">{t("bankTemplates.error.forbidden")}</p>
        <Link href="/app/bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title.new")}</h1>
        <Link href={listHref} className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>

      {validationIssues.length > 0 ? (
        <div className="rounded-md border border-destructive/40 bg-destructive/5 p-4">
          <p className="text-sm font-semibold text-destructive">Please fix the following fields:</p>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-destructive">
            {validationIssues.map((issue) => (
              <li key={issue.fieldId}>
                <button type="button" onClick={() => focusField(issue.fieldId)} className="underline underline-offset-2 hover:no-underline">
                  {issue.message}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {error ? <p className="text-sm font-medium text-destructive">{error}</p> : null}

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-md border border-border bg-surface p-5 shadow-sm">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.company")}</label>
          <select
            id="bank-template-company"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={companyId}
            onChange={(e) => {
              setCompanyId(e.target.value);
              setValidationIssues((prev) => prev.filter((x) => x.fieldId !== "bank-template-company"));
            }}
            required
            disabled={busy}
          >
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.platformTemplateId")}</label>
          <select
            id="bank-template-platform"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={platformTemplateId}
            onChange={(e) => {
              setPlatformTemplateId(e.target.value);
              setValidationIssues((prev) => prev.filter((x) => x.fieldId !== "bank-template-platform"));
            }}
            required
            disabled={busy || catalog.length === 0}
          >
            {catalog.map((row) => (
              <option key={row.id} value={row.id}>
                {row.name}
              </option>
            ))}
          </select>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.bankName")}</label>
            <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm" value={selectedTemplate?.bankName ?? ""} readOnly />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.swiftBic")}</label>
            <input className="w-full rounded border border-border bg-muted px-3 py-2 text-sm font-mono uppercase" value={selectedTemplate?.swiftBic ?? ""} readOnly />
          </div>
        </div>

        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("bankTemplates.label.accountNumber")}</label>
          <input
            id="bank-template-account-number"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm font-mono"
            value={accountNumber}
            onChange={(e) => {
              setAccountNumber(e.target.value);
              setValidationIssues((prev) => prev.filter((x) => x.fieldId !== "bank-template-account-number"));
            }}
            maxLength={100}
            required
            disabled={busy}
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
              disabled={busy}
            />
          </div>
          <div className="flex items-end gap-2 pb-1">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} disabled={busy} />
              {t("bankTemplates.label.active")}
            </label>
          </div>
        </div>

        <button type="submit" disabled={busy || !companyId} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50">
          {t("bankTemplates.action.create")}
        </button>
      </form>
    </div>
  );
}
