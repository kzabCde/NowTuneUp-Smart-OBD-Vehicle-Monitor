import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Features",
  description: "Explore NowTuneUp live telemetry, Vehicle Intelligence, Time Slip, adapter health and local-first OBD-II tools.",
};

const pillars = [
  {
    number: "01",
    eyebrow: "Monitor",
    title: "Realtime values without the noise.",
    description: "Demand-based polling prioritizes the values required by the active dashboard and avoids wasting adapter bandwidth on data you are not using.",
    chips: ["RPM", "Speed", "Coolant", "Voltage", "Engine load", "Throttle"],
  },
  {
    number: "02",
    eyebrow: "Diagnose",
    title: "Vehicle health in one read-only workspace.",
    description: "Inspect identity, readiness and fault state without turning normal diagnostics into a destructive workflow.",
    chips: ["VIN", "Stored DTC", "Pending DTC", "Permanent DTC", "Readiness", "Freeze frame"],
  },
  {
    number: "03",
    eyebrow: "Measure",
    title: "Performance timing from vehicle speed data.",
    description: "Time Slip temporarily switches to a speed-priority schedule so the adapter focuses on the data needed for an acceleration run.",
    chips: ["0–60 km/h", "0–100 km/h", "60–100 km/h", "1/4 mile"],
  },
  {
    number: "04",
    eyebrow: "Understand",
    title: "Know whether the connection can be trusted.",
    description: "Adapter health tracks rolling latency, command rate, success rate and recovery activity, then adjusts pacing when the link becomes unreliable.",
    chips: ["Latency", "Commands/sec", "Success rate", "Adaptive pacing", "Session report"],
  },
];

export default function Features() {
  return (
    <>
      <section className="shell section-space">
        <p className="eyebrow">Product capabilities</p>
        <h1 className="mt-4 max-w-5xl text-5xl font-black leading-[.96] tracking-[-0.055em] md:text-7xl">The useful parts of an OBD tool, without the dashboard clutter.</h1>
        <p className="muted mt-6 max-w-3xl text-lg leading-8">NowTuneUp is designed around four jobs: monitor live vehicle data, inspect read-only diagnostics, measure performance and understand whether the adapter connection is healthy enough to trust.</p>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-4">
          {pillars.map((pillar) => (
            <article key={pillar.eyebrow} className="panel grid gap-8 p-6 md:p-8 lg:grid-cols-[.82fr_1.18fr] lg:items-center">
              <div>
                <div className="flex items-center gap-3"><span className="text-xs font-black text-cyan-300">{pillar.number}</span><span className="eyebrow">{pillar.eyebrow}</span></div>
                <h2 className="mt-5 text-3xl font-black tracking-[-0.045em] md:text-5xl">{pillar.title}</h2>
                <p className="muted mt-5 max-w-2xl leading-7">{pillar.description}</p>
              </div>
              <div className="panel-soft p-5 md:p-6">
                <div className="flex flex-wrap gap-2">{pillar.chips.map((chip) => <span key={chip} className="chip">{chip}</span>)}</div>
                <div className="mt-7 grid gap-3 sm:grid-cols-2">
                  <div className="rounded-2xl border border-white/8 bg-black/30 p-5"><p className="text-xs uppercase tracking-[.15em] text-slate-500">Behavior</p><p className="mt-3 font-bold">Automatic where possible</p></div>
                  <div className="rounded-2xl border border-white/8 bg-black/30 p-5"><p className="text-xs uppercase tracking-[.15em] text-slate-500">Data</p><p className="mt-3 font-bold">Unavailable instead of fake zero</p></div>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-3">
          <article className="panel p-7 lg:col-span-2">
            <p className="eyebrow">Turbo Pressure v2</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em] md:text-5xl">Calculated pressure with quality gating.</h2>
            <p className="muted mt-5 max-w-3xl leading-7">Turbo Pressure combines MAP and barometric pressure, uses RPM for key-on/engine-off baseline logic and suppresses the result when source data is stale or low quality.</p>
            <div className="mt-7 flex flex-wrap gap-2"><span className="chip">MAP</span><span className="chip">BARO</span><span className="chip">RPM dependency</span><span className="chip">Good / delayed / unavailable</span></div>
          </article>
          <article className="panel p-7">
            <p className="eyebrow">Local-first</p>
            <h2 className="mt-4 text-3xl font-black tracking-[-0.04em]">No account needed.</h2>
            <p className="muted mt-5 leading-7">Vehicle monitoring and profile data are designed to work locally on the Android device rather than requiring a cloud account for the core OBD session.</p>
          </article>
        </div>
      </section>

      <section className="shell section-space">
        <div className="panel flex flex-col justify-between gap-6 p-7 md:flex-row md:items-center md:p-9">
          <div><p className="eyebrow">Ready to connect?</p><h2 className="mt-3 text-3xl font-black tracking-[-0.04em]">Check your adapter before installing.</h2></div>
          <div className="flex flex-wrap gap-3"><Link href="/supported-devices" className="button secondary">Compatibility</Link><Link href="/download" className="button">Download APK</Link></div>
        </div>
      </section>
    </>
  );
}
