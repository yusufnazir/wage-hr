import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

import { BFF_RELAY_JSESSION } from "@/lib/server/spring-bff-cookies";

function authWebBase(): string {
  return (process.env.NEXT_PUBLIC_AUTH_WEB_ORIGIN ?? "http://auth.lvh.me:3007").replace(/\/$/, "");
}

function authHostname(): string {
  try {
    return new URL(authWebBase()).hostname.toLowerCase();
  } catch {
    return "auth.lvh.me";
  }
}

function requestHostname(hostHeader: string): string {
  const h = hostHeader.trim().toLowerCase();
  return h.includes(":") ? h.split(":")[0]! : h;
}

function isAuthHost(hostHeader: string): boolean {
  return requestHostname(hostHeader) === authHostname();
}

const AUTH_SURFACE_PATHS = ["/login", "/register", "/forgot-password", "/reset-password"];

function isAuthSurfacePath(pathname: string): boolean {
  return AUTH_SURFACE_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

function looksAnonymous(request: NextRequest): boolean {
  return !request.cookies.get(BFF_RELAY_JSESSION)?.value;
}

/**
 * Default entry for unauthenticated users is the **auth** host: `/` and auth UI paths are served there.
 * Mirrors multi-host contract (see docs/modules/tenancy-routing.md).
 */
export function middleware(request: NextRequest) {
  const host = request.headers.get("host") ?? "";
  const { pathname, search } = request.nextUrl;

  if (!isAuthHost(host)) {
    if (pathname === "/" && looksAnonymous(request)) {
      return NextResponse.redirect(new URL(`/login${search}`, `${authWebBase()}/`));
    }
    if (isAuthSurfacePath(pathname)) {
      return NextResponse.redirect(new URL(`${pathname}${search}`, `${authWebBase()}/`));
    }
  } else if (pathname === "/" && looksAnonymous(request)) {
    return NextResponse.redirect(new URL(`/login${search}`, request.url));
  }

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-wage-host", host);
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"],
};
