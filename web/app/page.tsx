import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

const liveValues = [
  ["RPM", "2,841", "rpm"],
  ["Speed", "86", "km/h"],
  ["Turbo", "+0.72", "bar"],
  ["Coolant", "91", "°C"],
];

const capabilities = [
  ["01", "Monitor", "Live telemetry", "Demand-based polling keeps the active dashboard responsive without spending adapter bandwidth on unused PIDs."],
  ["02", "Diagnose", "Vehicle Intelligence", "VIN, readiness, stored, pending and permanent DTCs, plus freeze-frame context where the ECU supports them."],
  ["03", "Measure", "Time Slip", "A dedicated speed-priority mode for 0–60, 0–100, 60–100 km/h and quarter-mile timing."],
  ["04", "Understand", "Adapter health", "Rolling latency, success rate and recovery behavior help NowTuneUp adapt pacing to the connection you actually have."],
];

const checks = [
  ["VIN", "Mode 09 · PID 02"],
  ["Readiness", "Mode 01 · PID 01"],
  ["Stored / Pending / Permanent", "Mode 03 · 07 · 0A"],
  ["Adapter self-test", "Identity · Voltage · Core PIDs"],
];

export default function Home() {
  const product = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "NowTuneUp",
    applicationCategory: "UtilitiesApplication",
    operatingSystem: "Android 8.0+",
    description: "Local-first OBD-II telemetry, diagnostics and performance tools through compatible Bluetooth or USB ELM327 adapters.",
  };

  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(product) }} />

      <section className="page-hero">
        <div className="shell grid gap-12 lg:grid-cols-[1.02fr_.98fr] lg:items-center">
          <div>
            <div className="flex flex-wrap gap-2">
              <span className="chip"><span className="status-dot" aria-hidden="true" /> Production {localRelease.version}</span>
              <span className="chip">Android 8+</span>
              <span className="chip">Bluetooth + USB</span>
              <span className="chip">Local-first</span>
            </div>
            <p className="eyebrow mt-8">OBD-II vehicle intelligence</p>
            <h1 className="page-title mt-4">Your car, translated into <span className="text-cyan-300">live signal.</span></h1>
            <p className="page-lede mt-7">Monitor realtime vehicle data, inspect read-only diagnostics, time performance runs and understand adapter health — directly on Android without requiring a cloud account.</p>
            <div className="page-actions mt-9">
              <Link href="/download" className="button">Download {localRelease.version}</Link>
              <Link href="/supported-devices" className="button secondary">Check compatibility</Link>
            </div>
            <div className="mt-8 grid max-w-2xl grid-cols-2 gap-px overflow-hidden border border-white/8 bg-white/8 sm:grid-cols-4">
              {[['CONNECTION', 'OBD-II'], ['MODE', 'Adaptive'], ['DATA', 'Local'], ['BUILD', String(localRelease.versionCode)]].map(([label, value]) => (
                <div key={label} className="bg-[#0b0d09] px-4 py-3">
                  <p className="data-label">{label}</p>
                  <p className="data-value mt-1 text-sm">{value}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="panel telemetry-grid overflow-hidden">
            <div className="flex items-center justify-between border-b border-white/8 px-5 py-4">
              <div>
                <p className="data-label">Live dashboard</p>
                <p className="mt-1 text-sm font-bold">Vehicle session</p>
              </div>
              <span className="chip"><span className="status-dot" aria-hidden="true" /> OBD GOOD</span>
            </div>
            <div className="grid grid-cols-2 gap-px bg-white/8">
              {liveValues.map(([label, value, unit]) => (
                <div key={label} className="bg-[#0b0d09]/95 p-5 md:p-6">
                  <p className="data-label">{label}</p>
                  <p className="mt-5 text-4xl font-black tracking-[-0.055em] tabular-nums">{value}</p>
                  <p className="mt-1 text-xs font-bold text-cyan-300">{unit}</p>
                </div>
              ))}
            </div>
            <div className="grid gap-3 border-t border-white/8 p-4 sm:grid-cols-2">
              <div className="technical-card p-4">
                <p className="data-label">Adapter health</p>
                <div className="mt-3 flex items-end justify-between gap-4"><strong className="text-xl">Stable</strong><span className="status-good text-sm font-bold">GOOD</span></div>
                <div className="mt-4 h-1 overflow-hidden bg-white/8"><div className="h-full w-[82%] bg-cyan-300" /></div>
              </div>
              <div className="technical-card p-4">
                <p className="data-label">Polling strategy</p>
                <div className="mt-3 flex items-end justify-between gap-4"><strong className="text-xl">Balanced</strong><span className="text-cyan-300 text-sm font-bold">AUTO</span></div>
                <p className="muted mt-3 text-xs">Demand-based · adaptive pacing</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="flex flex-col justify-between gap-6 lg:flex-row lg:items-end">
            <div className="section-heading">
              <p className="eyebrow">Four jobs. One connection.</p>
              <h2 className="section-title">Monitor. Diagnose. Measure. Understand.</h2>
              <p className="section-copy">The interface is organized around what you need from the vehicle, not around a wall of every PID the adapter can possibly request.</p>
            </div>
            <Link href="/features" className="accent-link">Explore all capabilities →</Link>
          </div>

          <div className="mt-10 grid gap-4 md:grid-cols-2">
            {capabilities.map(([number, eyebrow, title, description]) => (
              <article key={eyebrow} className="panel card-hover p-6 md:p-8">
                <div className="flex items-center justify-between gap-4"><span className="feature-index">{number}</span><span className="eyebrow">{eyebrow}</span></div>
                <h3 className="mt-10 text-3xl font-black tracking-[-0.045em]">{title}</h3>
                <p className="muted mt-4 max-w-xl leading-7">{description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-10 lg:grid-cols-[.88fr_1.12fr] lg:items-center">
          <div className="section-heading">
            <p className="eyebrow">Vehicle Intelligence</p>
            <h2 className="section-title">Diagnostics that show context, not just codes.</h2>
            <p className="section-copy">NowTuneUp keeps normal diagnostics read-only and brings identity, readiness, fault classes and adapter context into the same workspace.</p>
            <div className="page-actions mt-3"><Link href="/features" className="button secondary">See diagnostic features</Link></div>
          </div>

          <div className="panel p-5 md:p-7">
            <div className="flex items-center justify-between gap-4 border-b border-white/8 pb-5">
              <div><p className="data-label">Diagnostic overview</p><p className="mt-1 font-bold">Vehicle health workspace</p></div>
              <span className="chip"><span className="status-dot" aria-hidden="true" /> Read-only</span>
            </div>
            <div className="mt-4 grid gap-3">
              {checks.map(([label, detail]) => (
                <div key={label} className="status-panel"><div><p className="font-bold">{label}</p><p className="muted mt-1 text-sm">{detail}</p></div><span className="status-good text-sm font-black">READY</span></div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="panel telemetry-grid overflow-hidden p-6 md:p-10">
            <div className="grid gap-10 lg:grid-cols-[1fr_.82fr] lg:items-center">
              <div className="section-heading">
                <p className="eyebrow">Time Slip · Performance mode</p>
                <h2 className="section-title">Measure the run with the data already on the bus.</h2>
                <p className="section-copy">Time Slip prioritizes the vehicle speed PID during a run. Actual timing precision still depends on ECU update rate and adapter latency, so it is presented as OBD-based performance timing rather than GPS-grade instrumentation.</p>
                <div className="mt-2 flex flex-wrap gap-2"><span className="chip">0–60 km/h</span><span className="chip">0–100 km/h</span><span className="chip">60–100 km/h</span><span className="chip">1/4 mile</span></div>
              </div>
              <div className="technical-card p-6 md:p-8">
                <div className="flex justify-between gap-4"><span className="data-label">0–100 km/h</span><span className="data-label text-cyan-300">READY</span></div>
                <p className="metric mt-10">0.00<span className="ml-2 text-xl text-cyan-300">s</span></p>
                <div className="mt-9 h-1 overflow-hidden bg-white/8"><div className="h-full w-[14%] bg-cyan-300" /></div>
                <div className="mt-4 flex justify-between gap-4 text-xs"><span className="muted">Speed PID ready</span><span className="muted">Waiting for launch</span></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="section-heading mx-auto text-center">
            <p className="eyebrow">Connection path</p>
            <h2 className="section-title">Vehicle → adapter → NowTuneUp.</h2>
            <p className="section-copy mx-auto">Bluetooth Classic and USB are both supported connection paths. NowTuneUp probes supported data before the realtime session begins.</p>
          </div>
          <div className="mt-10 grid gap-3 md:grid-cols-3">
            <div className="flow-step"><span className="feature-index">01</span><strong>Vehicle</strong><p>OBD-II port · ECU data source</p></div>
            <div className="flow-step"><span className="feature-index">02</span><strong>ELM327-compatible</strong><p>Bluetooth Classic or supported USB serial</p></div>
            <div className="flow-step"><span className="feature-index">03</span><strong>NowTuneUp</strong><p>Android 8+ · local vehicle session</p></div>
          </div>
          <div className="mt-7 text-center"><Link href="/supported-devices" className="button secondary">Compatibility guide</Link></div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-2">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Local-first</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Your vehicle data stays with the device.</h2>
            <div className="mt-7 grid gap-2">
              {["No account required for core OBD use", "No cloud backend required for live monitoring", "Vehicle profiles stored locally", "Diagnostic session reports remain local unless you share them"].map(item => <div key={item} className="status-panel"><span className="font-semibold">{item}</span><span className="status-good font-black">✓</span></div>)}
            </div>
          </article>
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Compatibility reality</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Standard protocol. Variable hardware.</h2>
            <p className="muted mt-5 leading-7">ELM327 labels do not guarantee identical firmware quality, buffers or response rates. Vehicle PIDs vary too. NowTuneUp treats missing or stale values as unavailable rather than inventing a zero.</p>
            <div className="mt-7 technical-card px-5">
              <div className="spec-row"><span className="spec-key">Adapter</span><span className="spec-value">ELM327-compatible</span></div>
              <div className="spec-row"><span className="spec-key">Wireless</span><span className="spec-value">Bluetooth Classic</span></div>
              <div className="spec-row"><span className="spec-key">Wired</span><span className="spec-value">USB serial</span></div>
            </div>
            <Link href="/supported-devices" className="accent-link mt-4">Check hardware requirements →</Link>
          </article>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space"><ReleaseCard release={localRelease} /></div>
      </section>

      <section className="shell mb-10">
        <div className="warning-callout flex flex-col justify-between gap-4 p-5 text-sm md:flex-row md:items-center">
          <p><strong>Safety first.</strong> Live data is not a mechanical diagnosis. Park safely before interacting with diagnostics, and use Time Slip only in a safe controlled environment.</p>
          <Link href="/terms" className="shrink-0 font-bold">Safety terms →</Link>
        </div>
      </section>
    </>
  );
}
