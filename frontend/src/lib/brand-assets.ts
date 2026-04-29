/**
 * Public paths under `frontend/public/wage/` (filenames with spaces are URL-encoded).
 * Favicon: squircle WP mark. Wordmark: compact transparent logo for sidebar / auth.
 */
export const brandFaviconSrc = "/wage/favicon.png";

const enc = (name: string) => `/wage/${encodeURIComponent(name)}`;

/** Horizontal wordmark, compact — sidebar expanded, marketing */
export const brandLogoWordmarkSmallSrc = enc("wage payroll-3-transparent-small.png");

/** Wider horizontal variant — optional hero */
export const brandLogoWordmarkRectSrc = enc("wage payroll-2-transparent-rect.png");
