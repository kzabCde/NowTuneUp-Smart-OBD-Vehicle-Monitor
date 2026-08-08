import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

const proof = [
  ["RPM", "2,841", "rpm"],
  ["Speed", "86", "km/h"],
  ["Turbo", "+0.72", "bar"],
  ["Coolant", "91", "°C"],
];

const features = [
  ["Live telemetry", "See the values that matter without polling unsupported data.", "RPM · Speed · Temperatures · Load"],
  ["Turbo Pressure", "Derived MAP and BARO pressure with quality gating and KOEO baseline logic.", "MAP · BARO · RPM"],
  ["Time Slip", "OBD-based acceleration timing with a dedicated speed-priority sampling mode.", "0–60 · 0–100 · 60–100 · 1/4 mile"],
  ["Vehicle Intelligence", "Read VIN, readiness state and multiple DTC classes from one diagnostic workspace.", "VIN · Readiness · DTC · Freeze frame"],
];

const checks = ["Stored DTC", "Pending DTC", "Permanent DTC", "Readiness"];

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

      <section className="shell section-space grid items-center gap-14 lg:grid-cols-[1.02fr_.98fr]">
        <div>
          <div className="flex flex-wrap gap-2">
            <span className="chip"><span className="status-dot" aria-hidden="true" /> NowTuneUp {localRelease.version}</span>
            <span className="chip">Android 8+</span>
            <span className="chip">Bluetooth + USB</span>
            <span className="chip">Local-first</span>
          </div>
          <p className="eyebrow mt-8">OBD-II vehicle intelligence</p>
          <h1 className="mt-4 max-w-4xl text-5xl font-black leading-[.94] tracking-[-0.06em] md:text-7xl lg:text-[5.25rem]">
            Know what your car is doing. <span className="text-cyan-300">In real time.</span>
          </h1>
          <p className="muted mt-7 max-w-2xl text-lg leading-8 md:text-xl">
            Live telemetry, read-only diagnostics, performance timing and vehicle intelligence — directly on your Android device, without requiring a cloud account.
          </p>
          <div className="mt-9 flex flex-wrap gap-3">
            <Link href="/download" className="button mobile-cta">Download NowTuneUp {localRelease.version}</Link>
            <Link href="/supported-devices" className="button secondary mobile-cta">Check compatibility</Link>
          </div>
          <p className="muted mt-4 text-sm">Signed APK · {localRelease.minimumAndroid}+ · Vehicle PID support varies by ECU</p>
        </div>

        <div className="relative">
          <div className="absolute -inset-8 -z-10 rounded-full bg-cyan-300/[0.04] blur-3xl" />
          <div className="phone-shell">
            <div className="phone-screen telemetry-grid">
              <div className="flex items-center justify-between border-b border-white/8 px-5 py-4 text-xs">
                <div>
                  <p className="font-bold tracking-wide">NOWTUNEUP</p>
                  <p className="mt-1 text-slate-500">Live dashboard</p>
                </div>
                <span className="chip"><span className="status-dot" aria-hidden="true" /> OBD GOOD</span>
              </div>
              <div className="grid grid-cols-2 gap-2 p-3">
                {proof.map(([label, value, unit]) => (
                  <div key={label} className="rounded-2xl border border-white/8 bg-black/35 p-4">
                    <p className="text-xs font-bold uppercase tracking-[.14em] text-slate-500">{label}</p>
                    <p className="mt-4 text-3xl font-black tracking-[-0.05em] text-white">{value}</p>
                    <p className="mt-1 text-xs text-cyan-300">{unit}</p>
                  </div>
                ))}
              </div>
              <div className="mx-3 mb-3 rounded-2xl border border-white/8 bg-black/40 p-4">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold">Adapter health</span>
                  <span className="text-emerald-300">Stable</span>
                </div>
                <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-white/8"><div className="h-full w-[82%] rounded-full bg-cyan-300" /></div>
                <div className="mt-3 flex justify-between text-[11px] text-slate-500"><span>Adaptive polling</span><span>Balanced</span></div>
              </div>
            </div>
          </div>
          <div className="panel-soft absolute -left-4 top-24 hidden w-40 p-4 lg:block">
            <p className="text-xs text-slate-500">Vehicle</p><p className="mt-1 font-bold">ECU ready</p><p className="mt-2 text-xs text-emerald-300">● Connected</p>
          </div>
          <div className="panel-soft absolute -right-4 bottom-20 hidden w-44 p-4 lg:block">
            <p className="text-xs text-slate-500">Performance</p><p className="mt-1 font-bold">0–100 km/h</p><p className="mt-2 text-xs text-cyan-300">Speed-priority mode</p>
          </div>
        </div>
      </section>

      <section id="product" className="section-rule">
        <div className="shell section-space">
          <p className="eyebrow">One connection. More context.</p>
          <div className="mt-4 flex flex-col justify-between gap-5 md:flex-row md:items-end">
            <h2 className="max-w-3xl text-4xl font-black tracking-[-0.045em] md:text-6xl">Monitor. Diagnose. Measure. Understand.</h2>
            <Link href="/features" className="text-sm font-bold text-cyan-300">Explore every feature →</Link>
          </div>

          <div className="mt-10 grid gap-4 lg:grid-cols-2">
            {features.map(([title, description, meta], index) => (
              <article key={title} className={`panel card-hover p-6 md:p-8 ${index === 0 || index === 3 ? "lg:min-h-72" : ""}`}>
                <div className="flex items-start justify-between gap-4">
                  <span className="text-xs font-black text-cyan-300">0{index + 1}</span>
                  <span className="chip">{meta}</span>
                </div>
                <h3 className="mt-12 text-2xl font-black tracking-[-0.035em] md:text-3xl">{title}</h3>
                <p className="muted mt-3 max-w-xl leading-7">{description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-10 lg:grid-cols-[.9fr_1.1fr] lg:items-center">
          <div>
            <p className="eyebrow">Vehicle Intelligence</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.045em] md:text-6xl">More than live gauges.</h2>
            <p className="muted mt-6 max-w-xl text-lg leading-8">
              NowTuneUp 1.9.0 brings identity and health information into one read-only diagnostic flow: VIN, MIL status, readiness, stored faults, pending faults, permanent faults and freeze-frame trigger data where the vehicle supports them.
            </p>
            <Link href="/features" className="button secondary mt-8">Vehicle Intelligence details</Link>
          </div>

          <div className="panel overflow-hidden">
            <div className="flex items-center justify-between border-b border-white/8 p-5 md:p-6">
              <div><p className="eyebrow">Vehicle health</p><p className="mt-2 font-bold">OBD-II diagnostic overview</p></div>
              <span className="chip"><span className="status-dot" aria-hidden="true" /> Read-only</span>
            </div>
            <div className="grid gap-3 p-4 md:grid-cols-2 md:p-6">
              <div className="panel-soft p-5 md:col-span-2">
                <p className="text-xs uppercase tracking-[.16em] text-slate-500">Vehicle identity</p>
                <div className="mt-3 flex flex-wrap justify-between gap-3"><strong>VIN detected when supported</strong><span className="text-sm text-cyan-300">Mode 09 · PID 02</span></div>
              </div>
              {checks.map((item) => (
                <div key={item} className="panel-soft flex items-center justify-between p-5">
                  <span className="font-semibold">{item}</span><span className="text-sm text-emerald-300">Ready</span>
                </div>
              ))}
              <div className="panel-soft p-5 md:col-span-2">
                <div className="flex justify-between gap-4"><span className="font-semibold">Adapter self-test</span><span className="text-sm text-cyan-300">Identity · Voltage · Core PIDs</span></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="panel telemetry-grid overflow-hidden p-6 md:p-10">
            <div className="grid gap-10 lg:grid-cols-[1fr_.85fr] lg:items-center">
              <div>
                <span className="chip">PERFORMANCE MODE</span>
                <p className="eyebrow mt-7">Time Slip</p>
                <h2 className="mt-3 text-4xl font-black tracking-[-0.05em] md:text-6xl">Measure the run, not the guess.</h2>
                <p className="muted mt-5 max-w-2xl text-lg leading-8">Use vehicle speed data with a dedicated OBD sampling schedule for simple acceleration timing on a closed course or private property.</p>
                <div className="mt-7 flex flex-wrap gap-2"><span className="chip">0–60 km/h</span><span className="chip">0–100 km/h</span><span className="chip">60–100 km/h</span><span className="chip">1/4 mile</span></div>
              </div>
              <div className="rounded-3xl border border-white/10 bg-black/45 p-6 md:p-8">
                <div className="flex justify-between text-xs font-bold uppercase tracking-[.16em] text-slate-500"><span>0–100 km/h</span><span>Ready</span></div>
                <p className="metric mt-10">0.00<span className="ml-2 text-xl text-cyan-300">s</span></p>
                <div className="mt-9 h-2 overflow-hidden rounded-full bg-white/8"><div className="h-full w-[14%] rounded-full bg-cyan-300" /></div>
                <div className="mt-4 flex justify-between text-xs text-slate-500"><span>Speed PID ready</span><span>Waiting for launch</span></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="text-center">
            <p className="eyebrow">Connection</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.045em] md:text-6xl">Plug. Pair. Drive.</h2>
            <p className="muted mx-auto mt-5 max-w-2xl text-lg leading-8">Use a compatible ELM327 adapter over Bluetooth Classic or USB. NowTuneUp discovers supported Mode 01 data before starting the realtime session.</p>
          </div>
          <div className="mt-10 grid gap-3 md:grid-cols-5 md:items-center">
            {[
              ["01", "Vehicle", "OBD-II port"],
              ["→", "", ""],
              ["02", "ELM327", "Bluetooth / USB"],
              ["→", "", ""],
              ["03", "NowTuneUp", "Android 8+"],
            ].map(([num, title, meta], index) => index === 1 || index === 3 ? (
              <div key={index} className="hidden text-center text-3xl text-slate-700 md:block">→</div>
            ) : (
              <div key={title} className="panel-soft p-6 text-center">
                <span className="text-xs font-black text-cyan-300">{num}</span><p className="mt-5 text-xl font-black">{title}</p><p className="muted mt-2 text-sm">{meta}</p>
              </div>
            ))}
          </div>
          <div className="mt-8 text-center"><Link href="/supported-devices" className="button secondary">Compatibility guide</Link></div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-6 lg:grid-cols-2">
          <div className="panel p-7 md:p-9">
            <p className="eyebrow">Local-first</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">Your vehicle data stays with you.</h2>
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {["No account required", "No cloud backend for monitoring", "Local vehicle profiles", "Local diagnostic session report"].map(item => <div key={item} className="panel-soft p-4 font-semibold">✓ {item}</div>)}
            </div>
          </div>
          <div className="panel p-7 md:p-9">
            <p className="eyebrow">Compatibility</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">Built around standard OBD-II.</h2>
            <p className="muted mt-5 leading-7">Adapter speed, firmware quality and vehicle PID support vary. NowTuneUp treats unsupported values as unavailable instead of inventing a zero reading.</p>
            <div className="mt-7 flex flex-wrap gap-2"><span className="chip">ELM327-compatible</span><span className="chip">Bluetooth Classic</span><span className="chip">USB serial</span></div>
            <Link href="/supported-devices" className="mt-8 inline-block text-sm font-bold text-cyan-300">See hardware requirements →</Link>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <ReleaseCard release={localRelease} />
        </div>
      </section>

      <section className="shell mb-10">
        <div className="panel-soft flex flex-col justify-between gap-4 p-5 text-sm md:flex-row md:items-center">
          <p><strong>Safety first.</strong> <span className="muted">Live data is not a mechanical diagnosis. Park safely before interacting with diagnostics, and use Time Slip only in a safe controlled environment.</span></p>
          <Link href="/terms" className="shrink-0 font-bold text-cyan-300">Safety terms →</Link>
        </div>
      </section>
    </>
  );
}
