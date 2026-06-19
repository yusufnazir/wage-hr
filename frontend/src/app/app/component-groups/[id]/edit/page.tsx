"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  formCardClass,
  formCheckboxRowClass,
  formFieldClass,
  formInputClass,
  formLabelClass,
  formPrimaryButtonClass,
} from "@/components/ui/formStyles";
import { showToast } from "@/components/ui/Toast";
import {
  deleteTenantComponentGroup,
  fetchTenantComponentGroup,
  putTenantComponentGroup,
  type TenantComponentGroupRow,
} from "@/lib/api";

export default function TenantComponentGroupEditPage() {
  const { me } = useTenantAppSession();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const sp = useSearchParams();
  const id = params.id;
  const companyId = (sp.get("companyId") ?? "").trim();

  const [group, setGroup] = useState<TenantComponentGroupRow | null>(null);
  const [load, setLoad] = useState<"loading" | "ready" | "error">("loading");
  const [sortOrder, setSortOrder] = useState(0);
  const [active, setActive] = useState(true);
  const [enName, setEnName] = useState("");
  const [nlName, setNlName] = useState("");
  const [busy, setBusy] = useState(false);

  const canManage = me.privileges.includes("WAGE_COMPONENT_MANAGE");

  useEffect(() => {
    if (!companyId || !id) {
      setLoad("error");
      return;
    }
    void (async () => {
      const r = await fetchTenantComponentGroup(companyId, id, { locale: me.locale });
      if (!r.ok) {
        setLoad("error");
        return;
      }
      const g = r.group;
      setGroup(g);
      setSortOrder(g.sortOrder);
      setActive(g.active);
      const en = g.translations.find((x) => x.locale.toLowerCase() === "en");
      const nl = g.translations.find((x) => x.locale.toLowerCase() === "nl");
      setEnName(en?.name ?? "");
      setNlName(nl?.name ?? "");
      setLoad("ready");
    })();
  }, [companyId, id, me.locale]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!companyId || !id) return;
    setBusy(true);
    try {
      const g = await putTenantComponentGroup(
        companyId,
        id,
        {
          sortOrder,
          active,
          translations: [
            { locale: "en", name: enName.trim(), description: null },
            { locale: "nl", name: nlName.trim(), description: null },
          ],
        },
        { locale: me.locale },
      );
      setGroup(g);
      showToast("Saved.");
    } catch {
      showToast("Save failed.", "error");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!companyId || !id) return;
    if (!window.confirm("Delete this component group and all headers/items?")) return;
    setBusy(true);
    try {
      await deleteTenantComponentGroup(companyId, id);
      showToast("Deleted.");
      router.push("/app/component-groups");
    } catch {
      showToast("Delete failed.", "error");
    } finally {
      setBusy(false);
    }
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-xl px-4 py-6">
        <p className="text-sm text-muted">Loading…</p>
      </div>
    );
  }

  if (load === "error" || !group) {
    return (
      <div className="mx-auto max-w-xl space-y-4 px-4 py-6">
        <p className="text-sm text-destructive">Could not load group.</p>
        <Link href="/app/component-groups" className="text-sm text-primary underline-offset-4 hover:underline">
          Back to list
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{group.name}</h1>
        <Link href="/app/component-groups" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          Back to list
        </Link>
      </div>
      <p className="text-xs text-muted">
        Country {group.countryCode}
        {group.platformComponentGroupTemplateId ? ` · template ${group.platformComponentGroupTemplateId}` : ""}
      </p>

      {canManage ? (
        <form onSubmit={(e) => void onSave(e)} className={formCardClass}>
          <div className={formFieldClass}>
            <label className={formLabelClass} htmlFor="tcg-en-name">
              EN name
            </label>
            <input
              id="tcg-en-name"
              className={formInputClass}
              value={enName}
              onChange={(e) => setEnName(e.target.value)}
            />
          </div>
          <div className={formFieldClass}>
            <label className={formLabelClass} htmlFor="tcg-nl-name">
              NL name
            </label>
            <input
              id="tcg-nl-name"
              className={formInputClass}
              value={nlName}
              onChange={(e) => setNlName(e.target.value)}
            />
          </div>
          <div className={formFieldClass}>
            <label className={formLabelClass} htmlFor="tcg-sort">
              Sort order
            </label>
            <input
              id="tcg-sort"
              type="number"
              className={formInputClass}
              value={sortOrder}
              onChange={(e) => setSortOrder(Number.parseInt(e.target.value, 10) || 0)}
            />
          </div>
          <div className={formFieldClass}>
            <label className={formCheckboxRowClass}>
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Active
            </label>
          </div>
          <button type="submit" disabled={busy} className={formPrimaryButtonClass}>
            {busy ? "Saving…" : "Save"}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => void onDelete()}
            className="inline-flex w-full items-center justify-center rounded-md border border-destructive bg-background px-4 py-2 text-sm font-medium text-destructive shadow-sm hover:bg-destructive/5 disabled:opacity-50"
          >
            Delete
          </button>
        </form>
      ) : (
        <p className="text-sm text-muted">View only — you need wage component manage to edit.</p>
      )}
    </div>
  );
}
