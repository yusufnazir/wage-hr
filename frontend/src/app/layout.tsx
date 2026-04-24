import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import Script from "next/script";
import { AppThemeProvider } from "@/components/theme/AppThemeProvider";
import "./globals.css";

const themeBlockingScript = `(function(){try{var v=localStorage.getItem("theme");var dark=false;if(v==="dark")dark=true;else if(v==="light")dark=false;else if(window.matchMedia("(prefers-color-scheme: dark)").matches)dark=true;var r=document.documentElement;if(dark)r.classList.add("dark");else r.classList.remove("dark");}catch(e){}})();`;

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "wage-payroll",
  description: "Multi-tenant payroll SaaS — scaffold",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${geistSans.variable} ${geistMono.variable} min-h-screen antialiased`}>
        <Script id="theme-preinit" strategy="beforeInteractive" dangerouslySetInnerHTML={{ __html: themeBlockingScript }} />
        <AppThemeProvider>{children}</AppThemeProvider>
      </body>
    </html>
  );
}
