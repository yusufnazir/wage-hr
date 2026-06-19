"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  fetchTenantCurrencies,
  fetchTenantDepartments,
  fetchTenantEmployees,
  fetchTenantJobs,
  fetchTenantPayPeriods,
  fetchTenantPaymentLocations,
  fetchTenantWageComponents,
  fetchTenantWorkTimes,
} from "@/lib/api";

type StepStatus = "done" | "available" | "locked" | "coming_soon";

type Step = {
  id:
    | "company"
    | "currencies"
    | "payment_locations"
    | "departments"
    | "jobs"
    | "work_times"
    | "shifts"
    | "wage_components"
    | "employees"
    | "run_payroll";
  title: string;
  href?: string;
  comingSoon?: boolean;
  dependsOn: Step["id"][];
};

type ProbeState = {
  currenciesAssigned: boolean | null;
  paymentLocations: boolean | null;
  departments: boolean | null;
  jobs: boolean | null;
  workTimes: boolean | null;
  wageComponents: boolean | null;
  employees: boolean | null;
  payPeriods: boolean | null;
};

const STEPS: Step[] = [
  { id: "company", title: "Create company", href: "/app/companies/new", dependsOn: [] },
  { id: "currencies", title: "Setup currencies", href: "/app/tenant-currencies", dependsOn: ["company"] },
  { id: "payment_locations", title: "Add payment locations", href: "/app/payment-locations", dependsOn: ["currencies"] },
  { id: "departments", title: "Setup departments", href: "/app/departments", dependsOn: ["company"] },
  { id: "jobs", title: "Setup job descriptions", href: "/app/jobs", dependsOn: ["departments"] },
  { id: "work_times", title: "Setup work times", href: "/app/work-times", dependsOn: ["company"] },
  { id: "shifts", title: "Setup shifts", comingSoon: true, dependsOn: ["work_times"] },
  { id: "wage_components", title: "Setup wage components", href: "/app/wage-components", dependsOn: ["company"] },
  { id: "employees", title: "Setup employees", href: "/app/employees", dependsOn: ["jobs", "work_times", "wage_components"] },
  { id: "run_payroll", title: "Run payroll", href: "/app/run-payroll", dependsOn: ["employees", "payment_locations"] },
];

