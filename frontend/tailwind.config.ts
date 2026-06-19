import type { Config } from "tailwindcss";

export default {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "var(--color-bg)",
        foreground: "var(--color-text)",
        surface: "var(--color-surface)",
        muted: "var(--color-muted)",
        border: "var(--color-border)",
        primary: "var(--color-primary)",
        "primary-foreground": "var(--color-primary-foreground)",
        "primary-strong": "var(--color-primary-strong)",
        accent: "var(--color-accent)",
        "accent-foreground": "var(--color-accent-foreground)",
        "accent-soft": "var(--color-accent-soft)",
        destructive: "var(--color-destructive)",
        "destructive-foreground": "var(--color-destructive-foreground)",
        "destructive-soft": "var(--color-destructive-soft)",
        "destructive-border": "var(--color-destructive-border)",
      },
      ringOffsetColor: {
        background: "var(--color-bg)",
      },
      borderRadius: {
        md: "var(--radius-md)",
        lg: "var(--radius-lg)",
      },
      boxShadow: {
        sm: "var(--shadow-sm)",
        md: "var(--shadow-md)",
        glass: "var(--shadow-glass)",
      },
      keyframes: {
        "progress-indeterminate": {
          "0%": { transform: "translateX(-100%)" },
          "100%": { transform: "translateX(400%)" },
        },
      },
      animation: {
        "progress-indeterminate": "progress-indeterminate 1.4s ease-in-out infinite",
      },
    },
  },
  plugins: [],
} satisfies Config;
