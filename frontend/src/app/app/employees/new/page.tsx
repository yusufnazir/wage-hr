"use client";

import Link from "next/link";
import { Suspense } from "react";

import { EmployeeCreateWizard } from "@/components/employee/EmployeeCreateWizard";
import { useTenantAppSession } from "@/components/shell/TenantAppSessionContext";
import { navLabel } from "@/messages/nav";

function EmployeeNewPageContent() {
  return <EmployeeCreateWizard />;
}

export default function EmployeeNewPage() {
  const { me } = useTenantAppSession();
  const t = (key: string) => navLabel(me.locale, key);
  const canManage = me.privileges.includes("EMPLOYEE_MANAGE");

  if (!canManage) {
    return (
      <div className="mx-auto max-w-lg space-y-4">
        <h1 className="text-lg font-semibold text-foreground">{t("employees.action.new")}</h1>
        <p className="text-sm text-muted">{t("employees.error.forbidden")}</p>
        <Link href="/app/employees" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          {"<- "}{t("employees.title")}
        </Link>
      </div>
    );
  }

  return (
    <Suspense fallback={<p className="mx-auto max-w-xl text-sm text-muted">{t("employees.state.loading")}</p>}>
      <EmployeeNewPageContent />
    </Suspense>
  );
}
