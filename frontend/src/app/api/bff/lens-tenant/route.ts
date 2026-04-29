import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { isUuidString, LENS_TENANT_COOKIE } from "@/lib/server/lens-tenant-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function lensCookieOptions(maxAgeSeconds: number) {
  return {
    httpOnly: true as const,
    sameSite: "lax" as const,
    path: "/",
    secure: process.env.NODE_ENV === "production",
    maxAge: maxAgeSeconds,
  };
}

export async function POST(request: NextRequest) {
  let body: { tenantId?: string; clear?: boolean };
  try {
    body = (await request.json()) as { tenantId?: string; clear?: boolean };
  } catch {
    return NextResponse.json({ error: "Invalid JSON" }, { status: 400 });
  }
  const res = NextResponse.json({ ok: true });
  if (body.clear === true) {
    res.cookies.delete(LENS_TENANT_COOKIE);
    return res;
  }
  const tid = body.tenantId?.trim();
  if (!tid || !isUuidString(tid)) {
    return NextResponse.json({ error: "tenantId must be a UUID" }, { status: 400 });
  }
  res.cookies.set(LENS_TENANT_COOKIE, tid.toLowerCase(), lensCookieOptions(60 * 60 * 8));
  return res;
}

export async function DELETE() {
  const res = NextResponse.json({ ok: true });
  res.cookies.delete(LENS_TENANT_COOKIE);
  return res;
}
