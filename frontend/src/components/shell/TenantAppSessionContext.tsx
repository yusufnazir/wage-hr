"use client";

import { createContext, useContext, type ReactNode } from "react";

import type { MePayload, NavigationItem, TenantSummary } from "@/lib/api";

export type TenantAppSessionValue = {
  me: MePayload;
  navigation: NavigationItem[];
  navigationLoadError: number | null;
  tenants: TenantSummary[];
  tenantsLoadError: number | null;
  refreshMe: () => Promise<void>;
  patchLocale: (locale: string) => Promise<void>;
  localeBusy: boolean;
};

const TenantAppSessionContext = createContext<TenantAppSessionValue | null>(null);

export function TenantAppSessionProvider({
  value,
  children,
}: {
  value: TenantAppSessionValue;
  children: ReactNode;
}) {
  return <TenantAppSessionContext.Provider value={value}>{children}</TenantAppSessionContext.Provider>;
}

export function useTenantAppSession(): TenantAppSessionValue {
  const v = useContext(TenantAppSessionContext);
  if (!v) {
    throw new Error("useTenantAppSession must be used under TenantAppSessionProvider");
  }
  return v;
}
