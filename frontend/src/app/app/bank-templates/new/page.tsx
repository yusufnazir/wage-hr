"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { navLabel } from "@/messages/nav";

export default function TenantBankTemplateNewPage() {
  const { me } = useTenantAppSession();
  const searchParams = useSearchParams();
  const paramCompanyId = searchParams.get("companyId") ?? "";
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const canView = me.privileges.includes("BANK_TEMPLATE_VIEW");
  const canManageCatalog = me.platformSuperadmin;
  const listHref = paramCompanyId
    ? `/app/bank-templates?companyId=${encodeURIComponent(paramCompanyId)}`
    : "/app/bank-templates";

  if (!canView) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title")}</h1>
        <p className="text-sm text-muted">{t("bankTemplates.error.forbidden")}</p>
        <Link href="/app/bank-templates" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {t("bankTemplates.action.backToList")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg space-y-4">
      <h1 className="text-lg font-semibold text-foreground">{t("bankTemplates.title")}</h1>
      <p className="text-sm text-foreground">{t("bankTemplates.msg.autoProvisioned")}</p>
      <div className="flex flex-wrap gap-3">
        <Link href={listHref} className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground">
          {t("bankTemplates.action.backToList")}
        </Link>
        {canManageCatalog ? (
          <Link
            href="/app/platform-bank-templates"
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted/40"
          >
            {t("bankTemplates.action.manageCatalog")}
          </Link>
        ) : null}
      </div>
    </div>
  );
}
