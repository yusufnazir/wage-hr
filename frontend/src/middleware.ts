import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";

/**
 * Host-aware routing scaffold: attach tenant hint for debugging; full auth routing is env-driven per architecture.
 * Local dev: use *.lvh.me:3007 (see docs/guides/LOCAL-DEV-PORTS.md).
 */
export function middleware(request: NextRequest) {
  const host = request.headers.get("host") ?? "";
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-wage-host", host);
  return NextResponse.next({ request: { headers: requestHeaders } });
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)"],
};
