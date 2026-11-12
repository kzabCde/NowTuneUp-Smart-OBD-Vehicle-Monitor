import type { Metadata } from "next";
import Link from "next/link";
import { localRelease } from "@/lib/local-release";

export const metadata: Metadata = {
  title: "Features",
  description: "Explore NowTuneUp v1.18 Motorsport Performance Suite: Custom HUD templates, driver-configured RPM shift lights, timestamped OBD telemetry with CSV export, Time Slip history, diagnostics and adapter health.",
};

const pillars = [
  {
    number: "01",
    eyebrow: "Monitor",
    title: "Make a motorsport cockpit from your own OBD signals.",
    description: "Choose an optional Daily Drive, Performance or Diagnostics HUD template or design your own 12×24 canvas. Add compact gauges, configure multi-stage RPM shift lights using driver-selected thresholds, and record bounded raw telemetry with original timestamps and CSV export.",
    chips: ["HUD templates", "RPM shift lights", "Compact gauges", "Live chart", "CSV export", "Portrait / Landscape"],
    rows: [["Canvas", "12 × 24"], ["HUD presets", "Optional"], ["Telemetry export", "CSV · local"]],
  },
  {
    number: "02",
    eyebrow: "Diagnose",
    title: "Read vehicle health without turning diagnostics into a destructive flow.",
    description: "Vehicle Intelligence combines identity, readiness and multiple DTC classes in one read-only workspace. The HUD distinguishes disconnected, stale and unsupported values, and exposes last-scan DTC availability rather than inventing a status.",
    chips: ["VIN", "DTC availability", "Stored DTC", "Pending DTC", "Readiness", "Freeze frame"],
    rows: [["VIN", "Mode 09"], ["DTC classes", "03 · 07 · 0A"], ["Normal scan", "Read-only"]],
  },
  {
    number: "03",
    eyebrow: "Measure",
    title: "Turn each Time Slip run into data you can inspect and compare.",
    description: "Time Slip adds an exact 0–60 mph target, searchable and sortable history, compatible run comparisons with split deltas, and available recorded RPM / throttle samples. Distance and acceleration remain estimates calculated from OBD speed, not GPS-certified results.",
    chips: ["0–60 mph", "0–100 km/h", "1/4 mile", "Split delta", "Run history", "OBD speed graphs"],
    rows: [["Timing source", "OBD speed"], ["History", "Search · Group · Sort"], ["Estimates", "Not GPS certified"]],
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
          <div className="flex flex-wrap gap-2"><span className="chip"><span className="status-dot" aria-hidden="true" /> v{localRelease.version}</span><span className="chip">Motorsport Suite</span><span className="chip">HUD presets · Shift Lights · CSV</span></div>
          <p className="eyebrow mt-8">Product capabilities</p>
          <h1 className="page-title mt-4">Built around the four jobs that matter on the road.</h1>
          <p className="page-lede mt-7">NowTuneUp v1.18.0 expands the custom HUD with optional motorsport templates and user-configured shift lights, while adding timestamped telemetry recording and more detailed, comparable Time Slip results. Core OBD monitoring stays local-first.</p>
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
            <p className="eyebrow">Vehicle Profiles · v{localRelease.version}</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Every saved vehicle starts with you.</h2>
            <p className="muted mt-5 max-w-3xl leading-7">Fresh installations start with zero vehicle profiles and zero demo data. VIN detection can still support diagnostics, but it never silently creates a saved vehicle profile.</p>
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              <div className="technical-card p-5"><p className="data-label">Fresh install</p><p className="data-value mt-3 text-xl">0 vehicles</p></div>
              <div className="technical-card p-5"><p className="data-label">Profile source</p><p className="data-value mt-3 text-xl">User-created only</p></div>
            </div>
            <div className="mt-5 flex flex-wrap gap-2"><span className="chip">Create</span><span className="chip">Edit</span><span className="chip">Delete</span><span className="chip">Duplicate</span><span className="chip">Select active</span></div>
          </article>

          <article className="panel telemetry-grid p-7 md:p-9">
            <p className="eyebrow">Motorsport HUD · v{localRelease.version}</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Your own cockpit, ready for the drive.</h2>
            <p className="muted mt-5 leading-7">Start from a Daily Drive, Performance or Diagnostics template, or arrange every widget from scratch. Configure shift-light RPM thresholds yourself; no manufacturer redline is assumed. Profiles can retain separate orientation layouts and be transferred as JSON.</p>
            <div className="mt-7 technical-card px-5">
              <div className="spec-row"><span className="spec-key">Canvas</span><span className="spec-value">12 × 24 · Freeform</span></div>
              <div className="spec-row"><span className="spec-key">Widgets</span><span className="spec-value">Gauge · Chart · Shift Light</span></div>
              <div className="spec-row"><span className="spec-key">Profile tools</span><span className="spec-value">Undo · Redo · Import · Export</span></div>
            </div>
          </article>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1.15fr_.85fr]">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Time Slip · v{localRelease.version}</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Inspect the run, not just the finish time.</h2>
            <p className="muted mt-5 max-w-3xl leading-7">Explore exact 0–60 mph targets, searchable history and matching-run comparisons with split deltas. Timestamped RPM and throttle appear where recorded; speed-derived acceleration and distance are estimates and are not GPS-certified measurements.</p>
            <div className="mt-8 grid gap-3 sm:grid-cols-3">
              <div className="technical-card p-5"><p className="data-label">Run metric</p><p className="data-value mt-3 text-xl">0–60 mph</p></div>
              <div className="technical-card p-5"><p className="data-label">Compare</p><p className="data-value mt-3 text-xl">Split delta</p></div>
              <div className="technical-card p-5"><p className="data-label">Local export</p><p className="data-value mt-3 text-xl">CSV</p></div>
            </div>
          </article>

          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Local-first</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Core OBD use without an account.</h2>
            <p className="muted mt-5 leading-7">Realtime monitoring, user-created vehicle profiles, Time Slip history and diagnostic session reports are designed around local device storage rather than a required cloud backend.</p>
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
          <div className="section-heading"><p className="eyebrow">Next step</p><h2 className="text-3xl font-black tracking-[-0.045em] md:text-4xl">Create your vehicle, then inspect the data.</h2></div>
          <div className="page-actions"><Link href="/supported-devices" className="button secondary">Compatibility</Link><Link href="/download" className="button">Download {localRelease.version}</Link></div>
        </div>
      </section>
    </>
  );
}
