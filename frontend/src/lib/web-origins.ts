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

const AUTH_SURFACE_PATHS = [
  "/login",
  "/register",
  "/verify-email",
  "/forgot-password",
  "/reset-password",
  "/terms-of-service",
  "/privacy-policy",
] as const;

/** Auth-host sign-in and registration paths (and legal pages served on the auth workspace). */
export function isAuthSurfacePathname(pathname: string): boolean {
  return AUTH_SURFACE_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

function isLoginUrlOnAuthHost(url: URL): boolean {
  try {
    return url.origin === new URL(getAuthWebOrigin()).origin && url.pathname === "/login";
  } catch {
    return false;
  }
}

/**
 * Walk nested {@code /login?returnTo=/login?returnTo=…} chains from a stale session redirect.
 * Returns the first absolute URL that is not another login page, or {@code null} if none.
 */
export function unwrapLoginReturnToChain(absoluteUrl: string): string | null {
  let current: string | null = absoluteUrl;
  const seen = new Set<string>();
  for (let depth = 0; depth < 16 && current; depth += 1) {
    if (seen.has(current)) {
      return null;
    }
    seen.add(current);
    let url: URL;
    try {
      url = new URL(current);
    } catch {
      return null;
    }
    if (!isLoginUrlOnAuthHost(url)) {
      return current;
    }
    current = url.searchParams.get("returnTo");
  }
  return null;
}

export function authLoginUrl(): string {
  return `${getAuthWebOrigin()}/login`;
}

/** Login URL on the auth host with optional post-login target (must pass backend {@code redirect-check}). */
export function authLoginUrlWithReturnTo(absoluteReturnUrl: string): string {
  const ultimate = unwrapLoginReturnToChain(absoluteReturnUrl) ?? absoluteReturnUrl;
  try {
    const target = new URL(ultimate);
    if (isLoginUrlOnAuthHost(target)) {
      return authLoginUrl();
    }
  } catch {
    return authLoginUrl();
  }
  const u = new URL("/login", `${getAuthWebOrigin()}/`);
  u.searchParams.set("returnTo", ultimate);
  return u.toString();
}

/**
 * Post-expiry redirect target from the current browser location.
 * {@code null} on auth surfaces (e.g. login) where the UI already handles anonymous 401s.
 */
export function sessionExpiredReturnTo(location: Pick<Location, "origin" | "pathname" | "search" | "hash">): string | null {
  if (isAuthSurfacePathname(location.pathname)) {
    return null;
  }
  const raw = `${location.origin}${location.pathname}${location.search}${location.hash}`;
  return unwrapLoginReturnToChain(raw) ?? raw;
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
