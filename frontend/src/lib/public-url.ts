/**
 * Match backend {@link com.wagepayroll.settings.PlatformUrlJoin}: strip trailing slashes from base, append path starting with `/`.
 */
export function joinPublicBaseAndPath(base: string, absolutePath: string): string {
  if (!absolutePath.startsWith("/")) {
    throw new Error("absolutePath must start with /");
  }
  let b = base.trim();
  while (b.endsWith("/")) {
    b = b.slice(0, -1);
  }
  return b + absolutePath;
}
