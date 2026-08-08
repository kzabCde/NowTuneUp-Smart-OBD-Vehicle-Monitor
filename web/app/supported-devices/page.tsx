import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Compatibility",
  description: "Check Android, ELM327 Bluetooth/USB and vehicle requirements for NowTuneUp.",
};

const usbDrivers = ["FTDI FT232", "Silicon Labs CP210x", "WCH CH340 / CH341", "CDC ACM", "Prolific PL2303"];

export default function Devices() {
  return (
    <>
      <section className="shell section-space">
        <p className="eyebrow">Compatibility</p>
        <h1 className="mt-4 max-w-5xl text-5xl font-black leading-[.96] tracking-[-0.055em] md:text-7xl">Three things need to agree: Android, adapter and vehicle.</h1>
        <p className="muted mt-6 max-w-3xl text-lg leading-8">NowTuneUp works with compatible ELM327-style hardware over Bluetooth Classic or supported USB serial connections. Actual PID availability and response rate are determined by the adapter and ECU.</p>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-4 lg:grid-cols-3">
          <article className="panel p-7">
            <span className="text-xs font-black text-cyan-300">01</span>
            <p className="eyebrow mt-5">Android</p>
            <h2 className="mt-3 text-3xl font-black tracking-[-0.04em]">Android 8.0+</h2>
            <p className="muted mt-4 leading-7">Bluetooth Classic is used for compatible wireless adapters. USB connections require Android USB Host / OTG support.</p>
            <div className="mt-6 flex flex-wrap gap-2"><span className="chip">Android 8+</span><span className="chip">Bluetooth Classic</span><span className="chip">USB Host / OTG</span></div>
          </article>

          <article className="panel p-7">
            <span className="text-xs font-black text-cyan-300">02</span>
            <p className="eyebrow mt-5">Adapter</p>
            <h2 className="mt-3 text-3xl font-black tracking-[-0.04em]">ELM327-compatible</h2>
            <p className="muted mt-4 leading-7">Firmware quality matters more than the label on the case. Slow or buffer-limited clones may require a more conservative polling rate.</p>
            <div className="mt-6 flex flex-wrap gap-2"><span className="chip">SPP / RFCOMM</span><span className="chip">USB serial</span><span className="chip">Adaptive pacing</span></div>
          </article>

          <article className="panel p-7">
            <span className="text-xs font-black text-cyan-300">03</span>
            <p className="eyebrow mt-5">Vehicle</p>
            <h2 className="mt-3 text-3xl font-black tracking-[-0.04em]">OBD-II compliant</h2>
            <p className="muted mt-4 leading-7">Not every ECU exposes every standard PID. VIN, BARO, readiness and some diagnostic modes may be unavailable on particular vehicles.</p>
            <div className="mt-6 flex flex-wrap gap-2"><span className="chip">Mode 01</span><span className="chip">Mode 03 / 07 / 0A</span><span className="chip">Mode 09 when supported</span></div>
          </article>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1.05fr_.95fr]">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Bluetooth adapters</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">Pair first, then let NowTuneUp connect.</h2>
            <p className="muted mt-5 leading-7">For Bluetooth Classic adapters, pair the ELM327 device in Android first. Only one app should actively own the adapter connection at a time, so close other OBD apps before connecting with NowTuneUp.</p>
            <div className="mt-7 rounded-2xl border border-white/8 bg-black/30 p-5">
              <p className="text-xs uppercase tracking-[.15em] text-slate-500">Recommended behavior</p>
              <div className="mt-4 grid gap-3 text-sm"><p>✓ Prefer stable PIC-based or well-reviewed adapters.</p><p>✓ Let adaptive polling slow down weak hardware.</p><p>✓ Use Adapter Self-Test when realtime data feels delayed.</p></div>
            </div>
          </article>

          <article className="panel p-7 md:p-9">
            <p className="eyebrow">USB adapters</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em]">Supported serial families</h2>
            <p className="muted mt-4 leading-7">USB support depends on the serial chipset and the Android device&apos;s USB Host capability.</p>
            <div className="mt-6 grid gap-3">{usbDrivers.map(driver => <div key={driver} className="panel-soft flex items-center justify-between p-4"><span>{driver}</span><span className="text-sm text-cyan-300">Supported path</span></div>)}</div>
          </article>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="panel p-7 md:p-9">
            <div className="grid gap-8 lg:grid-cols-[1fr_.9fr] lg:items-center">
              <div>
                <p className="eyebrow">What NowTuneUp does when data is missing</p>
                <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">Unavailable is better than misleading.</h2>
                <p className="muted mt-5 leading-7">If a PID is unsupported or source data becomes stale, NowTuneUp is designed to show it as unavailable rather than silently presenting a fabricated zero.</p>
              </div>
              <div className="panel-soft p-6">
                <div className="grid gap-4">
                  <div className="flex justify-between gap-4"><span className="muted">Supported PID</span><strong className="text-emerald-300">Live value</strong></div>
                  <div className="h-px bg-white/8" />
                  <div className="flex justify-between gap-4"><span className="muted">Unsupported PID</span><strong>Not supported</strong></div>
                  <div className="h-px bg-white/8" />
                  <div className="flex justify-between gap-4"><span className="muted">Stale derived data</span><strong>Unavailable</strong></div>
                </div>
              </div>
            </div>
          </div>
          <div className="mt-7 flex flex-wrap justify-center gap-3"><Link href="/install-guide" className="button secondary">Installation guide</Link><Link href="/download" className="button">Download NowTuneUp</Link></div>
        </div>
      </section>
    </>
  );
}
