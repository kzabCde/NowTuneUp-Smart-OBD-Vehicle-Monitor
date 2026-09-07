import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Features",
  description: "Explore NowTuneUp live telemetry, user-created vehicle profiles, Vehicle Intelligence, Time Slip, premium motion, adapter health and local-first OBD-II tools.",
};

const pillars = [
  {
    number: "01",
    eyebrow: "Monitor",
    title: "Realtime values, scheduled around what is visible.",
    description: "Demand-based polling prioritizes the PIDs required by the current screen instead of continuously asking the adapter for everything it supports.",
    chips: ["RPM", "Speed", "Coolant", "Voltage", "Engine load", "Throttle"],
    rows: [["Dashboard demand", "Active"], ["Polling profile", "Adaptive"], ["Unsupported PID", "Unavailable"]],
  },
  {
    number: "02",
    eyebrow: "Diagnose",
    title: "Read vehicle health without turning diagnostics into a destructive flow.",
    description: "Vehicle Intelligence combines identity, readiness and multiple DTC classes in one read-only workspace, with explicit confirmation kept for DTC clearing.",
    chips: ["VIN", "Stored DTC", "Pending DTC", "Permanent DTC", "Readiness", "Freeze frame"],
    rows: [["VIN", "Mode 09"], ["DTC classes", "03 · 07 · 0A"], ["Normal scan", "Read-only"]],
  },
  {
    number: "03",
    eyebrow: "Measure",
    title: "Give vehicle-speed data priority when the run starts.",
    description: "Time Slip switches the scheduler into a speed-priority mode for OBD-based acceleration and distance timing, while presentation animation stays separate from measurement timestamps.",
    chips: ["0–60 km/h", "0–100 km/h", "1/4 mile", "1/2 mile", "1 mile"],
    rows: [["Source", "Vehicle speed PID"], ["Scheduler", "Speed priority"], ["Storage", "Active vehicle history"]],
  },
  {
    number: "04",
    eyebrow: "Understand",
    title: "Know when the adapter link needs to slow down.",
    description: "Adapter health watches rolling latency, success rate, command throughput and recovery activity, then recommends a more conservative pace when the link weakens.",
    chips: ["Latency", "Commands/sec", "Success rate", "Adaptive pacing", "Session report"],
    rows: [["Health grade", "Good / Fair / Poor"], ["Recovery", "Soft first"], ["Recommendation", "Fast / Balanced / Stable"]],
  },
];

