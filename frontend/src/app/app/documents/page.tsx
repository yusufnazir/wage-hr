"use client";

import { useCallback, useEffect, useState } from "react";
import {
  completeDocumentUpload,
  createDocumentAttachment,
  createDocumentShare,
  createDocumentUploadSession,
  deleteDocumentAttachment,
  deleteDocumentShare,
  fetchDocumentAttachments,
  fetchDocumentDownloadUrl,
  fetchDocumentShares,
  fetchTenantDocumentsHub,
  putToDocumentUploadUrl,
  softDeleteTenantDocument,
  type DocumentAttachmentListItem,
  type DocumentHubItem,
  type DocumentShareListItem,
} from "@/lib/api";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { SetHtmlLang } from "@/components/i18n/SetHtmlLang";
import { formatUserFacingDate } from "@/lib/user-date-format";

type PageState =
  | { kind: "loading" }
  | { kind: "forbidden" }
  | { kind: "error"; message: string }
  | { kind: "ready"; items: DocumentHubItem[]; canUpload: boolean };

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  return `${(bytes / 1024).toFixed(1)} KiB`;
}

function DocumentHubRow(props: {
  doc: DocumentHubItem;
  dateFormat: string;
  canUpload: boolean;
  busy: boolean;
  setBusy: (v: boolean) => void;
  setNotice: (v: string | null) => void;
  onDownload: (d: DocumentHubItem) => void;
  reloadHub: () => Promise<void>;
}) {
  const { doc, dateFormat, canUpload, busy, setBusy, setNotice, onDownload, reloadHub } = props;
  const canManage = canUpload && doc.hubSource === "OWNED";
  const [shares, setShares] = useState<DocumentShareListItem[]>([]);
  const [attachments, setAttachments] = useState<DocumentAttachmentListItem[]>([]);
  const [detailLoaded, setDetailLoaded] = useState(false);
  const [shareUserId, setShareUserId] = useState("");
  const [attachType, setAttachType] = useState("PAYROLL_RUN");
  const [attachEntityId, setAttachEntityId] = useState("");

  async function loadDetail() {
    setBusy(true);
    setNotice(null);
    try {
      const [s, a] = await Promise.all([fetchDocumentShares(doc.id), fetchDocumentAttachments(doc.id)]);
      if (s.ok) {
        setShares(s.items);
      } else {
        setNotice(`Could not load shares (HTTP ${s.status}).`);
      }
      if (a.ok) {
        setAttachments(a.items);
      } else {
        setNotice(`Could not load attachments (HTTP ${a.status}).`);
      }
      setDetailLoaded(true);
    } finally {
      setBusy(false);
    }
  }

  async function onAddShare() {
    const trimmed = shareUserId.trim();
    if (!trimmed) {
      setNotice("Enter a grantee user UUID.");
      return;
    }
    setBusy(true);
    setNotice(null);
    const res = await createDocumentShare(doc.id, { granteeUserId: trimmed, granteeRoleId: null });
    setBusy(false);
    if (!res.ok) {
      setNotice(`Share failed (HTTP ${res.status}).`);
      return;
    }
    setShareUserId("");
    await loadDetail();
  }

  async function onRemoveShare(shareId: string) {
    setBusy(true);
    setNotice(null);
    const res = await deleteDocumentShare(doc.id, shareId);
    setBusy(false);
    if (!res.ok) {
      setNotice(`Remove share failed (HTTP ${res.status}).`);
      return;
    }
    await loadDetail();
  }

  async function onAddAttachment() {
    const id = attachEntityId.trim();
    if (!id) {
      setNotice("Enter entity UUID.");
      return;
    }
    setBusy(true);
    setNotice(null);
    const res = await createDocumentAttachment(doc.id, { entityType: attachType.trim(), entityId: id });
    setBusy(false);
    if (!res.ok) {
      setNotice(`Attachment failed (HTTP ${res.status}).`);
      return;
    }
    setAttachEntityId("");
    await loadDetail();
  }

  async function onRemoveAttachment(attachmentId: string) {
    setBusy(true);
    setNotice(null);
    const res = await deleteDocumentAttachment(doc.id, attachmentId);
    setBusy(false);
    if (!res.ok) {
      setNotice(`Remove attachment failed (HTTP ${res.status}).`);
      return;
    }
    await loadDetail();
  }

  async function onRemoveDocument() {
    if (!window.confirm(`Remove “${doc.originalFilename}” from your hub? The file may remain in storage until operators run cleanup.`)) {
      return;
    }
    setBusy(true);
    setNotice(null);
    const res = await softDeleteTenantDocument(doc.id);
    setBusy(false);
    if (!res.ok) {
      setNotice(`Delete failed (HTTP ${res.status}).`);
      return;
    }
    await reloadHub();
  }

  return (
    <li className="flex flex-col gap-2 rounded-md border border-border/80 bg-background/60 px-3 py-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm font-medium text-foreground">{doc.originalFilename}</p>
          <p className="text-xs text-muted">
            {doc.hubSource} · {formatUserFacingDate(doc.createdAt, dateFormat)} · {formatSize(doc.sizeBytes)} ·{" "}
            {doc.contentType}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => void onDownload(doc)}
            className="inline-flex w-fit shrink-0 items-center justify-center rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium text-foreground hover:opacity-90 disabled:opacity-50"
          >
            Download
          </button>
          {canManage ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => void onRemoveDocument()}
              className="inline-flex w-fit shrink-0 items-center justify-center rounded-md border border-destructive/50 bg-background px-3 py-1.5 text-xs font-medium text-destructive hover:opacity-90 disabled:opacity-50"
            >
              Remove
            </button>
          ) : null}
        </div>
      </div>
      {canManage ? (
        <details
          className="mt-1 border-t border-border/60 pt-2"
          onToggle={(e) => {
            const el = e.currentTarget;
            if (el.open && !detailLoaded) {
              void loadDetail();
            }
          }}
        >
          <summary className="cursor-pointer text-xs font-medium text-primary">Sharing &amp; record links</summary>
          <div className="mt-3 space-y-4 text-xs">
            <div>
              <p className="font-medium text-foreground">Shares (by user id)</p>
              {shares.length === 0 ? <p className="text-muted">None yet.</p> : null}
              <ul className="mt-1 space-y-1">
                {shares.map((s) => (
                  <li key={s.id} className="flex flex-wrap items-center gap-2 font-mono text-muted">
                    <span>
                      {s.granteeUserId ? `user ${s.granteeUserId}` : s.granteeRoleId ? `role ${s.granteeRoleId}` : "—"}
                    </span>
                    <button
                      type="button"
                      disabled={busy}
                      className="text-destructive underline"
                      onClick={() => void onRemoveShare(s.id)}
                    >
                      remove
                    </button>
                  </li>
                ))}
              </ul>
              <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                <input
                  className="w-full rounded-md border border-border bg-background px-2 py-1 font-mono text-xs sm:max-w-xs"
                  placeholder="Grantee user UUID"
                  value={shareUserId}
                  onChange={(e) => setShareUserId(e.target.value)}
                />
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void onAddShare()}
                  className="rounded-md border border-border bg-background px-2 py-1 font-medium"
                >
                  Add share
                </button>
              </div>
            </div>
            <div>
              <p className="font-medium text-foreground">Attachments</p>
              {attachments.length === 0 ? <p className="text-muted">None yet.</p> : null}
              <ul className="mt-1 space-y-1">
                {attachments.map((a) => (
                  <li key={a.id} className="flex flex-wrap items-center gap-2 font-mono text-muted">
                    <span>
                      {a.entityType} · {a.entityId}
                    </span>
                    <button
                      type="button"
                      disabled={busy}
                      className="text-destructive underline"
                      onClick={() => void onRemoveAttachment(a.id)}
                    >
                      remove
                    </button>
                  </li>
                ))}
              </ul>
              <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
                <input
                  className="w-full rounded-md border border-border bg-background px-2 py-1 font-mono text-xs sm:w-40"
                  placeholder="ENTITY_TYPE"
                  value={attachType}
                  onChange={(e) => setAttachType(e.target.value)}
                />
                <input
                  className="w-full rounded-md border border-border bg-background px-2 py-1 font-mono text-xs sm:max-w-xs"
                  placeholder="Entity UUID"
                  value={attachEntityId}
                  onChange={(e) => setAttachEntityId(e.target.value)}
                />
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void onAddAttachment()}
                  className="rounded-md border border-border bg-background px-2 py-1 font-medium"
                >
                  Add link
                </button>
              </div>
            </div>
          </div>
        </details>
      ) : null}
    </li>
  );
}

