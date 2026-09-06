import Link from "next/link";

const product = [["Features", "/features"], ["Compatibility", "/supported-devices"], ["Download", "/download"], ["Releases", "/releases"]];
const support = [["Install guide", "/install-guide"], ["Privacy", "/privacy"], ["Terms & safety", "/terms"]];

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="shell grid gap-10 py-12 md:grid-cols-[1.3fr_.7fr_.7fr] md:py-14">
        <div>
          <p className="footer-kicker">OBD-II / Android</p>
          <div className="mt-5 flex items-center gap-3">
            <span className="logo-mark" aria-hidden="true">N</span>
            <div>
              <strong className="block text-lg tracking-[-0.035em]">NowTuneUp</strong>
              <span className="muted mt-1 block text-xs">Local-first vehicle intelligence</span>
            </div>
          </div>
          <p className="muted mt-5 max-w-md leading-7">
            Live OBD-II telemetry, diagnostics and performance tools for Android through compatible Bluetooth Classic or USB ELM327 hardware.
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <span className="chip">Android 8+</span>
            <span className="chip">Bluetooth + USB</span>
            <span className="chip">No account</span>
          </div>
        </div>

        <nav aria-label="Product links">
          <p className="text-sm font-black text-white">Product</p>
          <div className="mt-4 grid gap-3 text-sm">
            {product.map(([label, href]) => <Link key={href} href={href} className="footer-link">{label}</Link>)}
          </div>
        </nav>

        <nav aria-label="Support links">
          <p className="text-sm font-black text-white">Support</p>
          <div className="mt-4 grid gap-3 text-sm">
            {support.map(([label, href]) => <Link key={href} href={href} className="footer-link">{label}</Link>)}
            <Link href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor" target="_blank" rel="noreferrer" className="footer-link">GitHub</Link>
          </div>
        </nav>
      </div>

      <div className="footer-rail">
        <div className="shell flex flex-wrap justify-between gap-3 py-5">
          <span>NOWTUNEUP // MONITOR · DIAGNOSE · MEASURE · UNDERSTAND</span>
          <span>Data availability depends on ECU and adapter support.</span>
        </div>
      </div>
    </footer>
  );
}
