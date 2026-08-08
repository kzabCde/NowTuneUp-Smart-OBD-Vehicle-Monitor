import type { Metadata } from "next";
import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard, bytes } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = {
  title: "Download Android APK",
  description: "Download the latest official signed NowTuneUp Android APK and verify its checksum.",
};

const steps = [
  ["01", "Download", "Get the official APK directly from NowTuneUp."],
  ["02", "Allow installation", "Android may ask permission for your browser or file manager to install apps."],
  ["03", "Install", "Open the downloaded APK and review Android's installation prompt."],
  ["04", "Connect", "Pair a Bluetooth ELM327 adapter or connect a supported USB adapter, then open NowTuneUp."],
];

export default function Download() {
  return (
    <>
      <section className="shell section-space">
        <div className="flex flex-wrap gap-2"><span className="chip"><span className="status-dot" aria-hidden="true" /> Production</span><span className="chip">Signed APK</span><span className="chip">Android 8+</span></div>
        <p className="eyebrow mt-8">Official Android download</p>
        <h1 className="mt-4 max-w-5xl text-5xl font-black leading-[.96] tracking-[-0.055em] md:text-7xl">Download NowTuneUp {localRelease.version}</h1>
        <p className="muted mt-6 max-w-3xl text-lg leading-8">Install the current production build directly on Android. The checksum below matches the APK bundled with this website release.</p>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <ReleaseCard release={localRelease} />
          <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="panel-soft p-5"><dt className="text-xs uppercase tracking-[.14em] text-slate-500">Version</dt><dd className="mt-2 text-xl font-black">{localRelease.version}</dd></div>
            <div className="panel-soft p-5"><dt className="text-xs uppercase tracking-[.14em] text-slate-500">Build</dt><dd className="mt-2 text-xl font-black">{localRelease.versionCode}</dd></div>
            <div className="panel-soft p-5"><dt className="text-xs uppercase tracking-[.14em] text-slate-500">File size</dt><dd className="mt-2 text-xl font-black">{bytes(localRelease.apkSize)}</dd></div>
            <div className="panel-soft p-5"><dt className="text-xs uppercase tracking-[.14em] text-slate-500">Minimum</dt><dd className="mt-2 text-xl font-black">{localRelease.minimumAndroid}+</dd></div>
          </dl>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <p className="eyebrow">Install in four steps</p>
          <h2 className="mt-4 text-4xl font-black tracking-[-0.045em] md:text-6xl">From download to live data.</h2>
          <div className="mt-9 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {steps.map(([number, title, description]) => (
              <article key={number} className="panel p-6">
                <span className="text-xs font-black text-cyan-300">{number}</span>
                <h3 className="mt-8 text-xl font-black">{title}</h3>
                <p className="muted mt-3 leading-7">{description}</p>
              </article>
            ))}
          </div>
          <div className="mt-7"><Link href="/install-guide" className="button secondary">Full installation guide</Link></div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1fr_.85fr]">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Verify your download</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">SHA-256 checksum</h2>
            <p className="muted mt-4 leading-7">Use the production checksum to verify that the APK has not changed after download.</p>
            <div className="code-value mt-6">{localRelease.sha256}</div>
            <p className="muted mt-4 text-sm">File: {localRelease.apkName}</p>
          </article>

          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Before connecting</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em]">Use the right adapter.</h2>
            <p className="muted mt-4 leading-7">Bluetooth Classic and USB ELM327-compatible adapters are supported paths, but clone quality and vehicle PID availability vary.</p>
            <div className="mt-7 flex flex-wrap gap-3"><Link href="/supported-devices" className="button secondary">Compatibility</Link><Link href="/releases" className="button secondary">Release history</Link></div>
          </article>
        </div>
      </section>
    </>
  );
}
