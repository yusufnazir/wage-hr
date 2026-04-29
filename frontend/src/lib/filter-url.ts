export function nextSearchParams(
  base: URLSearchParams,
  patch: Record<string, string | undefined>,
): URLSearchParams {
  const n = new URLSearchParams(base.toString());
  for (const [k, v] of Object.entries(patch)) {
    if (v === undefined || v === "") {
      n.delete(k);
    } else {
      n.set(k, v);
    }
  }
  return n;
}

export function toQueryString(params: URLSearchParams): string {
  const s = params.toString();
  return s ? `?${s}` : "";
}

