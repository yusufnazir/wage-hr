import type { ReactNode } from "react";

import { TenantAppShell } from "@/components/shell/TenantAppShell";

export default function TenantAppLayout({ children }: { children: ReactNode }) {
  return <TenantAppShell>{children}</TenantAppShell>;
}
