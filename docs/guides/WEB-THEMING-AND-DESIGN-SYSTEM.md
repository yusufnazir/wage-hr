# Web theming and design system

**Scope:** Browser web apps (e.g. Next.js). **Goal:** Ship **light + dark** from day one, look **modern**, and make **color tweaks or full theme swaps** a **data / token change**, not a hunt through hundreds of components.

Copy this file into **`docs/guides/`** in each product repo. Architecture summarizes *what* you chose; **this guide** is the *how* for implementation.

---

## 1. Layering (do this in order)

| Layer | Responsibility | Swapping colors | Swapping “entire theme” |
|-------|----------------|-----------------|-------------------------|
| **Primitives** | Raw palette (brand greens, neutrals, semantic danger) | Edit one palette file or CSS variables block | Replace primitive set (e.g. new brand kit) |
| **Semantic tokens** | `--color-bg`, `--color-text`, `--color-border`, `--color-primary`, `--radius-md`, `--font-sans` | Components **never** use primitives directly | Point semantic layer to a different primitive map |
| **Components** | Use **only** semantic tokens / Tailwind theme keys mapped to vars | Almost no component edits | Same — components stay stable |

**Rule:** UI code references **semantic** names only (`bg-background`, `text-foreground`, `border-border`, or `var(--color-surface)`). **Never** `#1a1a1a` or `green-600` scattered in JSX except in the **token definition** layer.

---

## 2. Light and dark mode

- Support **explicit user choice**: light, dark, **system** (follow `prefers-color-scheme`).
- Persist choice (cookie or localStorage — align with auth/cookie strategy in architecture).
- Apply mode with **one attribute** on the document root, e.g. `class="dark"` on `<html>` or `data-theme="dark"`, so CSS can scope all tokens.
- **Avoid flash of wrong theme:** inline script or SSR that sets class from cookie **before** paint (framework-specific; document the chosen approach in the repo).

---

## 3. Technology patterns (pick one stack; stay consistent)

**Recommended for Next.js + “modern fast” UI:**

- **CSS custom properties** for semantic tokens (single `globals` or `tokens.css` imported once).
- **Tailwind** `theme.extend` mapping colors/radii/fonts to `var(--…)` so utilities stay ergonomic.
- **`next-themes`** (or equivalent) for class on `<html>` + persistence.

**Alternative:** CSS-in-JS token provider — still keep the same **primitive → semantic → component** separation.

Document the **chosen stack** in architecture and in the repo `README` snippet for new devs.

---

## 4. Where files live (suggested layout)

Keep token and theme wiring **obvious and grep-friendly**:

```
styles/
  tokens-primitives.css    # optional: brand palette values
  tokens-semantic.css        # maps primitives → semantic vars for light
  tokens-semantic-dark.css   # overrides for .dark (or data-theme=dark)
  globals.css                # imports + base element styles
components/
  theme/
    ThemeProvider.tsx        # next-themes + attribute class
    ThemeToggle.tsx          # sun/moon control for auth shell + later app chrome
```

Feature screens **must not** define one-off colors; they consume shared utilities / vars.

---

## 5. Scaffold vs later features

| Phase | Theming expectation |
|-------|---------------------|
| **Scaffold** | Wire **ThemeProvider**, **semantic tokens**, **light/dark**, and a **polished minimal auth shell** (login + post-login redirect pages). Still **not** a full product UI — but it should **not** look like unstyled HTML. |
| **Features (Prompt 5+)** | Reuse tokens only; add **layout** and **components** that match the established system. |

The scaffold **non-goal** is still “no full business UI” — it is **not** “no CSS.”

---

## 6. Full rebrand or new color scheme

1. **Swap primitives** (or swap the file that defines them).
2. Adjust **semantic** mapping only if meaning changes (e.g. “primary” is now purple).
3. Run visual smoke on **auth + one dense screen** (tables, forms, errors).

Optional: support **`data-brand="acme"`** (or similar) if white-label is a requirement — second axis beside light/dark; document in architecture before implementing.

---

## 7. “Looks modern” baseline (non-designer bar)

Without mandating a specific art direction, scaffolds should include:

- Sensible **typography scale** and **spacing** rhythm (8px grid or Tailwind defaults mapped to tokens).
- **Radius**, **shadow**, and **focus rings** on interactive elements (a11y).
- **Empty / loading / error** states for the auth shell that are not raw browser defaults.

Product-specific **brand illustration / marketing polish** can still wait for dedicated design passes.

---

## 8. Where this is decided in your methodology

| Artifact | What to capture |
|----------|-----------------|
| **Architecture contract / architecture output** | Stack (Tailwind? shadcn?), **must** have light+dark, and whether white-label / multi-brand exists. |
| **This guide** | Implementation rules everyone follows. |
| **Scaffold** | First working **token pipeline + auth UI** that proves the system. |
| **Feature module docs** | Per-screen layout and behavior — **not** per-screen hex colors unless exceptional. |

If architecture and this guide **disagree**, update **architecture** first, then implementation.
