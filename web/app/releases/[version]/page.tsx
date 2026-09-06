import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocalRelease } from "@/lib/local-release";
import { versionSchema } from "@/lib/validation/releases";
import { ReleaseCard, bytes } from "@/components/ui/ReleaseCard";

export async function generateMetadata({ params }: { params: Promise<{ version: string }> }): Promise<Metadata> {
  const { version } = await params;
  return { title: `Release ${version}` };
}

export default async function Release({ params }: { params: Promise<{ version: string }> }) {
  const raw = decodeURIComponent((await params).version);
  if (!versionSchema.safeParse(raw).success) notFound();
  const release = getLocalRelease(raw);
  if (!release) notFound();

  return (
    <>
      <section className="page-hero">
        <div className="shell">
          <Link href="/releases" className="accent-link">← Release history</Link>
          <p className="eyebrow mt-7">Release detail</p>
          <h1 className="page-title mt-4">NowTuneUp {release.version}</h1>
          <p className="page-lede mt-7">Production package details, install target and integrity context for this local release record.</p>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <ReleaseCard release={release} />

          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="technical-card p-5"><p className="data-label">Version</p><p className="data-value mt-3 text-xl">{release.version}</p></div>
            <div className="technical-card p-5"><p className="data-label">Build</p><p className="data-value mt-3 text-xl">{release.versionCode}</p></div>
            <div className="technical-card p-5"><p className="data-label">Minimum</p><p className="data-value mt-3 text-xl">{release.minimumAndroid}+</p></div>
            <div className="technical-card p-5"><p className="data-label">APK size</p><p className="data-value mt-3 text-xl">{bytes(release.apkSize)}</p></div>
          </div>

          <div className="warning-callout mt-5 p-5 text-sm leading-7">For integrity verification, compare the APK SHA-256 value on the official download page after downloading.</div>
          <div className="page-actions mt-6"><Link href="/download" className="button secondary">Verify current APK</Link><Link href="/install-guide" className="button secondary">Installation guide</Link></div>
        </div>
      </section>
    </>
  );
}
