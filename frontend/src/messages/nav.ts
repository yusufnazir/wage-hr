/**
 * Client-side labels for server `label_key` values (see `docs/modules/i18n.md`).
 * Flutter should mirror keys in its ARB/JSON bundles.
 */

const en: Record<string, string> = {
  "nav.dashboard": "Dashboard",
  "nav.users": "Users",
  "nav.tenant_settings": "Tenant settings",
};

const nl: Record<string, string> = {
  "nav.dashboard": "Overzicht",
  "nav.users": "Gebruikers",
  "nav.tenant_settings": "Tenantinstellingen",
};

function bundleFor(locale: string): Record<string, string> {
  const lc = locale.trim().toLowerCase();
  if (lc === "nl" || lc.startsWith("nl-")) {
    return nl;
  }
  return en;
}

export function navLabel(locale: string, key: string): string {
  const b = bundleFor(locale);
  return b[key] ?? en[key] ?? key;
}
