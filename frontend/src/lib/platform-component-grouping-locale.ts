import type { PlatformComponentTranslation } from "@/lib/api";

export const LOCALE_ORDER = ["en", "nl"] as const;
export type LocaleKey = (typeof LOCALE_ORDER)[number];

export type LocaleDrafts = Record<LocaleKey, { name: string; description: string }>;

export function draftsFromTranslations(tr: PlatformComponentTranslation[]): LocaleDrafts {
  const map = new Map(tr.map((x) => [x.locale.toLowerCase(), x]));
  const out = {} as LocaleDrafts;
  for (const loc of LOCALE_ORDER) {
    const row = map.get(loc);
    out[loc] = { name: row?.name ?? "", description: row?.description ?? "" };
  }
  return out;
}

export function emptyLocaleDrafts(): LocaleDrafts {
  return { en: { name: "", description: "" }, nl: { name: "", description: "" } };
}

export function localeSectionTitleKey(loc: LocaleKey): string {
  if (loc === "en") return "platformComponentGroups.locale.en";
  return "platformComponentGroups.locale.nl";
}

export function primaryLocale(uiLocale: string): LocaleKey {
  const v = uiLocale.trim().toLowerCase().replace("_", "-");
  return v === "nl" ? "nl" : "en";
}

/**
 * When creating from Basics with only one locale filled, use that name for the other locale too
 * (same as platform component group "default name" on create).
 */
export function coalesceCreateLocaleDrafts(drafts: LocaleDrafts): LocaleDrafts {
  const en = drafts.en.name.trim();
  const nl = drafts.nl.name.trim();
  if (en && !nl) {
    return { en: drafts.en, nl: { ...drafts.nl, name: en } };
  }
  if (nl && !en) {
    return { en: { ...drafts.en, name: nl }, nl: drafts.nl };
  }
  return drafts;
}

/** Item lines use the wage component catalog name for both EN and NL until item i18n is supported. */
export function itemTranslationsFromCatalogName(catalogName: string): PlatformComponentTranslation[] {
  const name = catalogName.trim() || "—";
  return [
    { locale: "en", name, description: null },
    { locale: "nl", name, description: null },
  ];
}

export function buildTranslationsPayload(drafts: LocaleDrafts): PlatformComponentTranslation[] {
  const en = drafts.en.name.trim();
  const nl = drafts.nl.name.trim();
  if (!en || !nl) {
    throw new Error("NAME_REQUIRED");
  }
  return [
    { locale: "en", name: en, description: drafts.en.description.trim() || null },
    { locale: "nl", name: nl, description: drafts.nl.description.trim() || null },
  ];
}
