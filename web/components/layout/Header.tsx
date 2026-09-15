import Link from "next/link";

const links = [
  ["Features", "/features"],
  ["Compatibility", "/supported-devices"],
  ["Releases", "/releases"],
  ["Install", "/install-guide"],
];

function Brand() {
  return (
    <Link
      href="/"
      className="brand-lockup min-w-0 shrink"
      aria-label="NowTuneUp home"
    >
      <span className="logo-mark" aria-hidden="true">N</span>
      <span className="brand-copy min-w-0">
        <span className="brand-name whitespace-nowrap">NowTuneUp</span>
        <span className="brand-kicker whitespace-nowrap">Vehicle telemetry</span>
      </span>
    </Link>
  );
}

const mobileLinkClass =
  "flex min-h-11 items-center rounded-lg px-3 py-2 text-sm font-bold text-[#d1d3ca] transition-colors hover:bg-[rgba(199,255,70,0.055)] hover:text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]";

export function Header() {
  return (
    <header className="site-header">
      <div className="shell flex min-h-18 items-center justify-between gap-3 sm:gap-4">
        <Brand />

        <nav
          aria-label="Main navigation"
          className="hidden items-center gap-5 min-[1100px]:flex xl:gap-7"
        >
          {links.map(([label, href]) => (
            <Link key={href} href={href} className="nav-link">{label}</Link>
          ))}
        </nav>

        <div className="hidden shrink-0 items-center gap-2 min-[1100px]:flex">
          <Link
            href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor"
            className="button ghost"
            target="_blank"
            rel="noreferrer"
          >
            GitHub
          </Link>
          <Link href="/download" className="button">Download APK</Link>
        </div>

        <details className="group relative shrink-0 min-[1100px]:hidden">
          <summary
            className="button secondary cursor-pointer list-none select-none px-3 [&::-webkit-details-marker]:hidden"
            aria-label="Open navigation"
          >
            Menu
          </summary>
          <nav
            className="absolute right-0 top-[calc(100%+10px)] z-[60] grid max-h-[calc(100dvh-5.5rem)] w-[min(320px,calc(100vw-24px))] gap-1 overflow-y-auto overscroll-contain rounded-[var(--radius-soft)] border border-[var(--line-strong)] bg-[rgba(17,19,15,0.988)] p-2 shadow-[0_28px_70px_rgba(0,0,0,0.54)]"
            aria-label="Mobile navigation"
          >
            {links.map(([label, href]) => (
              <Link key={href} href={href} className={mobileLinkClass}>{label}</Link>
            ))}
            <Link
              href="https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor"
              target="_blank"
              rel="noreferrer"
              className={mobileLinkClass}
            >
              GitHub
            </Link>
            <Link href="/download" className="button mt-1 w-full">Download APK</Link>
          </nav>
        </details>
      </div>
    </header>
  );
}
