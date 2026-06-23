"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FilterChip } from "@/components/ui/FilterChip";
import { showToast } from "@/components/ui/Toast";
import {
  deleteTenantEmployee,
  fetchTenantCompanies,
  fetchTenantDepartments,
  fetchTenantEmployees,
  fetchTenantJobs,
  patchTenantEmployeeActive,
  type TenantCompanyItem,
  type TenantDepartmentItem,
  type TenantEmployeeItem,
  type TenantJobItem,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

type ActiveOption = "active" | "inactive";

const PAGE_SIZE = 18;

const AVATAR_GRADIENTS = [
  "radial-gradient(circle, #E2C3FF, #C9A6FF, #B18CFF)",
  "radial-gradient(circle, #FFD8B8, #FFC49E, #FFB085)",
  "radial-gradient(circle, #A2FFE0, #8AE6C8, #72CCB0)",
  "radial-gradient(circle, #FFB6D9, #E69FC2, #CC88AB)",
  "radial-gradient(circle, #B5E8FF, #9BD1E6, #82BACC)",
  "radial-gradient(circle, #FFF2B8, #E6DAA6, #CCC294)",
  "radial-gradient(circle, #A0FFEB, #8AE6D1, #72CCB8)",
  "radial-gradient(circle, #D4A5FF, #BE91E6, #A87DCC)",
  "radial-gradient(circle, #FFC0CB, #E6ADB9, #CC9AA7)",
  "radial-gradient(circle, #C7F0FF, #B0D8E6, #99C0CC)",
];

function initialsFor(first: string, last: string): string {
  const f = first?.trim()?.[0] ?? "";
  const l = last?.trim()?.[0] ?? "";
  return `${f}${l}`.toUpperCase() || "?";
}

function gradientFor(id: string): string {
  let sum = 0;
  for (let i = 0; i < id.length; i += 1) sum = (sum + id.charCodeAt(i)) >>> 0;
  return AVATAR_GRADIENTS[sum % AVATAR_GRADIENTS.length];
}

function activeBooleanFor(selection: ActiveOption[]): boolean | null {
  if (selection.length !== 1) return null;
  return selection[0] === "active";
}

export default function EmployeesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<TenantEmployeeItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [activeCount, setActiveCount] = useState(0);
  const [inactiveCount, setInactiveCount] = useState(0);
  const [companies, setCompanies] = useState<TenantCompanyItem[]>([]);
  const [departments, setDepartments] = useState<TenantDepartmentItem[]>([]);
  const [jobs, setJobs] = useState<TenantJobItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<{ item: TenantEmployeeItem } | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ item: TenantEmployeeItem } | null>(null);
  const [confirmBusy, setConfirmBusy] = useState(false);

  // Filters
  const [filterCompanyIds, setFilterCompanyIds] = useState<string[]>([]);
  const [filterFirstName, setFilterFirstName] = useState<string | null>(null);
  const [filterLastName, setFilterLastName] = useState<string | null>(null);
  const [filterActive, setFilterActive] = useState<ActiveOption[]>([]);

  const canManage = me.privileges.includes("EMPLOYEE_MANAGE");
  const hasAnyFilter =
    filterCompanyIds.length > 0 || !!filterFirstName || !!filterLastName || filterActive.length > 0;

  const reloadList = useCallback(
    async (
      p: number,
      opts?: {
        companyIds?: string[];
        firstName?: string | null;
        lastName?: string | null;
        active?: ActiveOption[];
      },
    ) => {
      setLoad("loading");
      const companyIds = opts?.companyIds ?? filterCompanyIds;
      const activeSel = opts?.active ?? filterActive;
      const r = await fetchTenantEmployees({
        page: p,
        size: PAGE_SIZE,
        companyIds: companyIds.length > 0 ? companyIds : undefined,
        firstName: (opts?.firstName ?? filterFirstName) || undefined,
        lastName: (opts?.lastName ?? filterLastName) || undefined,
        active: activeBooleanFor(activeSel),
      });
      if (!r.ok) {
        setLoad(r.status === 403 ? "forbidden" : "error");
        return;
      }
      setItems(r.items);
      setTotalElements(r.totalElements);
      setTotalPages(r.totalPages);
      setPage(p);
      setLoad("ready");
    },
    [filterCompanyIds, filterFirstName, filterLastName, filterActive],
  );

  const reloadStats = useCallback(
    async (companyIds: string[] = filterCompanyIds) => {
      const companyIdsArg = companyIds.length > 0 ? companyIds : undefined;
      const [act, inact] = await Promise.all([
        fetchTenantEmployees({ page: 0, size: 1, companyIds: companyIdsArg, active: true }),
        fetchTenantEmployees({ page: 0, size: 1, companyIds: companyIdsArg, active: false }),
      ]);
      if (act.ok) setActiveCount(act.totalElements);
      if (inact.ok) setInactiveCount(inact.totalElements);
    },
    [filterCompanyIds],
  );

  useEffect(() => {
    void (async () => {
      const [cr, dr, jr] = await Promise.all([
        fetchTenantCompanies({ size: 100 }),
        fetchTenantDepartments({ size: 200 }),
        fetchTenantJobs({ size: 200 }),
      ]);
      if (cr.ok) setCompanies(cr.items);
      if (dr.ok) setDepartments(dr.items);
      if (jr.ok) setJobs(jr.items);
    })();
    void reloadList(0);
    void reloadStats();
  }, [reloadList, reloadStats]);

  const deptName = useCallback((id: string) => departments.find((d) => d.id === id)?.name ?? "", [departments]);
  const jobTitle = useCallback((id: string) => jobs.find((j) => j.id === id)?.title ?? "", [jobs]);
  const companyName = useCallback((id: string) => companies.find((c) => c.id === id)?.name ?? id, [companies]);

  async function toggleActive(item: TenantEmployeeItem) {
    if (item.active) {
      setConfirm({ item });
      return;
    }
    setBusyId(item.id);
    try {
      await patchTenantEmployeeActive(item.id, true);
      showToast(`"${item.firstName} ${item.lastName}" set to active.`);
      await reloadList(page);
      await reloadStats();
    } catch {
      showToast("Could not update status. Please try again.", "error");
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDeactivate() {
    if (!confirm) return;
    const { item } = confirm;
    setConfirmBusy(true);
    try {
      await patchTenantEmployeeActive(item.id, false);
      showToast(`"${item.firstName} ${item.lastName}" set to inactive.`);
      await reloadList(page);
      await reloadStats();
    } catch {
      showToast("Could not update status. Please try again.", "error");
    } finally {
      setConfirmBusy(false);
      setConfirm(null);
    }
  }

  function requestDelete(item: TenantEmployeeItem) {
    setDeleteConfirm({ item });
  }

  async function confirmDelete() {
    if (!deleteConfirm) return;
    const { item } = deleteConfirm;
    setConfirmBusy(true);
    try {
      await deleteTenantEmployee(item.id);
      showToast(`"${item.firstName} ${item.lastName}" deleted.`);
      await reloadList(page);
      await reloadStats();
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t("employees.msg.deleteFailed");
      showToast(msg, "error");
    } finally {
      setConfirmBusy(false);
      setDeleteConfirm(null);
    }
  }

  function applyCompanyIds(next: string[] | null) {
    const value = next ?? [];
    setFilterCompanyIds(value);
    void reloadList(0, { companyIds: value });
    void reloadStats(value);
  }

  function applyFirstName(next: string | null) {
    setFilterFirstName(next);
    void reloadList(0, { firstName: next });
  }

  function applyLastName(next: string | null) {
    setFilterLastName(next);
    void reloadList(0, { lastName: next });
  }

  function applyActive(next: ActiveOption[] | null) {
    const value = next ?? [];
    setFilterActive(value);
    void reloadList(0, { active: value });
  }

  function clearAllFilters() {
    setFilterCompanyIds([]);
    setFilterFirstName(null);
    setFilterLastName(null);
    setFilterActive([]);
    void reloadList(0, { companyIds: [], firstName: null, lastName: null, active: [] });
    void reloadStats([]);
  }

  const pageWindow = useMemo(() => {
    const start = page * PAGE_SIZE;
    const end = start + items.length;
    return items.length ? `${start + 1}-${end}` : "0";
  }, [items.length, page]);

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.title")}</h1>
        <p className="text-sm text-muted">{t("employees.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">{"<- "}{t("nav.dashboard")}</Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6" data-testid="employees-page">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold text-foreground">{t("employees.title")}</h1>
          <p className="text-sm text-muted">Manage and view all employees in your organization.</p>
        </div>
        <div className="flex items-center gap-3 text-sm">
          <Link href="/app/departments" className="font-medium text-primary underline-offset-4 hover:underline">
            {"<- "}{t("departments.title")}
          </Link>
          {canManage ? (
            <Link href="/app/employees/new" className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90">
              + {t("employees.action.new")}
            </Link>
          ) : null}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard tone="primary" label="Total Employees" value={totalElements}
          icon={
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-5 w-5">
              <circle cx="9" cy="8" r="3.5" />
              <circle cx="16.5" cy="9" r="2.5" />
              <path d="M3.5 18c.5-3 3-4.5 5.5-4.5S14 15 14.5 18" />
              <path d="M14 16.5c.4-1.5 2-2.5 3.5-2.5S20.5 15 21 17" />
            </svg>
          }
        />
        <StatCard tone="success" label="Active" value={activeCount}
          icon={
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-5 w-5">
              <circle cx="12" cy="12" r="9" />
              <path d="m8 12 3 3 5-6" />
            </svg>
          }
        />
        <StatCard tone="destructive" label="Inactive" value={inactiveCount}
          icon={
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-5 w-5">
              <circle cx="12" cy="12" r="9" />
              <path d="m9 9 6 6m0-6-6 6" />
            </svg>
          }
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <FilterChip<string[]>
          label={t("employees.label.companyId")}
          value={filterCompanyIds.length > 0 ? filterCompanyIds : null}
          formatValue={(v) =>
            v.length === 1 ? companyName(v[0]) : `${v.length} selected`
          }
          onApply={(v) => applyCompanyIds(v)}
          renderInput={(draft, setDraft) => (
            <MultiSelectBody
              options={companies.map((c) => ({ value: c.id, label: c.name }))}
              selected={draft ?? []}
              onChange={(next) => setDraft(next)}
            />
          )}
        />

        <FilterChip
          label={t("employees.label.firstName")}
          value={filterFirstName}
          onApply={(v) => applyFirstName(v)}
          renderInput={(draft, setDraft, apply) => (
            <input
              autoFocus
              value={draft ?? ""}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); apply(); } }}
              placeholder="contains…"
              className="w-full rounded border border-border bg-background px-2 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none"
            />
          )}
        />

        <FilterChip
          label={t("employees.label.lastName")}
          value={filterLastName}
          onApply={(v) => applyLastName(v)}
          renderInput={(draft, setDraft, apply) => (
            <input
              autoFocus
              value={draft ?? ""}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); apply(); } }}
              placeholder="contains…"
              className="w-full rounded border border-border bg-background px-2 py-1.5 text-sm text-foreground focus:border-primary focus:outline-none"
            />
          )}
        />

        <FilterChip<ActiveOption[]>
          label="Status"
          value={filterActive.length > 0 ? filterActive : null}
          formatValue={(v) => {
            if (v.length === 1) return v[0] === "active" ? "Active" : "Inactive";
            return `${v.length} selected`;
          }}
          onApply={(v) => applyActive(v)}
          renderInput={(draft, setDraft) => (
            <MultiSelectBody<ActiveOption>
              options={[
                { value: "active", label: "Active" },
                { value: "inactive", label: "Inactive" },
              ]}
              selected={draft ?? []}
              onChange={(next) => setDraft(next)}
            />
          )}
        />

        {hasAnyFilter ? (
          <button
            type="button"
            onClick={clearAllFilters}
            className="text-xs font-medium text-muted underline-offset-4 hover:text-foreground hover:underline"
          >
            Clear filters
          </button>
        ) : null}

        <div className="ml-auto flex items-center gap-2 text-xs text-muted">
          <span>
            {pageWindow} / {totalElements}
          </span>
          <button onClick={() => void reloadList(0)} disabled={page === 0} aria-label="First page"
            className="rounded border border-border px-2 py-1 disabled:opacity-40 hover:bg-surface-alt">«</button>
          <button onClick={() => void reloadList(Math.max(0, page - 1))} disabled={page === 0} aria-label="Previous page"
            className="rounded border border-border px-2 py-1 disabled:opacity-40 hover:bg-surface-alt">‹</button>
          <button onClick={() => void reloadList(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1} aria-label="Next page"
            className="rounded border border-border px-2 py-1 disabled:opacity-40 hover:bg-surface-alt">›</button>
          <button onClick={() => void reloadList(Math.max(0, totalPages - 1))} disabled={page >= totalPages - 1} aria-label="Last page"
            className="rounded border border-border px-2 py-1 disabled:opacity-40 hover:bg-surface-alt">»</button>
        </div>
      </div>

      {load === "loading" ? <p className="text-sm text-muted">{t("employees.state.loading")}</p> : null}
      {load === "error" ? <p className="text-sm text-destructive">{t("employees.error.load")}</p> : null}

      {load === "ready" ? (
        items.length === 0 ? (
          <p className="text-sm text-muted">{t("employees.state.empty")}</p>
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => (
              <EmployeeCard
                key={item.id}
                item={item}
                companyName={companies.find((c) => c.id === item.companyId)?.name}
                deptName={deptName(item.departmentId ?? "")}
                jobTitle={jobTitle(item.jobId ?? "")}
                canManage={canManage}
                busy={busyId === item.id}
                onToggleActive={() => void toggleActive(item)}
                onDelete={() => requestDelete(item)}
              />
            ))}
          </div>
        )
      ) : null}

      <ConfirmDialog
        open={!!confirm}
        title="Deactivate employee?"
        description={confirm ? `This will deactivate "${confirm.item.firstName} ${confirm.item.lastName}" and hide them from all payroll operations.` : ""}
        confirmLabel="Deactivate"
        busy={confirmBusy}
        onConfirm={() => void confirmDeactivate()}
        onCancel={() => setConfirm(null)}
      />

      <ConfirmDialog
        open={!!deleteConfirm}
        title={deleteConfirm?.item.status === "DRAFT" ? t("employees.delete.titleDraft") : t("employees.delete.title")}
        description={
          deleteConfirm
            ? deleteConfirm.item.status === "DRAFT"
              ? t("employees.delete.descriptionDraft").replace("{name}", `${deleteConfirm.item.firstName} ${deleteConfirm.item.lastName}`)
              : t("employees.delete.description").replace("{name}", `${deleteConfirm.item.firstName} ${deleteConfirm.item.lastName}`)
            : ""
        }
        confirmLabel={t("employees.action.delete")}
        busy={confirmBusy}
        onConfirm={() => void confirmDelete()}
        onCancel={() => setDeleteConfirm(null)}
      />
    </div>
  );
}

