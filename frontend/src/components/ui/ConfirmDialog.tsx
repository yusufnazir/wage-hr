"use client";

type ConfirmDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onCancel(); }}
    >
      <div className="w-full max-w-sm rounded-lg border border-border bg-surface p-6 shadow-xl">
        <h2 id="confirm-dialog-title" className="mb-2 text-base font-semibold text-foreground">
          {title}
        </h2>
        <p className="mb-6 text-sm text-muted">{description}</p>
        <div className="flex justify-end gap-3">
          <button
            onClick={onCancel}
            disabled={busy}
            className="rounded border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-surface-alt disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            disabled={busy}
            className="rounded bg-destructive px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {busy ? "…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
