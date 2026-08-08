import type { Metadata } from "next";
import "./globals.css";
import { Header } from "@/components/layout/Header";
import { Footer } from "@/components/layout/Footer";

const site = process.env.NEXT_PUBLIC_SITE_URL ?? "https://nowtuneup.com";

export const metadata: Metadata = {
  metadataBase: new URL(site),
  title: {
    default: "NowTuneUp | OBD-II Telemetry, Diagnostics & Performance",
    template: "%s | NowTuneUp",
  },
  description: "Local-first Android OBD-II telemetry, diagnostics, performance timing and vehicle intelligence through compatible Bluetooth or USB ELM327 adapters.",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    title: "NowTuneUp",
    description: "Know what your car is doing in real time — without the cloud.",
    url: site,
  },
  twitter: {
    card: "summary_large_image",
    title: "NowTuneUp",
    description: "Local-first OBD-II telemetry, diagnostics and performance tools for Android.",
  },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Header />
        <main>{children}</main>
        <Footer />
      </body>
    </html>
  );
}
