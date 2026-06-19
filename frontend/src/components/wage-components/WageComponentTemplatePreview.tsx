import type { TenantWageComponentTemplateCatalogItem } from "@/lib/api";

function boolLabel(v: boolean): string {
  return v ? "Yes" : "No";
}

type Props = {
  template: TenantWageComponentTemplateCatalogItem;
  /** i18n labels: (key) => string */
  t: (key: string) => string;
};

/**
 * Read-only summary of a catalog template for create/edit context (not on the list page).
 */
export function WageComponentTemplatePreview({ template, t }: Props) {
  return (
    <section
      className="space-y-3 rounded-lg border border-border bg-surface-alt/40 p-4 text-sm"
      aria-labelledby="wage-tpl-preview-title"
    >
      <h2 id="wage-tpl-preview-title" className="text-sm font-semibold text-foreground">
        {t("wageComponents.templatePreview.title")}
      </h2>
      {template.description ? <p className="text-muted leading-relaxed">{template.description}</p> : null}
      <dl className="grid gap-2 sm:grid-cols-2">
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.label.template")}</dt>
          <dd className="font-mono text-xs text-foreground">
            {template.templateCode}
            <span className="ml-2 font-sans text-muted">({template.countryCode})</span>
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.name")}</dt>
          <dd className="text-foreground">{template.name}</dd>
        </div>
        {template.phaseHint ? (
          <div>
            <dt className="text-xs font-medium text-muted">{t("wageComponents.label.phase")}</dt>
            <dd className="text-foreground">{template.phaseHint}</dd>
          </div>
        ) : null}
        {template.processingOrderHint != null ? (
          <div>
            <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.processingHint")}</dt>
            <dd>
              <span className="text-foreground">{template.processingOrderHint}</span>
              <span className="mt-0.5 block text-xs text-muted">{t("wageComponents.helper.processingOrder")}</span>
            </dd>
          </div>
        ) : null}
        {template.recurrence ? (
          <div>
            <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.recurrence")}</dt>
            <dd className="text-foreground">{template.recurrence}</dd>
          </div>
        ) : null}
        {template.countryRuleKey ? (
          <div className="sm:col-span-2">
            <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.countryRule")}</dt>
            <dd className="font-mono text-xs text-foreground">{template.countryRuleKey}</dd>
          </div>
        ) : null}
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.applyInPayroll")}</dt>
          <dd className="text-foreground">{boolLabel(template.applyInPayroll)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.auxiliary")}</dt>
          <dd className="text-foreground">{boolLabel(template.auxiliary)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.printOnPayslip")}</dt>
          <dd className="text-foreground">{boolLabel(template.printOnPayslip)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium text-muted">{t("wageComponents.templatePreview.duplicable")}</dt>
          <dd className="text-foreground">{boolLabel(template.duplicable)}</dd>
        </div>
      </dl>
      {template.platformCountryTaxRuleId ? (
        <div className="rounded-md border border-border bg-background/80 p-3 text-xs leading-relaxed text-muted">
          <p className="font-medium text-foreground">{t("wageComponents.templatePreview.taxHeading")}</p>
          <p className="mt-1">{t("wageComponents.templatePreview.taxBody")}</p>
        </div>
      ) : (
        <p className="text-xs text-muted">{t("wageComponents.templatePreview.noTaxRule")}</p>
      )}
    </section>
  );
}
