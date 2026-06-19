"use client";

import dynamic from "next/dynamic";
import { useTheme } from "next-themes";
import { useCallback, useEffect, useRef } from "react";
import type { Monaco } from "@monaco-editor/react";
import type { editor, Position } from "monaco-editor";

import { WAGE_COMPONENT_FORMULA_REFS } from "@/lib/wage-component-formula";

const Editor = dynamic(() => import("@monaco-editor/react").then((m) => m.default), { ssr: false });

const FORMULA_LANG = "payrollFormula";

let languageRegistered = false;

function ensureLanguage(monaco: Monaco) {
	if (languageRegistered) {
		return;
	}
	monaco.languages.register({ id: FORMULA_LANG });
	monaco.languages.setMonarchTokensProvider(FORMULA_LANG, {
		tokenizer: {
			root: [
				[/\b(?:compensation|transaction|definition)\.[a-z_.]+\b/, "identifier"],
				[/[0-9]+(?:\.[0-9]+)?/, "number"],
				[/[*+\-\/()]/, "operator"],
				[/\s+/, "white"],
				[/./, "source"],
			],
		},
	});
	languageRegistered = true;
}

type Props = {
	value: string;
	onChange: (v: string | null) => void;
};

export function WageComponentFormulaMonaco({ value, onChange }: Props) {
	const { resolvedTheme } = useTheme();
	const disposableRef = useRef<{ dispose: () => void } | null>(null);
	const monacoTheme = resolvedTheme === "dark" ? "vs-dark" : "vs";

	const beforeMount = useCallback((monaco: Monaco) => {
		ensureLanguage(monaco);
		disposableRef.current?.dispose();
		disposableRef.current = monaco.languages.registerCompletionItemProvider(FORMULA_LANG, {
			triggerCharacters: [".", "_", "("],
			provideCompletionItems: (model: editor.ITextModel, position: Position) => {
				const word = model.getWordUntilPosition(position);
				const range = {
					startLineNumber: position.lineNumber,
					endLineNumber: position.lineNumber,
					startColumn: word.startColumn,
					endColumn: position.column,
				};
				const suggestions = [
					...WAGE_COMPONENT_FORMULA_REFS.map((label) => ({
						label,
						kind: monaco.languages.CompletionItemKind.Field,
						insertText: label,
						range,
					})),
					...["*", "+", "-", "/", "(", ")"].map((op) => ({
						label: op,
						kind: monaco.languages.CompletionItemKind.Operator,
						insertText: op,
						range,
					})),
				];
				return { suggestions };
			},
		});
	}, []);

	useEffect(() => () => disposableRef.current?.dispose(), []);

	return (
		<div className="overflow-hidden rounded border border-border">
			<Editor
				height="168px"
				language={FORMULA_LANG}
				theme={monacoTheme}
				value={value}
				beforeMount={beforeMount}
				onChange={(v) => onChange(v && v.trim() !== "" ? v : null)}
				options={{
					minimap: { enabled: false },
					fontSize: 13,
					wordWrap: "on",
					scrollBeyondLastLine: false,
					tabSize: 2,
					automaticLayout: true,
					lineNumbers: "off",
				}}
			/>
		</div>
	);
}
