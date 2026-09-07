import type { Metadata } from "next";
import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard, bytes } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = {
  title: "Download Android APK",
  description: "Download the latest official signed NowTuneUp Android APK and verify its checksum.",
};

const steps = [
  ["01", "Download", "Get the official signed APK from the current GitHub production release."],
  ["02", "Allow installation", "Android may ask your browser or file manager for permission to install apps."],
  ["03", "Install", "Open the APK and review Android's installation confirmation."],
  ["04", "Create your vehicle", "Create and select your own vehicle profile before using vehicle-specific features."],
];

export default function Download() {
  return (
    <>
      <section className="page-hero">
        <div className="shell grid gap-10 lg:grid-cols-[.9fr_1.1fr] lg:items-center">
          <div>
            <div className="flex flex-wrap gap-2"><span className="chip"><span className="status-dot" aria-hidden="true" /> Production</span><span className="chip">Signed APK</span><span className="chip">Android 8+</span></div>
            <p className="eyebrow mt-8">Official Android download</p>
            <h1 className="page-title mt-4">NowTuneUp {localRelease.version}</h1>
            <p className="page-lede mt-7">Install the canonical production APK published with the official NowTuneUp release. Build facts and SHA-256 verification stay visible so you can confirm exactly what you downloaded.</p>
          </div>
          <ReleaseCard release={localRelease} compact />
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="section-heading">
            <p className="eyebrow">Production facts</p>
            <h2 className="section-title">Know the build before you install it.</h2>
          </div>
          <dl className="mt-9 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="technical-card p-5"><dt className="data-label">Version</dt><dd className="data-value mt-4 text-2xl">{localRelease.version}</dd></div>
            <div className="technical-card p-5"><dt className="data-label">Build code</dt><dd className="data-value mt-4 text-2xl">{localRelease.versionCode}</dd></div>
            <div className="technical-card p-5"><dt className="data-label">APK size</dt><dd className="data-value mt-4 text-2xl">{bytes(localRelease.apkSize)}</dd></div>
            <div className="technical-card p-5"><dt className="data-label">Minimum Android</dt><dd className="data-value mt-4 text-2xl">{localRelease.minimumAndroid}+</dd></div>
          </dl>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="section-heading">
            <p className="eyebrow">Install journey</p>
            <h2 className="section-title">Four steps from APK to your vehicle.</h2>
            <p className="section-copy">Fresh installations intentionally start with zero vehicle profiles and zero fake vehicle data.</p>
          </div>
          <div className="mt-9 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
            {steps.map(([number, title, description]) => (
              <article key={number} className="flow-step">
                <span className="feature-index">{number}</span>
                <strong>{title}</strong>
                <p>{description}</p>
              </article>
            ))}
          </div>
          <Link href="/install-guide" className="accent-link mt-5">Open the full installation guide →</Link>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1.15fr_.85fr]">
          <article className="panel p-7 md:p-9">
            <div className="flex flex-wrap items-center justify-between gap-3"><p className="eyebrow">Integrity verification</p><span className="chip">SHA-256</span></div>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Verify the APK after download.</h2>
            <p className="muted mt-5 leading-7">The value below is the checksum published for the signed production APK.</p>
            <div className="code-value mt-7">{localRelease.sha256}</div>
            <div className="mt-5 technical-card px-5">
              <div className="spec-row"><span className="spec-key">File</span><span className="spec-value">{localRelease.apkName}</span></div>
              <div className="spec-row"><span className="spec-key">Size</span><span className="spec-value">{bytes(localRelease.apkSize)}</span></div>
              <div className="spec-row"><span className="spec-key">Build</span><span className="spec-value">{localRelease.versionCode}</span></div>
            </div>
          </article>

          <article className="panel telemetry-grid p-7 md:p-9">
            <p className="eyebrow">Before connecting</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Create a vehicle, then match the adapter path.</h2>
            <p className="muted mt-5 leading-7">NowTuneUp never creates a default vehicle silently. After creating your own profile, Bluetooth Classic and supported USB serial adapters are both valid connection paths.</p>
            <div className="mt-7 grid gap-3">
              <div className="status-panel"><div><p className="data-label">Wireless</p><p className="mt-1 font-bold">Bluetooth Classic SPP</p></div><span className="status-good font-black">READY</span></div>
              <div className="status-panel"><div><p className="data-label">Wired</p><p className="mt-1 font-bold">Supported USB serial</p></div><span className="status-good font-black">READY</span></div>
            </div>
            <div className="page-actions mt-7"><Link href="/supported-devices" className="button secondary">Compatibility</Link><Link href="/releases" className="button secondary">Release history</Link></div>
          </article>
        </div>
      </section>
    </>
  );
}
