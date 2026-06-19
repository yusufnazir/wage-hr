"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { showToast } from "@/components/ui/Toast";
import { WageComponentTemplatePreview } from "@/components/wage-components/WageComponentTemplatePreview";
import {
  createTenantWageComponent,
  fetchTenantCompanies,
  fetchTenantWageComponentTemplates,
  type TenantCompanyItem,
  type TenantWageComponentTemplateCatalogItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "error";

export default function WageComponentNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const presetCompanyId = searchParams.get("companyId") ?? "";

  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [templates, setTemplates] = useState<TenantWageComponentTemplateCatalogItem[]>([]);
  const [companyId, setCompanyId] = useState(presetCompanyId);
  const [templateId, setTemplateId] = useState("");
  const [codeSuffix, setCodeSuffix] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  useEffect(() => {
    void (async () => {
      setLoad("loading");
      const cr = await fetchTenantCompanies({ size: 100 });
      if (!cr.ok) {
        setLoad("error");
        return;
      }
      setCompanies(cr.items);
      const cid = presetCompanyId || (cr.items.length === 1 ? cr.items[0].id : "");
      setCompanyId(cid);
      if (cid) {
        const tr = await fetchTenantWageComponentTemplates(cid);
        if (tr.ok) setTemplates(tr.items);
      }
      setLoad("ready");
    })();
  }, [presetCompanyId, me.userId]);

  useEffect(() => {
    if (!companyId) {
      setTemplates([]);
      return;
    }
    void (async () => {
      const tr = await fetchTenantWageComponentTemplates(companyId);
      if (tr.ok) setTemplates(tr.items);
    })();
  }, [companyId]);

  const duplicableTemplates = useMemo(() => templates.filter((x) => x.duplicable), [templates]);

  const selectedTemplate = useMemo(
    () => duplicableTemplates.find((x) => x.id === templateId) ?? null,
    [duplicableTemplates, templateId],
  );

  useEffect(() => {
    if (templateId && !duplicableTemplates.some((x) => x.id === templateId)) {
      setTemplateId("");
    }
  }, [templateId, duplicableTemplates]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!companyId) {
      setError("Company is required.");
      return;
    }
    if (!templateId) {
      setError("Template is required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const created = await createTenantWageComponent({
        companyId,
        platformTemplateId: templateId,
        codeSuffix: codeSuffix.trim() || null,
        name: name.trim() || null,
      });
      showToast(`"${created.name}" created successfully.`);
      router.push("/app/wage-components");
    } catch (err) {
      setError(err instanceof Error ? err.message : t("wageComponents.msg.createFailed"));
      setBusy(false);
    }
  }

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.action.new")}</h1>
        <p className="text-sm text-muted">{t("wageComponents.error.forbidden")}</p>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("wageComponents.title")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return <p className="mx-auto max-w-xl text-sm text-muted">{t("wageComponents.state.loading")}</p>;
  }

  if (load === "error") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <p className="text-sm text-destructive">{t("wageComponents.error.load")}</p>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("wageComponents.title")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("wageComponents.action.new")}</h1>
        <Link href="/app/wage-components" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}
          {t("wageComponents.title")}
        </Link>
      </div>

      <p className="text-sm text-muted leading-relaxed">{t("wageComponents.new.intro")}</p>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-lg border border-border bg-surface p-6">
        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("wageComponents.label.companyId")}</span>
          <select
            required
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={companyId}
            onChange={(e) => {
              setCompanyId(e.target.value);
              setTemplateId("");
            }}
          >
            <option value="">—</option>
            {companies.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("wageComponents.label.template")}</span>
          <select
            required
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={templateId}
            onChange={(e) => setTemplateId(e.target.value)}
          >
            <option value="">—</option>
            {duplicableTemplates.map((tpl) => (
              <option key={tpl.id} value={tpl.id}>
                {tpl.name} ({tpl.templateCode})
              </option>
            ))}
          </select>
          {templates.length > 0 && duplicableTemplates.length < templates.length ? (
            <p className="text-xs text-muted">
              Some templates are fixed (non-duplicable) and are not listed here; they are provisioned separately.
            </p>
          ) : null}
        </label>

        {selectedTemplate ? <WageComponentTemplatePreview template={selectedTemplate} t={t} /> : null}

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("wageComponents.label.codeSuffix")} (optional)</span>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs text-foreground"
            value={codeSuffix}
            onChange={(e) => setCodeSuffix(e.target.value)}
            placeholder="e.g. TEAM_A (full code = template code + _ + suffix)"
          />
          <span className="block text-xs text-muted">{t("wageComponents.hint.codeSuffix")}</span>
        </label>

        <label className="block space-y-1 text-sm">
          <span className="text-muted">{t("wageComponents.label.name")} (optional)</span>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-foreground"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Defaults to template name"
          />
        </label>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={busy}
            className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {t("wageComponents.action.create")}
          </button>
          <Link
            href="/app/wage-components"
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt"
          >
            {t("wageComponents.action.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
