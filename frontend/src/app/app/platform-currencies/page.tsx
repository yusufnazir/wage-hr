"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import {
  createPlatformCurrency,
  fetchPlatformCurrencies,
  patchPlatformCurrency,
  type PlatformCurrencyRow,
} from "@/lib/api";
import { navLabel } from "@/messages/nav";

type LoadState = "loading" | "ready" | "forbidden" | "error";

export default function PlatformCurrenciesPage() {
  const { me } = useTenantAppSession();
  const t = useCallback((key: string) => navLabel(me.locale, key), [me.locale]);

  const [load, setLoad] = useState<LoadState>("loading");
  const [items, setItems] = useState<PlatformCurrencyRow[]>([]);
  const [msg, setMsg] = useState<string | null>(null);

  // create form
  const [code, setCode] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [sortOrder, setSortOrder] = useState("100");
  const [active, setActive] = useState(true);
  const [busy, setBusy] = useState(false);

  // edit form
  const [editId, setEditId] = useState<string | null>(null);
  const [editDisplayName, setEditDisplayName] = useState("");
  const [editSortOrder, setEditSortOrder] = useState("");
  const [editActive, setEditActive] = useState(true);

  const reload = useCallback(async () => {
    setLoad("loading");
    setMsg(null);
    const r = await fetchPlatformCurrencies();
    if (!r.ok) {
      setLoad(r.status === 403 ? "forbidden" : "error");
      return;
    }
    setItems(r.items);
    setLoad("ready");
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function onCreate(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      await createPlatformCurrency({
        code: code.trim().toUpperCase(),
        displayName: displayName.trim(),
        sortOrder: parseInt(sortOrder, 10) || 100,
        active,
      });
      setCode("");
      setDisplayName("");
      setSortOrder("100");
      setActive(true);
      await reload();
    } catch {
      setMsg(t("platformCurrencies.msg.createFailed"));
    } finally {
      setBusy(false);
    }
  }

  function openEdit(row: PlatformCurrencyRow) {
    setEditId(row.id);
    setEditDisplayName(row.displayName);
    setEditSortOrder(String(row.sortOrder));
    setEditActive(row.active);
    setMsg(null);
  }

  async function onSaveEdit(e: React.FormEvent) {
    e.preventDefault();
    if (!editId) return;
    setBusy(true);
    setMsg(null);
    try {
      await patchPlatformCurrency(editId, {
        displayName: editDisplayName.trim(),
        sortOrder: parseInt(editSortOrder, 10),
        active: editActive,
      });
      setEditId(null);
      setMsg(t("platformCurrencies.msg.saved"));
      await reload();
    } catch {
      setMsg(t("platformCurrencies.msg.saveFailed"));
    } finally {
      setBusy(false);
    }
  }

  if (!me.platformSuperadmin) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.notOperator")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "forbidden") {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <p className="text-sm text-muted">{t("platformCurrencies.error.forbidden")}</p>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>
    );
  }

  if (load === "loading") {
    return (
      <div className="mx-auto max-w-2xl">
        <p className="text-sm text-muted">{t("platformCurrencies.state.loading")}</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-semibold text-foreground">{t("platformCurrencies.title")}</h1>
        <Link href="/app" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          ← {t("nav.dashboard")}
        </Link>
      </div>

      <p className="text-sm text-muted">{t("platformCurrencies.helper.intro")}</p>

      {msg ? <p className="text-sm font-medium text-primary">{msg}</p> : null}

      {load === "error" ? (
        <p className="text-sm text-muted">{t("platformCurrencies.error.load")}</p>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
              <tr>
                <th className="px-3 py-2">{t("platformCurrencies.col.code")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.name")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.sortOrder")}</th>
                <th className="px-3 py-2">{t("platformCurrencies.col.active")}</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((row) =>
                editId === row.id ? (
                  <tr key={row.id} className="border-t border-border bg-surface/60">
                    <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                    <td className="px-3 py-2">
                      <input
                        className="w-full rounded border border-border bg-background px-2 py-1 text-sm text-foreground"
                        value={editDisplayName}
                        onChange={(e) => setEditDisplayName(e.target.value)}
                        maxLength={128}
                      />
                    </td>
                    <td className="px-3 py-2">
                      <input
                        type="number"
                        className="w-20 rounded border border-border bg-background px-2 py-1 text-sm text-foreground"
                        value={editSortOrder}
                        onChange={(e) => setEditSortOrder(e.target.value)}
                        min={0}
                        max={10000}
                      />
                    </td>
                    <td className="px-3 py-2">
                      <input type="checkbox" checked={editActive} onChange={(e) => setEditActive(e.target.checked)} />
                    </td>
                    <td className="px-3 py-2">
                      <form onSubmit={(e) => void onSaveEdit(e)} className="flex gap-2">
                        <button
                          type="submit"
                          disabled={busy}
                          className="text-sm font-medium text-primary underline-offset-4 hover:underline disabled:opacity-50"
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditId(null)}
                          className="text-sm font-medium text-muted underline-offset-4 hover:underline"
                        >
                          Cancel
                        </button>
                      </form>
                    </td>
                  </tr>
                ) : (
                  <tr key={row.id} className="border-t border-border">
                    <td className="px-3 py-2 font-mono font-semibold text-foreground">{row.code}</td>
                    <td className="px-3 py-2 text-foreground">{row.displayName}</td>
                    <td className="px-3 py-2 text-muted">{row.sortOrder}</td>
                    <td className="px-3 py-2 text-muted">{row.active ? "✓" : "—"}</td>
                    <td className="px-3 py-2">
                      <button
                        type="button"
                        onClick={() => openEdit(row)}
                        className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                      >
                        Edit
                      </button>
                    </td>
                  </tr>
                ),
              )}
            </tbody>
          </table>
        </div>
      )}

      <section className="rounded-md border border-border bg-surface p-5 shadow-sm">
        <h2 className="mb-3 text-sm font-semibold text-foreground">{t("platformCurrencies.section.create")}</h2>
        <form onSubmit={(e) => void onCreate(e)} className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.code")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground uppercase placeholder:normal-case"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              maxLength={3}
              required
              pattern="[A-Z]{3}"
              placeholder="e.g. USD"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.name")}</label>
            <input
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              maxLength={128}
              required
              placeholder="e.g. US Dollar"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCurrencies.label.sortOrder")}</label>
            <input
              type="number"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm text-foreground"
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value)}
              min={0}
              max={10000}
            />
          </div>
          <div className="flex items-end space-x-2 pb-1">
            <input
              id="currency-active"
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
            />
            <label htmlFor="currency-active" className="text-sm text-foreground">
              {t("platformCurrencies.label.active")}
            </label>
          </div>
          <div className="sm:col-span-2">
            <button
              type="submit"
              disabled={busy}
              className="rounded bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-50"
            >
              {t("platformCurrencies.action.create")}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
