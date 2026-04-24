"use client";

import { useEffect } from "react";

/** Syncs {@code document.documentElement.lang} with the resolved UI locale. */
export function SetHtmlLang({ locale }: { locale: string }) {
  useEffect(() => {
    if (locale) {
      document.documentElement.lang = locale;
    }
  }, [locale]);
  return null;
}
