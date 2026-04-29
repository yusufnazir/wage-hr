import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { getUpstreamApiBaseUrl } from "@/lib/server/upstream-base";
import {
  BFF_RELAY_JSESSION,
  BFF_RELAY_XSRF,
  applyJarToResponse,
  clearRelayCookies,
  cookieHeaderForSpring,
  jarFromRelayCookies,
  mergeSetCookieIntoJar,
  type SpringSessionJar,
} from "@/lib/server/spring-bff-cookies";
import { readLensTenantIdFromCookieStore, shouldAttachLensTenantHeader } from "@/lib/server/lens-tenant-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const MUTATING = new Set(["POST", "PATCH", "PUT", "DELETE"]);

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
  "host",
  "content-length",
]);

async function readSetCookies(res: Response): Promise<string[]> {
  const h = res.headers as unknown as { getSetCookie?: () => string[] };
  if (typeof h.getSetCookie === "function") {
    return h.getSetCookie();
  }
  const single = res.headers.get("set-cookie");
  return single ? [single] : [];
}

function buildUpstreamPath(segments: string[]): string | null {
  if (segments.length === 0 || segments[0] !== "v1") {
    return null;
  }
  return `/api/${segments.join("/")}`;
}

function browserHost(request: NextRequest): string {
  return request.headers.get("host") ?? "localhost";
}

async function handle(request: NextRequest, segments: string[], method: string): Promise<NextResponse> {
  const upstreamPath = buildUpstreamPath(segments);
  if (!upstreamPath) {
    return NextResponse.json({ error: "Not found" }, { status: 404 });
  }

  const base = getUpstreamApiBaseUrl();
  const targetUrl = new URL(upstreamPath + request.nextUrl.search, base).toString();
  const host = browserHost(request);
  const proto = request.nextUrl.protocol.replace(":", "") || "http";

  const cookieStore = await cookies();
  const hadRelay = !!(cookieStore.get(BFF_RELAY_JSESSION) || cookieStore.get(BFF_RELAY_XSRF));

  let jar: SpringSessionJar = jarFromRelayCookies((name) => cookieStore.get(name));

  if (MUTATING.has(method)) {
    const csrfUrl = new URL("/api/v1/auth/csrf", base).toString();
    const csrfHeaders = new Headers();
    const ch0 = cookieHeaderForSpring(jar);
    if (ch0) {
      csrfHeaders.set("Cookie", ch0);
    }
    csrfHeaders.set("X-Forwarded-Host", host);
    csrfHeaders.set("X-Forwarded-Proto", proto);
    const csrfRes = await fetch(csrfUrl, { method: "GET", headers: csrfHeaders });
    jar = mergeSetCookieIntoJar(jar, await readSetCookies(csrfRes));
    if (!csrfRes.ok) {
      return NextResponse.json(
        { error: "CSRF prefetch failed", status: csrfRes.status },
        { status: csrfRes.status === 401 || csrfRes.status === 403 ? csrfRes.status : 502 },
      );
    }
    const csrfJson = (await csrfRes.json()) as {
      data: { token: string; headerName: string };
    };
    const csrfToken = csrfJson.data.token;
    const csrfHeaderName = csrfJson.data.headerName;

    const bodyText = await request.text();
    const forwardHeaders = new Headers();
    forwardHeaders.set("X-Forwarded-Host", host);
    forwardHeaders.set("X-Forwarded-Proto", proto);
    const lensId = readLensTenantIdFromCookieStore(cookieStore);
    if (lensId && shouldAttachLensTenantHeader(upstreamPath)) {
      forwardHeaders.set("X-Tenant-Id", lensId);
    }
    const ch1 = cookieHeaderForSpring(jar);
    if (ch1) {
      forwardHeaders.set("Cookie", ch1);
    }
    forwardHeaders.set(csrfHeaderName, csrfToken);
    const contentType = request.headers.get("content-type");
    if (contentType) {
      forwardHeaders.set("Content-Type", contentType);
    }
    const breakGlass = request.headers.get("x-break-glass-reason");
    if (breakGlass) {
      forwardHeaders.set("X-Break-Glass-Reason", breakGlass);
    }
    const accept = request.headers.get("accept");
    if (accept) {
      forwardHeaders.set("Accept", accept);
    }

    const upstreamRes = await fetch(targetUrl, {
      method,
      headers: forwardHeaders,
      body: bodyText.length ? bodyText : undefined,
      redirect: "manual",
    });
    jar = mergeSetCookieIntoJar(jar, await readSetCookies(upstreamRes));
    return buildBffResponse(upstreamRes, jar, host, hadRelay, upstreamPath);
  }

  const forwardHeaders = new Headers();
  forwardHeaders.set("X-Forwarded-Host", host);
  forwardHeaders.set("X-Forwarded-Proto", proto);
  const lensIdGet = readLensTenantIdFromCookieStore(cookieStore);
  if (lensIdGet && shouldAttachLensTenantHeader(upstreamPath)) {
    forwardHeaders.set("X-Tenant-Id", lensIdGet);
  }
  const ch = cookieHeaderForSpring(jar);
  if (ch) {
    forwardHeaders.set("Cookie", ch);
  }
  const accept = request.headers.get("accept");
  if (accept) {
    forwardHeaders.set("Accept", accept);
  }

  const upstreamRes = await fetch(targetUrl, {
    method,
    headers: forwardHeaders,
    redirect: "manual",
  });
  jar = mergeSetCookieIntoJar(jar, await readSetCookies(upstreamRes));
  return buildBffResponse(upstreamRes, jar, host, hadRelay, upstreamPath);
}

function buildBffResponse(
  upstreamRes: Response,
  jar: SpringSessionJar,
  host: string,
  hadRelay: boolean,
  upstreamPath: string,
): NextResponse {
  const success = upstreamRes.status >= 200 && upstreamRes.status < 300;

  const out =
    upstreamRes.status === 204
      ? new NextResponse(null, { status: 204, statusText: upstreamRes.statusText })
      : new NextResponse(upstreamRes.body, {
          status: upstreamRes.status,
          statusText: upstreamRes.statusText,
        });

  upstreamRes.headers.forEach((value, key) => {
    const lk = key.toLowerCase();
    if (lk === "set-cookie" || HOP_BY_HOP.has(lk)) {
      return;
    }
    out.headers.set(key, value);
  });

  if (upstreamRes.status === 401) {
    clearRelayCookies(out, host);
  } else if (upstreamPath === "/api/v1/auth/logout" && success) {
    clearRelayCookies(out, host);
  } else if (success) {
    applyJarToResponse(out, jar, host);
  } else if (hadRelay) {
    applyJarToResponse(out, jar, host);
  } else {
    clearRelayCookies(out, host);
  }

  return out;
}

export async function GET(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  return handle(request, path, "GET");
}

export async function POST(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  return handle(request, path, "POST");
}

export async function PATCH(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  return handle(request, path, "PATCH");
}

export async function PUT(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  return handle(request, path, "PUT");
}

export async function DELETE(request: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  return handle(request, path, "DELETE");
}
