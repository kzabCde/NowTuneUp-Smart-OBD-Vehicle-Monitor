import Link from "next/link";

const product = [["Features", "/features"], ["Compatibility", "/supported-devices"], ["Download", "/download"], ["Releases", "/releases"]];
const support = [["Install guide", "/install-guide"], ["Privacy", "/privacy"], ["Terms & safety", "/terms"]];

export function Footer() {
  return (
    <footer className="mt-24 border-t border-white/8 bg-black/10">
      <div className="shell grid gap-10 py-12 md:grid-cols-[1.25fr_.75fr_.75fr]">
        <div>
          <div className="flex items-center gap-3">
            <span className="logo-mark" aria-hidden="true">N</span>
            <strong className="text-lg tracking-[-0.03em]">NowTuneUp</strong>
          </div>
          <p className="muted mt-4 max-w-md leading-7">
            Local-first OBD-II telemetry, diagnostics and performance tools for Android. Connect through compatible Bluetooth Classic or USB ELM327 hardware.
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <span className="chip">Android 8+</span>
            <span className="chip">Bluetooth + USB</span>
            <span className="chip">No account</span>
          </div>
        </div>

        <nav aria-label="Product links">
          <p className="text-sm font-bold text-white">Product</p>
          <div className="mt-4 grid gap-3 text-sm text-slate-400">
            {product.map(([label, href]) => <Link key={href} href={href} className="hover:text-white">{label}</Link>)}
          </div>
        </nav>

        <nav aria-label="Support links">
          <p className="text-sm font-bold text-white">Support</p>
          <div className="mt-4 grid gap-3 text-sm text-slate-400">
            {support.map(([label, href]) => <Link key={href} href={href} className="hover:text-white">{label}</Link>)}
            <Link href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor" target="_blank" rel="noreferrer" className="hover:text-white">GitHub</Link>
          </div>
        </nav>
      </div>
      <div className="shell flex flex-wrap justify-between gap-3 border-t border-white/8 py-5 text-xs text-slate-500">
        <span>NowTuneUp · Monitor · Diagnose · Measure · Understand</span>
        <span>Vehicle data availability depends on ECU and adapter support.</span>
      </div>
    </footer>
  );
}
