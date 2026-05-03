"use client";

import { useEffect, useState } from "react";

export type ToastVariant = "success" | "error";

export type ToastMessage = {
  id: number;
  message: string;
  variant: ToastVariant;
};

let _nextId = 1;
let _dispatch: ((msg: ToastMessage) => void) | null = null;

export function showToast(message: string, variant: ToastVariant = "success") {
  _dispatch?.({ id: _nextId++, message, variant });
}

export function ToastContainer() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  useEffect(() => {
    _dispatch = (msg) => {
      setToasts((prev) => [...prev, msg]);
      if (msg.variant === "success") {
        setTimeout(() => {
          setToasts((prev) => prev.filter((t) => t.id !== msg.id));
        }, 4000);
      }
    };
    return () => {
      _dispatch = null;
    };
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={[
            "flex items-start gap-3 rounded-md px-4 py-3 text-sm shadow-xl",
            toast.variant === "success"
              ? "bg-green-600 text-white"
              : "bg-red-600 text-white",
          ].join(" ")}
        >
          <span className="flex-1">{toast.message}</span>
          <button
            onClick={() => setToasts((prev) => prev.filter((t) => t.id !== toast.id))}
            className="ml-2 shrink-0 opacity-70 hover:opacity-100 text-white"
            aria-label="Dismiss"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}
