"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { showToast } from "@/components/ui/Toast";
import {
  completeDocumentUpload,
  createDocumentAttachment,
  createDocumentUploadSession,
  fetchDocumentDownloadUrl,
  fetchTenantDocumentsByEntity,
  putToDocumentUploadUrl,
  softDeleteTenantDocument,
  type DocumentHubItem,
} from "@/lib/api";

type UploadState = "idle" | "uploading" | "error";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

type Props = {
  entityType: string;
  entityId: string;
  canEdit: boolean;
};

export function EntityDocumentsTab({ entityType, entityId, canEdit }: Props) {
  const [docs, setDocs] = useState<DocumentHubItem[]>([]);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "error">("loading");
  const [uploadState, setUploadState] = useState<UploadState>("idle");
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const reload = useCallback(async () => {
    setLoadState("loading");
    const r = await fetchTenantDocumentsByEntity(entityType, entityId);
    if (!r.ok) {
      setLoadState("error");
      return;
    }
    setDocs(r.items);
    setLoadState("ready");
  }, [entityType, entityId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    // Reset input so the same file can be re-selected if needed
    e.target.value = "";

    setUploadState("uploading");
    setUploadError(null);

    try {
      // 1. Create upload session
      const sessionResult = await createDocumentUploadSession({
        originalFilename: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      });
      if (!sessionResult.ok) {
        const msg =
          sessionResult.status === 503
            ? "Document storage is not configured on this server."
            : "Could not start upload. Please try again.";
        setUploadError(msg);
        setUploadState("error");
        return;
      }
      const { session } = sessionResult;

      // 2. PUT bytes directly to presigned URL
      await putToDocumentUploadUrl(session.uploadUrl, file, session.requiredHeaders);

      // 3. Complete — persists the document record
      const completeResult = await completeDocumentUpload({
        documentId: session.documentId,
        storageKey: session.storageKey,
        originalFilename: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      });
      if (!completeResult.ok) {
        setUploadError("Upload failed during finalisation. Please try again.");
        setUploadState("error");
        return;
      }

      // 4. Attach to entity
      const attachResult = await createDocumentAttachment(session.documentId, {
        entityType,
        entityId,
      });
      if (!attachResult.ok) {
        // Document exists but attach failed — show warning, still reload
        showToast("File uploaded but could not link it here. Check Documents hub.", "error");
      } else {
        showToast(`"${file.name}" uploaded successfully.`);
      }

      setUploadState("idle");
      await reload();
    } catch {
      setUploadError("An unexpected error occurred. Please try again.");
      setUploadState("error");
    }
  }

  async function handleDownload(doc: DocumentHubItem) {
    const r = await fetchDocumentDownloadUrl(doc.id);
    if (!r.ok) {
      showToast("Could not generate download link. Please try again.", "error");
      return;
    }
    window.open(r.downloadUrl, "_blank", "noopener");
  }

  async function handleDelete(doc: DocumentHubItem) {
    setDeletingId(doc.id);
    const r = await softDeleteTenantDocument(doc.id);
    if (!r.ok) {
      showToast("Could not remove document. Please try again.", "error");
    } else {
      showToast(`"${doc.originalFilename}" removed.`);
      await reload();
    }
    setDeletingId(null);
  }

  return (
    <div className="space-y-4">
      {/* Upload button */}
      {canEdit ? (
        <div className="flex items-center gap-3">
          <button
            type="button"
            disabled={uploadState === "uploading"}
            onClick={() => fileInputRef.current?.click()}
            className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {uploadState === "uploading" ? "Uploading…" : "Upload document"}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={(e) => void handleFileChange(e)}
          />
          {uploadError ? (
            <p className="text-sm text-red-500">{uploadError}</p>
          ) : null}
        </div>
      ) : null}

      {/* Document list */}
      {loadState === "loading" ? (
        <p className="text-sm text-muted">Loading documents…</p>
      ) : loadState === "error" ? (
        <p className="text-sm text-red-500">Could not load documents.</p>
      ) : docs.length === 0 ? (
        <p className="text-sm text-muted">No documents attached yet.</p>
      ) : (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-surface-alt">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-muted">File</th>
                <th className="px-4 py-2 text-left font-medium text-muted">Size</th>
                <th className="px-4 py-2 text-left font-medium text-muted">Uploaded</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {docs.map((doc) => (
                <tr key={doc.id}>
                  <td className="px-4 py-2 font-medium text-foreground">{doc.originalFilename}</td>
                  <td className="px-4 py-2 text-muted">{formatBytes(doc.sizeBytes)}</td>
                  <td className="px-4 py-2 text-muted">{formatDate(doc.createdAt)}</td>
                  <td className="px-4 py-2 text-right">
                    <button
                      type="button"
                      onClick={() => void handleDownload(doc)}
                      className="mr-3 text-sm text-primary underline-offset-4 hover:underline"
                    >
                      Download
                    </button>
                    {canEdit ? (
                      <button
                        type="button"
                        disabled={deletingId === doc.id}
                        onClick={() => void handleDelete(doc)}
                        className="text-sm text-muted underline-offset-4 hover:underline disabled:opacity-50"
                      >
                        Remove
                      </button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
