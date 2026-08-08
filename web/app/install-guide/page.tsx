import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Installation guide" };

const steps = [
  "Download the APK from the official NowTuneUp website.",
  "Open the downloaded APK in your browser or file manager.",
  "Allow installation from that app when Android asks.",
  "Review Android's security warning and confirm installation.",
  "Open NowTuneUp after installation completes.",
  "For Bluetooth Classic, pair the ELM327 adapter in Android. For USB, connect the supported adapter through USB OTG.",
  "Close other OBD apps so only NowTuneUp owns the adapter connection.",
  "Turn the vehicle ignition on in a safe, ventilated location.",
  "Open the connection screen, choose the adapter and connect to the ECU.",
];

export default function Guide() {
  return (
    <>
      <section className="shell section-space">
        <p className="eyebrow">Installation</p>
        <h1 className="mt-4 max-w-4xl text-5xl font-black leading-[.96] tracking-[-0.055em] md:text-7xl">Install, connect and verify the adapter.</h1>
        <p className="muted mt-6 max-w-3xl text-lg leading-8">The APK installation is the same for Bluetooth and USB. Only the adapter connection step changes.</p>
      </section>
      <section className="section-rule">
        <div className="shell section-space">
          <ol className="grid gap-3">
            {steps.map((step, index) => (
              <li className="panel flex gap-5 p-5 md:items-center md:p-6" key={step}>
                <b className="text-2xl text-cyan-300">{String(index + 1).padStart(2, "0")}</b>
                <span className="leading-7">{step}</span>
              </li>
            ))}
          </ol>
          <div className="mt-7 rounded-2xl border border-amber-300/20 bg-amber-300/[0.05] p-5 text-amber-100">Never install a repackaged APK from an unofficial mirror. Compare the SHA-256 checksum with the value published on the official download page.</div>
          <div className="mt-7 flex flex-wrap gap-3"><Link href="/download" className="button">Download APK</Link><Link href="/supported-devices" className="button secondary">Compatibility</Link></div>
        </div>
      </section>
    </>
  );
}