type MultiSelectOption<T> = { value: T; label: string };

function MultiSelectBody<T extends string>({
  options,
  selected,
  onChange,
}: {
  options: MultiSelectOption<T>[];
  selected: T[];
  onChange: (next: T[]) => void;
}) {
  const allSelected = options.length > 0 && options.every((o) => selected.includes(o.value));

  function toggle(value: T) {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  }

  function selectAll() {
    onChange(options.map((o) => o.value));
  }

  function clearAll() {
    onChange([]);
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between text-xs">
        <button
          type="button"
          onClick={selectAll}
          disabled={allSelected}
          className="font-medium text-primary underline-offset-4 hover:underline disabled:opacity-40 disabled:no-underline"
        >
          Select all
        </button>
        <button
          type="button"
          onClick={clearAll}
          disabled={selected.length === 0}
          className="font-medium text-muted underline-offset-4 hover:text-foreground hover:underline disabled:opacity-40 disabled:no-underline"
        >
          Clear all
        </button>
      </div>
      <ul className="max-h-56 overflow-y-auto rounded border border-border bg-background">
        {options.length === 0 ? (
          <li className="px-3 py-2 text-xs text-muted">No options available</li>
        ) : (
          options.map((opt) => {
            const checked = selected.includes(opt.value);
            return (
              <li key={String(opt.value)}>
                <label className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-foreground hover:bg-surface-alt">
                  <input
                    type="checkbox"
                    className="h-4 w-4"
                    checked={checked}
                    onChange={() => toggle(opt.value)}
                  />
                  <span className="truncate">{opt.label}</span>
                </label>
              </li>
            );
          })
        )}
      </ul>
    </div>
  );
}