export default function Features() {
  return (
    <>
      <section className="page-hero">
        <div className="shell">
          <div className="flex flex-wrap gap-2"><span className="chip"><span className="status-dot" aria-hidden="true" /> v1.14.0</span><span className="chip">User-owned vehicles</span><span className="chip">Premium motion</span></div>
          <p className="eyebrow mt-8">Product capabilities</p>
          <h1 className="page-title mt-4">Built around the four jobs that matter on the road.</h1>
          <p className="page-lede mt-7">NowTuneUp keeps live data, diagnostics, performance timing and connection health separate enough to understand — while vehicle profiles and motion now follow one consistent local-first product flow.</p>
          <div className="mt-8 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            {pillars.map((pillar) => (
              <div key={pillar.eyebrow} className="technical-card p-4">
                <span className="feature-index">{pillar.number}</span>
                <p className="mt-5 font-black tracking-[-0.03em]">{pillar.eyebrow}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5">
          {pillars.map((pillar, index) => (
            <article key={pillar.eyebrow} className="panel overflow-hidden">
              <div className={`grid gap-0 lg:grid-cols-2 ${index % 2 ? "lg:[&>*:first-child]:order-2" : ""}`}>
                <div className="p-6 md:p-9">
                  <div className="flex items-center gap-3"><span className="feature-index">{pillar.number}</span><span className="eyebrow">{pillar.eyebrow}</span></div>
                  <h2 className="mt-7 text-3xl font-black tracking-[-0.048em] md:text-5xl">{pillar.title}</h2>
                  <p className="muted mt-5 max-w-2xl leading-7">{pillar.description}</p>
                  <div className="mt-7 flex flex-wrap gap-2">{pillar.chips.map((chip) => <span key={chip} className="chip">{chip}</span>)}</div>
                </div>

                <div className="telemetry-grid border-t border-white/8 bg-black/15 p-5 lg:border-l lg:border-t-0 md:p-7">
                  <div className="technical-card px-5">
                    {pillar.rows.map(([key, value]) => (
                      <div key={key} className="spec-row"><span className="spec-key">{key}</span><span className="spec-value">{value}</span></div>
                    ))}
                  </div>
                  <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <div className="status-panel"><div><p className="data-label">Behavior</p><p className="mt-1 font-bold">Automatic where possible</p></div><span className="status-good font-black">✓</span></div>
                    <div className="status-panel"><div><p className="data-label">Missing data</p><p className="mt-1 font-bold">Unavailable, not zero</p></div><span className="text-cyan-300 font-black">—</span></div>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-2">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Vehicle Profiles · v1.14.0</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Every saved vehicle starts with you.</h2>
            <p className="muted mt-5 max-w-3xl leading-7">Fresh installations start with zero vehicle profiles and zero demo data. VIN detection can still support diagnostics, but it never silently creates a saved vehicle profile.</p>
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              <div className="technical-card p-5"><p className="data-label">Fresh install</p><p className="data-value mt-3 text-xl">0 vehicles</p></div>
              <div className="technical-card p-5"><p className="data-label">Profile source</p><p className="data-value mt-3 text-xl">User-created only</p></div>
            </div>
            <div className="mt-5 flex flex-wrap gap-2"><span className="chip">Create</span><span className="chip">Edit</span><span className="chip">Delete</span><span className="chip">Duplicate</span><span className="chip">Select active</span></div>
          </article>

          <article className="panel telemetry-grid p-7 md:p-9">
            <p className="eyebrow">Premium automotive motion</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Motion that explains state without changing the data.</h2>
            <p className="muted mt-5 leading-7">The N + tachometer visual language now extends from the launcher and splash into connection, navigation and Time Slip presentation. Gauge smoothing remains presentation-only and never changes ECU values or run timing.</p>
            <div className="mt-7 technical-card px-5">
              <div className="spec-row"><span className="spec-key">UI transitions</span><span className="spec-value">150–300 ms</span></div>
              <div className="spec-row"><span className="spec-key">Telemetry</span><span className="spec-value">Measurement unchanged</span></div>
              <div className="spec-row"><span className="spec-key">Accessibility</span><span className="spec-value">Reduce Motion aware</span></div>
            </div>
          </article>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1.15fr_.85fr]">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Turbo Pressure v2</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Calculated boost only when the inputs deserve trust.</h2>
            <p className="muted mt-5 max-w-3xl leading-7">Turbo Pressure combines MAP and barometric pressure, uses RPM for key-on/engine-off baseline logic and suppresses the result when source data is stale or low quality.</p>
            <div className="mt-8 grid gap-3 sm:grid-cols-3">
              <div className="technical-card p-5"><p className="data-label">Input 01</p><p className="data-value mt-3 text-xl">MAP</p></div>
              <div className="technical-card p-5"><p className="data-label">Input 02</p><p className="data-value mt-3 text-xl">BARO</p></div>
              <div className="technical-card p-5"><p className="data-label">Gate</p><p className="data-value mt-3 text-xl">RPM + freshness</p></div>
            </div>
          </article>

          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Local-first</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Core OBD use without an account.</h2>
            <p className="muted mt-5 leading-7">Realtime monitoring, user-created vehicle profiles and diagnostic session reports are designed around local device storage rather than a required cloud backend.</p>
            <div className="mt-7 technical-card px-5">
              <div className="spec-row"><span className="spec-key">Account</span><span className="spec-value">Not required</span></div>
              <div className="spec-row"><span className="spec-key">Live telemetry</span><span className="spec-value">Local</span></div>
              <div className="spec-row"><span className="spec-key">Profiles</span><span className="spec-value">Explicit user creation</span></div>
            </div>
          </article>
        </div>
      </section>

      <section className="shell section-space">
        <div className="panel flex flex-col justify-between gap-7 p-7 md:flex-row md:items-center md:p-9">
          <div className="section-heading"><p className="eyebrow">Next step</p><h2 className="text-3xl font-black tracking-[-0.045em] md:text-4xl">Create your vehicle, then match the adapter path.</h2></div>
          <div className="page-actions"><Link href="/supported-devices" className="button secondary">Compatibility</Link><Link href="/download" className="button">Download 1.14.0</Link></div>
        </div>
      </section>
    </>
  );
}
