/**
 * Browser-visible web origins for multi-host local dev (*.lvh.me + port 3007).
 * Override with NEXT_PUBLIC_* when ports or hosts differ.
 */

export function getAuthWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_AUTH_WEB_ORIGIN ?? "http://auth.lvh.me:3007").replace(/\/$/, "");
}

/** Whether {@code hostname} (no port, or full host header) matches the configured auth web origin. */
export function isAuthWorkspaceHostname(hostname: string): boolean {
  const u = new URL(getAuthWebOrigin());
  const h = (hostname.includes(":") ? hostname.split(":")[0]! : hostname).toLowerCase();
  return h === u.hostname.toLowerCase();
}

/** Seeded demo tenant UI origin (tenant handle `demo`). */
export function getDefaultTenantWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_DEFAULT_TENANT_WEB_ORIGIN ?? "http://demo.lvh.me:3007").replace(/\/$/, "");
}

/** Platform operator (superadmin) Next.js origin — must match `admin.{BASE_DOMAIN}` in Spring `Host` routing. */
export function getAdminWebOrigin(): string {
  return (process.env.NEXT_PUBLIC_ADMIN_WEB_ORIGIN ?? "http://admin.lvh.me:3007").replace(/\/$/, "");
}

/** Whether `hostname` is the configured admin workspace host (first label must match admin origin). */
export function isAdminWorkspaceHostname(hostname: string): boolean {
  const admin = new URL(getAdminWebOrigin());
  return hostname.trim().toLowerCase() === admin.hostname.toLowerCase();
}

/** True when {@code absoluteUrl} targets the configured admin web origin (operator workspace). */
export function isAdminWebOriginUrl(absoluteUrl: string): boolean {
  try {
    return new URL(absoluteUrl).origin === new URL(getAdminWebOrigin()).origin;
  } catch {
    return false;
  }
}

export function authLoginUrl(): string {
  return `${getAuthWebOrigin()}/login`;
}

/** Login URL on the auth host with optional post-login target (must pass backend {@code redirect-check}). */
export function authLoginUrlWithReturnTo(absoluteReturnUrl: string): string {
  const u = new URL("/login", `${getAuthWebOrigin()}/`);
  u.searchParams.set("returnTo", absoluteReturnUrl);
  return u.toString();
}

export function defaultTenantAppUrl(): string {
  return `${getDefaultTenantWebOrigin()}/app`;
}

/**
 * Build `/app` URL for another tenant handle using the same scheme/port and **first DNS label**
 * as {@link getDefaultTenantWebOrigin} (intended for `{handle}.lvh.me` dev hosts).
 */
export function tenantWebAppUrlForHandle(handle: string): string {
  const template = getDefaultTenantWebOrigin();
  const u = new URL(template);
  const labels = u.hostname.split(".");
  labels[0] = handle;
  u.hostname = labels.join(".");
  return `${u.origin}/app`;
}
