"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";

import { DEFAULT_EMPLOYEE_EDIT_TAB_SLUG } from "@/lib/employee-edit-tabs";

/** Redirect `/app/employees/:id/edit` to the default tab route. */
export default function EmployeeEditIndexPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  useEffect(() => {
    if (!id) {
      return;
    }
    router.replace(`/app/employees/${id}/edit/${DEFAULT_EMPLOYEE_EDIT_TAB_SLUG}`);
  }, [id, router]);

  return null;
}
