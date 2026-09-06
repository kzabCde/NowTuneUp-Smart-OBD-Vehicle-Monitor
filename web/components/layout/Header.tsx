import Link from "next/link";

const links = [
  ["Features", "/features"],
  ["Compatibility", "/supported-devices"],
  ["Releases", "/releases"],
  ["Install", "/install-guide"],
];

function Brand() {
  return (
    <Link href="/" className="brand-lockup" aria-label="NowTuneUp home">
      <span className="logo-mark" aria-hidden="true">N</span>
      <span className="brand-copy">
        <span className="brand-name">NowTuneUp</span>
        <span className="brand-kicker">Vehicle telemetry</span>
      </span>
    </Link>
  );
}

export function Header() {
  return (
    <header className="site-header">
      <div className="shell flex min-h-18 items-center justify-between gap-4">
        <Brand />

        <nav aria-label="Main navigation" className="desktop-nav flex items-center gap-7">
          {links.map(([label, href]) => (
            <Link key={href} href={href} className="nav-link">{label}</Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <Link
            href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor"
            className="button ghost desktop-only"
            target="_blank"
            rel="noreferrer"
          >
            GitHub
          </Link>
          <Link href="/download" className="button desktop-only">Download APK</Link>

          <details className="mobile-nav relative">
            <summary className="button secondary" aria-label="Open navigation">Menu</summary>
            <nav className="mobile-nav-panel" aria-label="Mobile navigation">
              {links.map(([label, href]) => <Link key={href} href={href}>{label}</Link>)}
              <Link href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor" target="_blank" rel="noreferrer">GitHub</Link>
              <Link href="/download" className="button mt-1">Download APK</Link>
            </nav>
          </details>
        </div>
      </div>
    </header>
  );
}