type StatTone = "primary" | "success" | "destructive";

function StatCard({ tone, label, value, icon }: { tone: StatTone; label: string; value: number; icon: React.ReactNode }) {
  const toneClasses: Record<StatTone, { value: string; iconBg: string; iconColor: string }> = {
    primary: { value: "text-primary", iconBg: "bg-primary/10", iconColor: "text-primary" },
    success: { value: "text-emerald-600", iconBg: "bg-emerald-100", iconColor: "text-emerald-600" },
    destructive: { value: "text-destructive", iconBg: "bg-destructive/10", iconColor: "text-destructive" },
  };
  const c = toneClasses[tone];
  return (
    <div className="flex items-center justify-between rounded-lg border border-border bg-surface px-5 py-4">
      <div>
        <div className={`text-3xl font-semibold leading-tight ${c.value}`}>{value}</div>
        <div className="text-xs text-muted">{label}</div>
      </div>
      <div className={`flex h-10 w-10 items-center justify-center rounded-full ${c.iconBg} ${c.iconColor}`}>
        {icon}
      </div>
    </div>
  );
}

function EmployeeCard({
  item,
  companyName,
  deptName,
  jobTitle,
  canManage,
  busy,
  onToggleActive,
  onDelete,
}: {
  item: TenantEmployeeItem;
  companyName?: string;
  deptName: string;
  jobTitle: string;
  canManage: boolean;
  busy: boolean;
  onToggleActive: () => void;
  onDelete: () => void;
}) {
  const initials = initialsFor(item.firstName, item.lastName);
  const gradient = gradientFor(item.id);
  const isDraft = item.status === "DRAFT";
  const profileHref = isDraft
    ? `/app/employees/new?draft=${encodeURIComponent(item.id)}`
    : `/app/employees/${item.id}/edit`;

  const borderColor = isDraft ? "#d97706" : item.active ? "#16a34a" : "var(--color-destructive, #dc2626)";
  const bg = isDraft ? "rgba(217, 119, 6, 0.06)" : item.active ? undefined : "rgba(239, 68, 68, 0.04)";
  const nameColor = isDraft ? undefined : item.active ? undefined : "var(--color-destructive, #dc2626)";

  return (
    <div
      className="group relative h-full rounded-lg border border-border bg-surface p-4 transition-shadow hover:shadow-sm"
      style={{ borderLeft: `5px solid ${borderColor}`, background: bg }}
    >
      <div className="flex items-start gap-3">
        <div
          aria-hidden="true"
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-slate-700"
          style={{ background: gradient }}
        >
          {initials}
        </div>
        <div className="min-w-0 flex-1">
          <Link
            href={profileHref}
            className="block truncate text-base font-semibold leading-tight hover:underline"
            style={{ color: nameColor }}
            title={`${item.firstName} ${item.lastName}`}
          >
            {item.firstName} {item.lastName}
          </Link>
          {isDraft ? (
            <p className="mt-0.5 text-xs font-medium text-amber-600 dark:text-amber-400">Draft — finish onboarding</p>
          ) : null}
          {jobTitle ? <p className="truncate text-xs text-muted">{jobTitle}</p> : null}
          <div className="mt-1 flex items-center gap-1.5">
            <span aria-hidden="true" className="inline-block h-1.5 w-1.5 rounded-full bg-muted" />
            <span className="truncate text-xs text-muted">{deptName || companyName || "—"}</span>
          </div>
          {item.email ? <p className="mt-1 truncate text-xs text-muted">{item.email}</p> : null}
        </div>
      </div>
      {canManage ? (
        <div className="mt-3 flex items-center justify-end gap-3 text-xs">
          <Link href={profileHref} className="font-medium text-primary underline-offset-4 hover:underline">
            {isDraft ? "Continue setup" : "Edit"}
          </Link>
          {!isDraft ? (
            <button
              type="button"
              onClick={onToggleActive}
              disabled={busy}
              className="font-medium text-muted underline-offset-4 hover:text-foreground hover:underline disabled:opacity-50"
            >
              {item.active ? "Deactivate" : "Activate"}
            </button>
          ) : null}
          <button
            type="button"
            onClick={onDelete}
            disabled={busy}
            className="font-medium text-destructive underline-offset-4 hover:underline disabled:opacity-50"
          >
            Delete
          </button>
        </div>
      ) : null}
    </div>
  );
}