export function OnboardingChecklist({
  title = "Setup checklist",
  subtitle = "Complete these steps to finish your payroll setup.",
}: {
  title?: string;
  subtitle?: string;
}) {
  const { hasCompany, primaryCompanyId, me } = useTenantAppSession();

  const [probe, setProbe] = useState<ProbeState>({
    currenciesAssigned: null,
    paymentLocations: null,
    departments: null,
    jobs: null,
    workTimes: null,
    wageComponents: null,
    employees: null,
    payPeriods: null,
  });

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (hasCompany !== true) {
        if (!cancelled) {
          setProbe((p) => ({
            ...p,
            currenciesAssigned: null,
            paymentLocations: null,
            departments: null,
            jobs: null,
            workTimes: null,
            wageComponents: null,
            employees: null,
            payPeriods: null,
          }));
        }
        return;
      }

      // Currencies is tenant-level (no companyId).
      const currencies = await fetchTenantCurrencies();
      const currenciesAssigned = currencies.ok ? currencies.assignedCodes.length > 0 : null;

      // Company-scoped probes need a company id; we use the first/primary one if available.
      const cid = primaryCompanyId;
      const canProbeCompanyScoped = Boolean(cid);

      const safeSet = (patch: Partial<ProbeState>) => {
        if (!cancelled) setProbe((prev) => ({ ...prev, ...patch }));
      };

      safeSet({ currenciesAssigned });

      if (!canProbeCompanyScoped) {
        safeSet({
          paymentLocations: null,
          departments: null,
          jobs: null,
          workTimes: null,
          wageComponents: null,
          employees: null,
          payPeriods: null,
        });
        return;
      }

      const [
        paymentLocations,
        departments,
        jobs,
        workTimes,
        wageComponents,
        employees,
        payPeriods,
      ] = await Promise.all([
        fetchTenantPaymentLocations({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({ ok: false as const, status: 0 })),
        fetchTenantDepartments({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({ ok: false as const, status: 0 })),
        fetchTenantJobs({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({ ok: false as const, status: 0 })),
        fetchTenantWorkTimes({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({ ok: false as const, status: 0 })),
        fetchTenantWageComponents({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({
          ok: false as const,
          status: 0,
        })),
        fetchTenantEmployees({ companyId: cid!, page: 0, size: 1, active: true }).catch(() => ({ ok: false as const, status: 0 })),
        fetchTenantPayPeriods({ companyId: cid!, page: 0, size: 1 }).catch(() => ({ ok: false as const, status: 0 })),
      ]);

      safeSet({
        paymentLocations: paymentLocations.ok ? paymentLocations.totalElements > 0 : null,
        departments: departments.ok ? departments.totalElements > 0 : null,
        jobs: jobs.ok ? jobs.totalElements > 0 : null,
        workTimes: workTimes.ok ? workTimes.totalElements > 0 : null,
        wageComponents: wageComponents.ok ? wageComponents.totalElements > 0 : null,
        employees: employees.ok ? employees.totalElements > 0 : null,
        payPeriods: payPeriods.ok ? payPeriods.totalElements > 0 : null,
      });
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [hasCompany, primaryCompanyId, me.userId]);

  const completionById = useMemo(() => {
    const done: Record<Step["id"], boolean> = {
      company: hasCompany === true,
      currencies: probe.currenciesAssigned === true,
      payment_locations: probe.paymentLocations === true,
      departments: probe.departments === true,
      jobs: probe.jobs === true,
      work_times: probe.workTimes === true,
      shifts: false,
      wage_components: probe.wageComponents === true,
      employees: probe.employees === true,
      run_payroll: (probe.payPeriods === true && probe.employees === true && probe.paymentLocations === true) || false,
    };
    return done;
  }, [hasCompany, probe]);

  const statusById = useMemo(() => {
    const status: Record<Step["id"], StepStatus> = {} as Record<Step["id"], StepStatus>;
    for (const step of STEPS) {
      if (step.comingSoon) {
        status[step.id] = "coming_soon";
        continue;
      }
      if (completionById[step.id]) {
        status[step.id] = "done";
        continue;
      }
      const depsMet = step.dependsOn.every((d) => completionById[d]);
      status[step.id] = depsMet ? "available" : "locked";
    }
    return status;
  }, [completionById]);

  const completedCount = useMemo(
    () => STEPS.filter((s) => !s.comingSoon && completionById[s.id]).length,
    [completionById],
  );
  const totalCount = useMemo(() => STEPS.filter((s) => !s.comingSoon).length, []);

  const nextRecommended = useMemo(() => {
    return STEPS.find((s) => statusById[s.id] === "available") ?? null;
  }, [statusById]);

  const canCreateCompany = me.privileges.includes("COMPANY_MANAGE");
  const canManageCurrencies = me.privileges.includes("TENANT_CURRENCY_EDIT") || me.privileges.includes("TENANT_CURRENCY_VIEW");

  function actionHref(step: Step): string | null {
    if (step.id === "company") return canCreateCompany ? (step.href ?? null) : null;
    if (step.id === "currencies") return canManageCurrencies ? (step.href ?? null) : null;
    return step.href ?? null;
  }

  return (
    <section className="rounded-xl border border-border bg-surface p-7 shadow-sm" data-testid="onboarding-checklist">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-sm font-semibold text-foreground">{title}</h2>
          <p className="mt-1 text-sm text-muted">{subtitle}</p>
        </div>
        <p className="text-sm text-muted">
          <span className="font-semibold text-foreground">{completedCount}</span> / {totalCount} completed
        </p>
      </div>

      {nextRecommended ? (
        <div className="mt-4 rounded-lg border border-border bg-background/40 px-4 py-3">
          <p className="text-sm text-muted">
            Next recommended: <span className="font-semibold text-foreground">{nextRecommended.title}</span>
          </p>
        </div>
      ) : null}

      <ol className="mt-5 grid gap-2">
        {STEPS.map((step, idx) => {
          const st = statusById[step.id];
          const href = actionHref(step);
          const disabled = st === "locked" || st === "coming_soon" || !href;

          const badgeClass =
            st === "done"
              ? "bg-success/10 text-success"
              : st === "available"
                ? "bg-primary/10 text-primary"
                : st === "coming_soon"
                  ? "bg-muted/20 text-muted"
                  : "bg-muted/20 text-muted";
          const badgeText = st === "done" ? "Done" : st === "available" ? "Ready" : st === "coming_soon" ? "Coming soon" : "Locked";

          return (
            <li
              key={step.id}
              className="flex flex-col gap-2 rounded-lg border border-border bg-background/30 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
              data-testid={`onboarding-step-${step.id}`}
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-foreground">
                    {idx + 1}. {step.title}
                  </span>
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${badgeClass}`}>{badgeText}</span>
                </div>
                {st === "locked" ? (
                  <p className="mt-1 text-xs text-muted">Complete earlier steps to unlock this.</p>
                ) : st === "coming_soon" ? (
                  <p className="mt-1 text-xs text-muted">This module hasn’t been added yet.</p>
                ) : null}
              </div>

              <div className="shrink-0">
                {disabled ? (
                  <button
                    type="button"
                    disabled
                    className="inline-flex items-center justify-center rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-muted opacity-60"
                  >
                    {st === "coming_soon" ? "Coming soon" : "Open"}
                  </button>
                ) : (
                  <Link
                    href={href}
                    className="inline-flex items-center justify-center rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90"
                  >
                    Open
                  </Link>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

