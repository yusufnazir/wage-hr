import type { Metadata } from "next";
import Script from "next/script";
import { AppThemeProvider } from "@/components/theme/AppThemeProvider";
import "./globals.css";

const themeBlockingScript = `(function(){try{var v=localStorage.getItem("theme");var dark=false;if(v==="dark")dark=true;else if(v==="light")dark=false;else if(window.matchMedia("(prefers-color-scheme: dark)").matches)dark=true;var r=document.documentElement;if(dark)r.classList.add("dark");else r.classList.remove("dark");}catch(e){}})();`;

export const metadata: Metadata = {
  title: "Wage Payroll",
  description: "Multi-tenant payroll — secure documents, roles, and subscriptions.",
  icons: {
    icon: [{ url: "/wage/favicon.png", type: "image/png" }],
    apple: [{ url: "/wage/favicon.png", type: "image/png" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen antialiased">
        <Script id="theme-preinit" strategy="beforeInteractive" dangerouslySetInnerHTML={{ __html: themeBlockingScript }} />
        <AppThemeProvider>{children}</AppThemeProvider>
      </body>
    </html>
  );
}
