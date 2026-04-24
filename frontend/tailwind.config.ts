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
        accent: "var(--color-accent)",
        "accent-foreground": "var(--color-accent-foreground)",
      },
      ringOffsetColor: {
        background: "var(--color-bg)",
      },
      borderRadius: {
        md: "var(--radius-md)",
      },
      boxShadow: {
        sm: "var(--shadow-sm)",
      },
    },
  },
  plugins: [],
} satisfies Config;
