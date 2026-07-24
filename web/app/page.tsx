import Link from "next/link";
import Image from "next/image";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

const features = [
  ["Live telemetry", "RPM, speed, temperature, voltage, engine load and throttle at a glance."],
  ["Read-only diagnostics", "Inspect stored diagnostic codes without modifying your ECU."],
  ["Local by design", "No account, cloud backend, or internet connection for vehicle monitoring."],
];

export default function Home() {
  const product = {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "NowTuneUp",
    applicationCategory: "UtilitiesApplication",
    operatingSystem: "Android 8.0+",
    description: "Real-time vehicle monitoring through USB OBD-II",
  };

  return (
    <>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(product) }} />
      <section className="shell grid min-h-[68vh] items-center py-16 lg:grid-cols-[1.1fr_.9fr]">
        <div>
          <p className="eyebrow">Android · USB OBD-II · Local-first</p>
          <h1 className="mt-5 text-5xl font-black tracking-tight md:text-7xl">
            Real-time vehicle monitoring, <span className="text-cyan-300">without the cloud.</span>
          </h1>
          <p className="muted mt-6 max-w-2xl text-lg">
            Monitor RPM, speed, coolant temperature, voltage, engine load, and diagnostic information directly from your Android device.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/download" className="button">Download NowTuneUp</Link>
            <Link href="/install-guide" className="button secondary">Installation guide</Link>
          </div>
        </div>
        <div className="panel relative mt-12 overflow-hidden p-2 lg:mt-0">
          <Image src="/screenshots/dashboard-preview.svg" alt="NowTuneUp dashboard displaying RPM, speed, coolant, voltage, and engine load" width={960} height={540} priority />
        </div>
      </section>
      <section className="shell py-16">
        <p className="eyebrow">Built for the road</p>
        <h2 className="mt-3 text-4xl font-black">Useful insight. Minimal distraction.</h2>
        <div className="grid mt-8 md:grid-cols-3">
          {features.map(([title, description]) => (
            <article className="panel p-6" key={title}>
              <h3 className="text-xl font-bold">{title}</h3>
              <p className="muted mt-3">{description}</p>
            </article>
          ))}
        </div>
      </section>
      <section className="shell py-16">
        <div className="panel p-8 text-center">
          <p className="eyebrow">Simple connection path</p>
          <h2 className="mt-3 text-3xl font-black">Vehicle ECU → OBD-II → USB OTG → NowTuneUp</h2>
          <p className="muted mx-auto mt-4 max-w-2xl">Android 8.0 or later and a USB Host-capable device are required. PID availability depends on your vehicle.</p>
        </div>
      </section>
      <section className="shell py-16"><ReleaseCard release={localRelease} /></section>
      <section className="shell panel p-6"><strong>Safety first.</strong><span className="muted"> NowTuneUp is read-only, but live data is not a mechanical diagnosis. Park safely before interacting with the app.</span></section>
    </>
  );
}
