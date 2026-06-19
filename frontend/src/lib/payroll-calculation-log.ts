export function downloadTextFile(filename: string, text: string): void {
  const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function payrollCalculationLogFilename(employeeLabel: string, year: number, period: number): string {
  const safe = employeeLabel.replace(/[^\w\s-]/g, "").trim().replace(/\s+/g, "-") || "employee";
  return `payroll-calculation-${safe}-${year}-${period}.txt`;
}