export default function TenantDocumentsPage() {
  const { me } = useTenantAppSession();
  const [state, setState] = useState<PageState>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const hub = await fetchTenantDocumentsHub();
    if (!hub.ok) {
      if (hub.status === 403) {
        setState({ kind: "forbidden" });
        return;
      }
      setState({ kind: "error", message: `Could not load documents (HTTP ${hub.status}).` });
      return;
    }
    const canUpload = me.privileges.includes("DOCUMENT_EDIT");
    setState({ kind: "ready", items: hub.items, canUpload });
  }, [me]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const hub = await fetchTenantDocumentsHub();
      if (cancelled) return;
      if (!hub.ok) {
        if (hub.status === 403) {
          setState({ kind: "forbidden" });
          return;
        }
        setState({ kind: "error", message: `Could not load documents (HTTP ${hub.status}).` });
        return;
      }
      const canUpload = me.privileges.includes("DOCUMENT_EDIT");
      setState({ kind: "ready", items: hub.items, canUpload });
    })();
    return () => {
      cancelled = true;
    };
  }, [me]);

  async function onDownload(doc: DocumentHubItem) {
    setNotice(null);
    const res = await fetchDocumentDownloadUrl(doc.id);
    if (!res.ok) {
      setNotice(`Download link failed (HTTP ${res.status}).`);
      return;
    }
    window.open(res.downloadUrl, "_blank", "noopener,noreferrer");
  }

  async function onPickFile(file: File | null) {
    if (!file || state.kind !== "ready") {
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      const sessionRes = await createDocumentUploadSession({
        originalFilename: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      });
      if (!sessionRes.ok) {
        if (sessionRes.status === 503) {
          setNotice("Storage is not configured on the API (MinIO env). Upload is disabled until operators set MINIO_*.");
        } else {
          setNotice(`Upload session failed (HTTP ${sessionRes.status}).`);
        }
        return;
      }
      const s = sessionRes.session;
      await putToDocumentUploadUrl(s.uploadUrl, file, s.requiredHeaders);
      const done = await completeDocumentUpload({
        documentId: s.documentId,
        storageKey: s.storageKey,
        originalFilename: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      });
      if (!done.ok) {
        setNotice(`Complete upload failed (HTTP ${done.status}).`);
        return;
      }
      setNotice("Upload complete.");
      await reload();
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Upload failed";
      setNotice(
        `${msg} — if the browser blocked the PUT, configure MinIO CORS for this app origin (see docs/modules/documents-minio.md).`,
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6" data-testid="tenant-documents-page">
      <div>
        <h1 className="text-lg font-semibold tracking-tight text-foreground">Documents</h1>
        <p className="mt-1 text-sm text-muted">Hub, upload, and shares for this tenant (DOCUMENT_VIEW / DOCUMENT_EDIT).</p>
      </div>
      {state.kind === "loading" ? <p className="text-sm text-muted">Loading…</p> : null}
      {state.kind === "forbidden" ? (
        <p className="text-sm text-muted">You do not have DOCUMENT_VIEW in this tenant.</p>
      ) : null}
      {state.kind === "error" ? <p className="text-sm text-muted">{state.message}</p> : null}
      {state.kind === "ready" ? (
        <div className="flex flex-col gap-6">
          <SetHtmlLang locale={me.locale} />
            {state.canUpload ? (
              <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
                <h2 className="text-sm font-medium text-foreground">Upload</h2>
                <p className="mt-2 text-xs text-muted">
                  Uses presigned PUT to MinIO/S3. The API must have MinIO configured; the bucket must allow CORS from this
                  web origin for browser uploads.
                </p>
                <label className="mt-3 flex cursor-pointer flex-col gap-2 text-sm">
                  <span className="text-muted">Choose file</span>
                  <input
                    type="file"
                    disabled={busy}
                    className="text-sm text-foreground file:mr-3 file:rounded-md file:border file:border-border file:bg-background file:px-3 file:py-1.5"
                    onChange={(e) => void onPickFile(e.target.files?.[0] ?? null)}
                  />
                </label>
              </section>
            ) : (
              <p className="text-xs text-muted">Upload requires DOCUMENT_EDIT in this tenant.</p>
            )}
            {notice ? (
              <p className="text-xs text-foreground" data-testid="documents-notice">
                {notice}
              </p>
            ) : null}
            <section className="rounded-md border border-border bg-surface p-6 shadow-sm">
              <h2 className="text-sm font-medium text-foreground">Your hub</h2>
              <p className="mt-1 text-xs text-muted">Owned and shared-with-you (DOCUMENT_VIEW).</p>
              {state.items.length === 0 ? (
                <p className="mt-3 text-sm text-muted">No documents yet.</p>
              ) : (
                <ul className="mt-4 space-y-3">
                  {state.items.map((d) => (
                    <DocumentHubRow
                      key={d.id}
                      doc={d}
                      dateFormat={me.dateFormat}
                      canUpload={state.canUpload}
                      busy={busy}
                      setBusy={setBusy}
                      setNotice={setNotice}
                      onDownload={onDownload}
                      reloadHub={reload}
                    />
                  ))}
                </ul>
              )}
            </section>
          </div>
        ) : null}
    </div>
  );
}
