import type { NextResponse } from "next/server";

/** Relay cookie names (browser); upstream uses {@code JSESSIONID} / {@code XSRF-TOKEN}. */
export const BFF_RELAY_JSESSION = "wp_bff_j";
export const BFF_RELAY_XSRF = "wp_bff_x";

export type SpringSessionJar = {
  JSESSIONID?: string;
  "XSRF-TOKEN"?: string;
};

function extractCookiePair(line: string): { name: string; value: string } | null {
  const sem = line.indexOf(";");
  const first = sem === -1 ? line : line.slice(0, sem);
  const eq = first.indexOf("=");
  if (eq <= 0) {
    return null;
  }
  const name = first.slice(0, eq).trim();
  const value = first.slice(eq + 1).trim();
  return { name, value };
}

function isDeletingCookie(line: string): boolean {
  const lower = line.toLowerCase();
  return (
    /\bmax-age=0\b/.test(lower) ||
    /\bexpires=thu,\s*01\s+jan\s+1970/i.test(lower) ||
    /\bexpires=wed,\s*31\s+dec\s+1969/i.test(lower)
  );
}

export function jarFromRelayCookies(get: (name: string) => { value: string } | undefined): SpringSessionJar {
  const j = get(BFF_RELAY_JSESSION)?.value;
  const x = get(BFF_RELAY_XSRF)?.value;
  const out: SpringSessionJar = {};
  if (j) {
    out.JSESSIONID = j;
  }
  if (x) {
    out["XSRF-TOKEN"] = x;
  }
  return out;
}

export function cookieHeaderForSpring(jar: SpringSessionJar): string | undefined {
  const parts: string[] = [];
  if (jar.JSESSIONID) {
    parts.push(`JSESSIONID=${jar.JSESSIONID}`);
  }
  if (jar["XSRF-TOKEN"]) {
    parts.push(`XSRF-TOKEN=${jar["XSRF-TOKEN"]}`);
  }
  return parts.length ? parts.join("; ") : undefined;
}

export function mergeSetCookieIntoJar(jar: SpringSessionJar, setCookieLines: string[]): SpringSessionJar {
  const out: SpringSessionJar = { ...jar };
  for (const line of setCookieLines) {
    const pair = extractCookiePair(line);
    if (!pair) {
      continue;
    }
    if (pair.name !== "JSESSIONID" && pair.name !== "XSRF-TOKEN") {
      continue;
    }
    if (isDeletingCookie(line)) {
      if (pair.name === "JSESSIONID") {
        delete out.JSESSIONID;
      }
      if (pair.name === "XSRF-TOKEN") {
        delete out["XSRF-TOKEN"];
      }
      continue;
    }
    if (pair.name === "JSESSIONID") {
      out.JSESSIONID = pair.value;
    } else {
      out["XSRF-TOKEN"] = pair.value;
    }
  }
  return out;
}

export function relayCookieOptions(browserHost: string) {
  const fromEnv = process.env.BFF_SESSION_COOKIE_DOMAIN?.trim();
  const domain =
    fromEnv ||
    (browserHost.toLowerCase().includes("lvh.me") ? ".lvh.me" : undefined);
  return {
    httpOnly: true as const,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/",
    ...(domain ? { domain } : {}),
  };
}

export function applyJarToResponse(res: NextResponse, jar: SpringSessionJar, browserHost: string): void {
  const opts = relayCookieOptions(browserHost);
  if (jar.JSESSIONID) {
    res.cookies.set(BFF_RELAY_JSESSION, jar.JSESSIONID, opts);
  } else {
    res.cookies.set(BFF_RELAY_JSESSION, "", { ...opts, maxAge: 0 });
  }
  if (jar["XSRF-TOKEN"]) {
    res.cookies.set(BFF_RELAY_XSRF, jar["XSRF-TOKEN"], opts);
  } else {
    res.cookies.set(BFF_RELAY_XSRF, "", { ...opts, maxAge: 0 });
  }
}

export function clearRelayCookies(res: NextResponse, browserHost: string): void {
  const opts = relayCookieOptions(browserHost);
  res.cookies.set(BFF_RELAY_JSESSION, "", { ...opts, maxAge: 0 });
  res.cookies.set(BFF_RELAY_XSRF, "", { ...opts, maxAge: 0 });
}
