"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  TAX_RULE_PARAMETER_FREQS,
  TAX_RULE_PARAMETER_KINDS,
  defaultTaxRuleParametersForm,
  nextMarginalIndex,
  nextServiceYearIndex,
  parseTaxRuleParametersJson,
  serializeTaxRuleParameters,
  type TaxRuleParameterKind,
  type TaxRuleParametersForm,
} from "@/lib/country-tax-rule-parameters";
import { navLabel } from "@/messages/nav";

type Props = {
  locale: string;
  value: string;
  onChange: (json: string) => void;
};

const MAX_JSON_LEN = 4000;

function initialStateFromValue(raw: string): {
  form: TaxRuleParametersForm;
  extras: Record<string, unknown>;
  jsonDraft: string;
} {
  const parsed = parseTaxRuleParametersJson(raw);
  if (parsed.ok) {
    const json = serializeTaxRuleParameters(parsed.form, parsed.extras);
    return { form: parsed.form, extras: parsed.extras, jsonDraft: json };
  }
  return { form: defaultTaxRuleParametersForm(), extras: {}, jsonDraft: raw };
}

export function CountryTaxRuleParametersEditor({ locale, value, onChange }: Props) {
  const t = useCallback((key: string) => navLabel(locale, key), [locale]);

  const [jsonMode, setJsonMode] = useState(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [initial] = useState(() => initialStateFromValue(value));
  const [form, setForm] = useState<TaxRuleParametersForm>(initial.form);
  const [extras, setExtras] = useState<Record<string, unknown>>(initial.extras);
  const [jsonDraft, setJsonDraft] = useState(initial.jsonDraft);
  /** Tracks last value pushed to parent; null so the first load always parses server JSON. */
  const lastEmittedRef = useRef<string | null>(null);

  const applyParsed = useCallback(
    (raw: string) => {
      const result = parseTaxRuleParametersJson(raw);
      if (!result.ok) {
        setParseError(result.error);
        setJsonMode(true);
        setJsonDraft(raw);
        return false;
      }
      setParseError(null);
      setForm(result.form);
      setExtras(result.extras);
      setJsonDraft(serializeTaxRuleParameters(result.form, result.extras));
      return true;
    },
    [],
  );

  useEffect(() => {
    if (value === lastEmittedRef.current) {
      return;
    }
    lastEmittedRef.current = value;
    applyParsed(value);
  }, [value, applyParsed]);

  const serialized = useMemo(() => serializeTaxRuleParameters(form, extras), [form, extras]);
  const serializedLen = serialized.length;

  const pushStructured = useCallback(
    (next: TaxRuleParametersForm, nextExtras = extras) => {
      setForm(next);
      const json = serializeTaxRuleParameters(next, nextExtras);
      setJsonDraft(json);
      lastEmittedRef.current = json;
      onChange(json);
    },
    [extras, onChange],
  );

  function switchKind(kind: TaxRuleParameterKind) {
    const base = { ...form, kind };
    if (kind === "MARGINAL_RATES" && base.marginalRows.length === 0) {
      base.marginalRows = [{ i: 1, pct: 0, min: 0, max: null }];
    }
    if (kind === "LEGACY_SERVICE_YEAR_TABLE" && base.serviceYearRows.length === 0) {
      base.serviceYearRows = [{ i: 1, pct: 0, lo: 0, hi: null }];
    }
    pushStructured(base);
  }

  function enterJsonMode() {
    setJsonMode(true);
    setJsonDraft(value);
  }

  function applyJsonDraft() {
    const trimmed = jsonDraft.trim();
    if (!trimmed) {
      setParseError("INVALID_JSON");
      return;
    }
    if (trimmed.length > MAX_JSON_LEN) {
      setParseError("TOO_LONG");
      return;
    }
    if (applyParsed(trimmed)) {
      lastEmittedRef.current = trimmed;
      onChange(trimmed);
      setJsonMode(false);
    }
  }

  function tryStructuredFromJson() {
    const result = parseTaxRuleParametersJson(jsonDraft);
    if (!result.ok) {
      setParseError(result.error);
      return;
    }
    const json = serializeTaxRuleParameters(result.form, result.extras);
    setForm(result.form);
    setExtras(result.extras);
    setParseError(null);
    setJsonDraft(json);
    lastEmittedRef.current = json;
    onChange(json);
    setJsonMode(false);
  }

  const errorMessage =
    parseError === "INVALID_JSON"
      ? t("platformCountryTaxRules.params.error.invalidJson")
      : parseError === "TOO_LONG"
        ? t("platformCountryTaxRules.params.error.tooLong")
        : parseError
          ? t("platformCountryTaxRules.params.error.unsupported")
          : null;

  if (jsonMode) {
    return (
      <div className="space-y-3" data-testid="tax-rule-params-json-mode">
        {errorMessage ? <p className="text-sm text-destructive">{errorMessage}</p> : null}
        <p className="text-xs text-muted">{t("platformCountryTaxRules.params.hint.jsonMode")}</p>
        <textarea
          className="min-h-[220px] w-full rounded border border-border bg-background px-3 py-2 font-mono text-xs leading-relaxed"
          value={jsonDraft}
          onChange={(e) => setJsonDraft(e.target.value)}
          spellCheck={false}
          data-testid="tax-rule-params-json-textarea"
        />
        <p
          className={`text-xs ${jsonDraft.trim().length > MAX_JSON_LEN ? "text-destructive font-medium" : "text-muted"}`}
        >
          {t("platformCountryTaxRules.params.charCount").replace("{n}", String(jsonDraft.trim().length))}
        </p>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="rounded border border-border bg-background px-3 py-1.5 text-sm font-medium"
            onClick={() => void applyJsonDraft()}
          >
            {t("platformCountryTaxRules.params.action.applyJson")}
          </button>
          <button
            type="button"
            className="rounded border border-border px-3 py-1.5 text-sm text-muted"
            onClick={() => void tryStructuredFromJson()}
          >
            {t("platformCountryTaxRules.params.action.tryStructured")}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 rounded-md border border-border bg-background/50 p-4" data-testid="tax-rule-params-structured">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.section")}</p>
        <button
          type="button"
          className="text-xs font-medium text-primary underline-offset-4 hover:underline"
          onClick={enterJsonMode}
        >
          {t("platformCountryTaxRules.params.action.editJson")}
        </button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.kind")}</label>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.kind}
            onChange={(e) => switchKind(e.target.value as TaxRuleParameterKind)}
            data-testid="tax-rule-params-kind"
          >
            {TAX_RULE_PARAMETER_KINDS.map((k) => (
              <option key={k} value={k}>
                {t(`platformCountryTaxRules.params.kind.${k}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.freq")}</label>
          <select
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.freq}
            onChange={(e) => pushStructured({ ...form, freq: e.target.value as TaxRuleParametersForm["freq"] })}
          >
            <option value="">{t("platformCountryTaxRules.params.freq.unset")}</option>
            {TAX_RULE_PARAMETER_FREQS.map((f) => (
              <option key={f} value={f}>
                {f}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">
            {t("platformCountryTaxRules.params.label.legacyTariff")}
          </label>
          <input
            type="number"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.legacyTariffTypeId ?? ""}
            onChange={(e) => {
              const v = e.target.value.trim();
              pushStructured({
                ...form,
                legacyTariffTypeId: v === "" ? null : Number(v),
              });
            }}
            min={0}
            step={1}
          />
        </div>
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.source")}</label>
          <input
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.source}
            onChange={(e) => pushStructured({ ...form, source: e.target.value })}
            placeholder="legacy-740-2024"
          />
        </div>
      </div>

      {form.kind === "MARGINAL_RATES" ? (
        <MarginalRatesTable
          rows={form.marginalRows}
          t={t}
          onChange={(marginalRows) => pushStructured({ ...form, marginalRows })}
        />
      ) : null}

      {form.kind === "LEGACY_SERVICE_YEAR_TABLE" ? (
        <ServiceYearTable
          rows={form.serviceYearRows}
          t={t}
          onChange={(serviceYearRows) => pushStructured({ ...form, serviceYearRows })}
        />
      ) : null}

      {form.kind === "FLAT_RATE" ? (
        <div className="space-y-1 max-w-xs">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.pct")}</label>
          <input
            type="number"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.pct ?? ""}
            onChange={(e) => {
              const v = e.target.value.trim();
              pushStructured({ ...form, pct: v === "" ? null : Number(v) });
            }}
            step="0.01"
          />
        </div>
      ) : null}

      {form.kind === "THRESHOLD_AMOUNT" ? (
        <div className="space-y-1 max-w-xs">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.amount")}</label>
          <input
            type="number"
            className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.amount ?? ""}
            onChange={(e) => {
              const v = e.target.value.trim();
              pushStructured({ ...form, amount: v === "" ? null : Number(v) });
            }}
            step="1"
          />
        </div>
      ) : null}

      {form.kind === "AMOUNT_BAND" ? (
        <div className="grid max-w-md gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.min")}</label>
            <input
              type="number"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.min ?? ""}
              onChange={(e) => {
                const v = e.target.value.trim();
                pushStructured({ ...form, min: v === "" ? null : Number(v) });
              }}
              step="1"
            />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.max")}</label>
            <input
              type="number"
              className="w-full rounded border border-border bg-background px-3 py-2 text-sm"
              value={form.max ?? ""}
              onChange={(e) => {
                const v = e.target.value.trim();
                pushStructured({ ...form, max: v === "" ? null : Number(v) });
              }}
              step="1"
            />
          </div>
        </div>
      ) : null}

      {form.kind === "PLACEHOLDER" ? (
        <div className="space-y-1">
          <label className="text-xs font-medium uppercase text-muted">{t("platformCountryTaxRules.params.label.note")}</label>
          <textarea
            className="min-h-[80px] w-full rounded border border-border bg-background px-3 py-2 text-sm"
            value={form.note}
            onChange={(e) => pushStructured({ ...form, note: e.target.value })}
          />
        </div>
      ) : null}

      <p className={`text-xs ${serializedLen > MAX_JSON_LEN ? "text-destructive font-medium" : "text-muted"}`}>
        {t("platformCountryTaxRules.params.charCount").replace("{n}", String(serializedLen))}
      </p>
    </div>
  );
}

function MarginalRatesTable({
  rows,
  t,
  onChange,
}: {
  rows: TaxRuleParametersForm["marginalRows"];
  t: (key: string) => string;
  onChange: (rows: TaxRuleParametersForm["marginalRows"]) => void;
}) {
  function updateRow(idx: number, patch: Partial<TaxRuleParametersForm["marginalRows"][0]>) {
    onChange(rows.map((row, i) => (i === idx ? { ...row, ...patch } : row)));
  }

  return (
    <div className="space-y-2">
      <p className="text-xs text-muted">{t("platformCountryTaxRules.params.hint.marginal")}</p>
      <div className="overflow-x-auto rounded border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-2 py-1.5 w-16">{t("platformCountryTaxRules.params.col.index")}</th>
              <th className="px-2 py-1.5 w-24">{t("platformCountryTaxRules.params.col.pct")}</th>
              <th className="px-2 py-1.5">{t("platformCountryTaxRules.params.col.min")}</th>
              <th className="px-2 py-1.5">{t("platformCountryTaxRules.params.col.max")}</th>
              <th className="px-2 py-1.5 w-12" />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, idx) => (
              <tr key={`${row.i}-${idx}`} className="border-t border-border">
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm font-mono"
                    value={row.i}
                    onChange={(e) => updateRow(idx, { i: Number(e.target.value) || 0 })}
                    min={1}
                    step={1}
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.pct}
                    onChange={(e) => updateRow(idx, { pct: Number(e.target.value) || 0 })}
                    step="0.01"
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.min}
                    onChange={(e) => updateRow(idx, { min: Number(e.target.value) || 0 })}
                    step="1"
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.max ?? ""}
                    placeholder={t("platformCountryTaxRules.params.openEnded")}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      updateRow(idx, { max: v === "" ? null : Number(v) });
                    }}
                    step="1"
                  />
                </td>
                <td className="px-2 py-1 text-center">
                  <button
                    type="button"
                    className="text-muted hover:text-destructive disabled:opacity-30"
                    disabled={rows.length <= 1}
                    onClick={() => onChange(rows.filter((_, i) => i !== idx))}
                    title={t("platformCountryTaxRules.params.action.removeRow")}
                  >
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button
        type="button"
        className="rounded border border-border px-3 py-1 text-sm"
        onClick={() =>
          onChange([...rows, { i: nextMarginalIndex(rows), pct: 0, min: 0, max: null }])
        }
      >
        + {t("platformCountryTaxRules.params.action.addBracket")}
      </button>
    </div>
  );
}

function ServiceYearTable({
  rows,
  t,
  onChange,
}: {
  rows: TaxRuleParametersForm["serviceYearRows"];
  t: (key: string) => string;
  onChange: (rows: TaxRuleParametersForm["serviceYearRows"]) => void;
}) {
  function updateRow(idx: number, patch: Partial<TaxRuleParametersForm["serviceYearRows"][0]>) {
    onChange(rows.map((row, i) => (i === idx ? { ...row, ...patch } : row)));
  }

  return (
    <div className="space-y-2">
      <p className="text-xs text-muted">{t("platformCountryTaxRules.params.hint.serviceYears")}</p>
      <div className="overflow-x-auto rounded border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-left text-xs font-medium uppercase text-muted">
            <tr>
              <th className="px-2 py-1.5 w-16">{t("platformCountryTaxRules.params.col.index")}</th>
              <th className="px-2 py-1.5 w-24">{t("platformCountryTaxRules.params.col.pct")}</th>
              <th className="px-2 py-1.5">{t("platformCountryTaxRules.params.col.yearsFrom")}</th>
              <th className="px-2 py-1.5">{t("platformCountryTaxRules.params.col.yearsTo")}</th>
              <th className="px-2 py-1.5 w-12" />
            </tr>
          </thead>
          <tbody>
            {rows.map((row, idx) => (
              <tr key={`${row.i}-${idx}`} className="border-t border-border">
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm font-mono"
                    value={row.i}
                    onChange={(e) => updateRow(idx, { i: Number(e.target.value) || 0 })}
                    min={1}
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.pct}
                    onChange={(e) => updateRow(idx, { pct: Number(e.target.value) || 0 })}
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.lo}
                    onChange={(e) => updateRow(idx, { lo: Number(e.target.value) || 0 })}
                    min={0}
                    step={1}
                  />
                </td>
                <td className="px-2 py-1">
                  <input
                    type="number"
                    className="w-full rounded border border-border bg-background px-2 py-1 text-sm"
                    value={row.hi ?? ""}
                    placeholder={t("platformCountryTaxRules.params.openEnded")}
                    onChange={(e) => {
                      const v = e.target.value.trim();
                      updateRow(idx, { hi: v === "" ? null : Number(v) });
                    }}
                    min={0}
                    step={1}
                  />
                </td>
                <td className="px-2 py-1 text-center">
                  <button
                    type="button"
                    className="text-muted hover:text-destructive disabled:opacity-30"
                    disabled={rows.length <= 1}
                    onClick={() => onChange(rows.filter((_, i) => i !== idx))}
                  >
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button
        type="button"
        className="rounded border border-border px-3 py-1 text-sm"
        onClick={() =>
          onChange([...rows, { i: nextServiceYearIndex(rows), pct: 0, lo: 0, hi: null }])
        }
      >
        + {t("platformCountryTaxRules.params.action.addRow")}
      </button>
    </div>
  );
}
