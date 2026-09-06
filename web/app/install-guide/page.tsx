import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = { title: "Installation guide" };

const steps = [
  ["Install", "Download the APK from the official NowTuneUp website."],
  ["Install", "Open the downloaded APK in your browser or file manager."],
  ["Install", "Allow installation from that app when Android asks."],
  ["Install", "Review Android's security warning and confirm installation."],
  ["Open", "Open NowTuneUp after installation completes."],
  ["Connect", "For Bluetooth Classic, pair the ELM327 adapter in Android. For USB, connect the supported adapter through USB OTG."],
  ["Connect", "Grant NowTuneUp access to the USB device when Android asks; Bluetooth adapters do not need the USB permission step."],
  ["Prepare", "Close other OBD apps, turn the vehicle ignition on in a safe ventilated location, and keep only NowTuneUp connected to the adapter."],
  ["Connect", "Open the connection screen, choose the adapter and connect to the ECU."],
];

export default function Guide() {
  return (
    <>
      <section className="page-hero">
        <div className="shell grid gap-9 lg:grid-cols-[1fr_.72fr] lg:items-end">
          <div>
            <p className="eyebrow">Installation</p>
            <h1 className="page-title mt-4">Install NowTuneUp</h1>
            <p className="page-lede mt-7">Install the APK first, then choose either Bluetooth Classic or USB for the vehicle connection. The Android installation path itself is the same for both.</p>
          </div>
          <div className="technical-card px-5">
            <div className="spec-row"><span className="spec-key">Android</span><span className="spec-value">8.0+</span></div>
            <div className="spec-row"><span className="spec-key">Wireless path</span><span className="spec-value">Bluetooth Classic</span></div>
            <div className="spec-row"><span className="spec-key">Wired path</span><span className="spec-value">USB Host / OTG</span></div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-10 lg:grid-cols-[.68fr_1.32fr]">
          <div className="section-heading lg:sticky lg:top-28 lg:self-start">
            <p className="eyebrow">Nine-step flow</p>
            <h2 className="section-title">APK → adapter → ECU.</h2>
            <p className="section-copy">Follow the sequence in order. USB adds an Android permission prompt; Bluetooth Classic requires pairing in Android first.</p>
          </div>

          <ol className="release-rail grid gap-4">
            {steps.map(([phase, step], index) => (
              <li className="release-dot panel p-5 md:p-6" key={step}>
                <div className="flex gap-5">
                  <b className="feature-index shrink-0">{String(index + 1).padStart(2, "0")}</b>
                  <div>
                    <p className="data-label">{phase}</p>
                    <p className="mt-2 leading-7">{step}</p>
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-2">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Bluetooth Classic</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Pair before opening the vehicle session.</h2>
            <p className="muted mt-5 leading-7">Pair the ELM327 device from Android settings, then make sure other OBD apps are closed before NowTuneUp connects.</p>
            <div className="mt-7 technical-card px-5"><div className="spec-row"><span className="spec-key">Pairing</span><span className="spec-value">Android settings</span></div><div className="spec-row"><span className="spec-key">USB permission</span><span className="spec-value">Not required</span></div></div>
          </article>

          <article className="panel p-7 md:p-9">
            <p className="eyebrow">USB</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Connect through USB Host / OTG.</h2>
            <p className="muted mt-5 leading-7">Attach a supported USB serial adapter and accept Android's device-access prompt when NowTuneUp requests it.</p>
            <div className="mt-7 technical-card px-5"><div className="spec-row"><span className="spec-key">Pairing</span><span className="spec-value">Not required</span></div><div className="spec-row"><span className="spec-key">Device permission</span><span className="spec-value">Required</span></div></div>
          </article>
        </div>
      </section>

      <section className="shell section-space">
        <div className="warning-callout p-5 md:p-6"><strong>Verify the source before installing.</strong><p className="mt-2 leading-7">Never install a repackaged APK from an unofficial mirror. Compare the SHA-256 checksum with the value published on the official download page.</p></div>
        <div className="page-actions mt-7"><Link href="/download" className="button">Download APK</Link><Link href="/supported-devices" className="button secondary">Compatibility</Link></div>
      </section>
    </>
  );
}
