import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Compatibility",
  description: "Check Android, ELM327 Bluetooth/USB and vehicle requirements for NowTuneUp.",
};

const usbDrivers = ["FTDI FT232", "Silicon Labs CP210x", "WCH CH340 / CH341", "CDC ACM", "Prolific PL2303"];

const requirements = [
  ["01", "Android", "Android 8.0+", "Bluetooth Classic for wireless adapters. USB Host / OTG for wired adapters."],
  ["02", "Adapter", "ELM327-compatible", "Firmware quality, buffers and response rate matter more than the version printed on the case."],
  ["03", "Vehicle", "OBD-II compliant", "The ECU decides which standard PIDs and diagnostic modes are actually exposed."],
];

export default function Devices() {
  return (
    <>
      <section className="page-hero">
        <div className="shell">
          <p className="eyebrow">Compatibility</p>
          <h1 className="page-title mt-4">Three links in the chain need to agree.</h1>
          <p className="page-lede mt-7">Android, adapter and vehicle all affect what NowTuneUp can read. The app supports Bluetooth Classic and compatible USB serial paths, but no ELM327 label can guarantee identical hardware behavior.</p>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="grid gap-3 lg:grid-cols-3">
            {requirements.map(([number, eyebrow, title, description]) => (
              <article key={eyebrow} className="flow-step">
                <span className="feature-index">{number}</span>
                <p className="eyebrow mt-6">{eyebrow}</p>
                <strong>{title}</strong>
                <p>{description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="section-heading">
            <p className="eyebrow">Choose a connection path</p>
            <h2 className="section-title">Bluetooth for convenience. USB for a wired link.</h2>
            <p className="section-copy">Both paths still depend on the adapter firmware and the vehicle ECU. Only one OBD app should own the adapter connection at a time.</p>
          </div>

          <div className="mt-10 grid gap-5 lg:grid-cols-2">
            <article className="panel p-7 md:p-9">
              <div className="flex items-center justify-between gap-4"><p className="eyebrow">Bluetooth Classic</p><span className="chip">SPP / RFCOMM</span></div>
              <h3 className="mt-5 text-4xl font-black tracking-[-0.05em]">Pair in Android first.</h3>
              <p className="muted mt-5 leading-7">Pair the ELM327 device from Android settings, close other OBD apps, then select the adapter in NowTuneUp. Weak clones may need Balanced or Stable pacing.</p>
              <div className="mt-7 technical-card px-5">
                <div className="spec-row"><span className="spec-key">Pairing</span><span className="spec-value">Android system</span></div>
                <div className="spec-row"><span className="spec-key">Transport</span><span className="spec-value">Bluetooth Classic SPP</span></div>
                <div className="spec-row"><span className="spec-key">Exclusive owner</span><span className="spec-value">One OBD app</span></div>
              </div>
            </article>

            <article className="panel p-7 md:p-9">
              <div className="flex items-center justify-between gap-4"><p className="eyebrow">USB</p><span className="chip">USB Host / OTG</span></div>
              <h3 className="mt-5 text-4xl font-black tracking-[-0.05em]">Use a supported serial chipset.</h3>
              <p className="muted mt-5 leading-7">Android must support USB Host / OTG and grant NowTuneUp access to the USB device when prompted.</p>
              <div className="mt-7 grid gap-2 sm:grid-cols-2">
                {usbDrivers.map(driver => <div key={driver} className="status-panel"><span className="font-semibold">{driver}</span><span className="text-cyan-300 text-xs font-black">PATH</span></div>)}
              </div>
            </article>
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-5 lg:grid-cols-[1.05fr_.95fr]">
          <article className="panel p-7 md:p-9">
            <p className="eyebrow">Before buying an adapter</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em] md:text-5xl">Do not choose by “ELM327 v1.5” alone.</h2>
            <p className="muted mt-5 leading-7">Clone hardware can differ in firmware, PCB quality, buffers and command handling even when listings use the same version label. Prefer adapters with clear chipset details and credible stability feedback.</p>
            <div className="mt-7 grid gap-3">
              {["Check that the adapter uses Bluetooth Classic, not BLE-only, for the wireless path.", "For USB, confirm the serial chipset is in a supported family.", "Expect PID availability to vary by vehicle even with a good adapter.", "Use Adapter Self-Test if live values feel delayed or unstable."].map(item => (
                <div key={item} className="status-panel"><span className="leading-6">{item}</span><span className="status-good font-black">✓</span></div>
              ))}
            </div>
          </article>

          <article className="panel telemetry-grid p-7 md:p-9">
            <p className="eyebrow">Missing data policy</p>
            <h2 className="mt-4 text-4xl font-black tracking-[-0.05em]">Unavailable is better than misleading.</h2>
            <p className="muted mt-5 leading-7">NowTuneUp is designed to distinguish unsupported or stale data from a real numerical zero.</p>
            <div className="mt-7 technical-card px-5">
              <div className="spec-row"><span className="spec-key">Supported PID</span><span className="spec-value status-good">Live value</span></div>
              <div className="spec-row"><span className="spec-key">Unsupported PID</span><span className="spec-value">Not supported</span></div>
              <div className="spec-row"><span className="spec-key">Stale derived data</span><span className="spec-value">Unavailable</span></div>
            </div>
          </article>
        </div>
      </section>

      <section className="shell section-space">
        <div className="panel flex flex-col justify-between gap-7 p-7 md:flex-row md:items-center md:p-9">
          <div><p className="eyebrow">Compatible path ready?</p><h2 className="mt-3 text-3xl font-black tracking-[-0.045em]">Install the current production APK.</h2></div>
          <div className="page-actions"><Link href="/install-guide" className="button secondary">Installation guide</Link><Link href="/download" className="button">Download NowTuneUp</Link></div>
        </div>
      </section>
    </>
  );
}
