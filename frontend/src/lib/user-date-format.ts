/**
 * Date-only display for ISO calendar dates (API date-only fields use UTC `yyyy-MM-dd`; instants use the calendar date part).
 * Tokens align with {@code platform.date_format} / {@code MePayload.dateFormat}.
 */
export function formatUserFacingDate(iso: string, dateFormat: string): string {
  const datePart = iso.includes("T") ? iso.slice(0, 10) : iso.slice(0, 10);
  const parts = datePart.split("-").map((x) => Number.parseInt(x, 10));
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) {
    return iso;
  }
  const [y, m, day] = parts;
  const token = dateFormat.trim();
  if (token === "dd/MM/yyyy") {
    return `${String(day).padStart(2, "0")}/${String(m).padStart(2, "0")}/${y}`;
  }
  if (token === "MM/dd/yyyy") {
    return `${String(m).padStart(2, "0")}/${String(day).padStart(2, "0")}/${y}`;
  }
  if (isCustomDatePattern(token)) {
    const yyyy = String(y).padStart(4, "0");
    const MM = String(m).padStart(2, "0");
    const dd = String(day).padStart(2, "0");
    // Replace longest-first to avoid partial token collisions.
    return token.replaceAll("yyyy", yyyy).replaceAll("MM", MM).replaceAll("dd", dd);
  }
  return datePart;
}

function isCustomDatePattern(token: string): boolean {
  if (!token) {
    return false;
  }
  if (token === "yyyy-MM-dd" || token === "dd/MM/yyyy" || token === "MM/dd/yyyy" || token === "ISO-8601") {
    return false;
  }
  if (token.length < 6 || token.length > 32) {
    return false;
  }
  if (!/^[yMd\-/. ]+$/.test(token)) {
    return false;
  }
  if (countOccurrences(token, "yyyy") !== 1 || countOccurrences(token, "MM") !== 1 || countOccurrences(token, "dd") !== 1) {
    return false;
  }
  // Reject ambiguous tokens.
  if (token.includes("yy") && !token.includes("yyyy")) {
    return false;
  }
  if (token.includes("M") && !token.includes("MM")) {
    return false;
  }
  if (token.includes("d") && !token.includes("dd")) {
    return false;
  }
  return true;
}

function countOccurrences(s: string, needle: string): number {
  let n = 0;
  let i = 0;
  while (true) {
    const idx = s.indexOf(needle, i);
    if (idx < 0) {
      return n;
    }
    n += 1;
    i = idx + needle.length;
  }
}
