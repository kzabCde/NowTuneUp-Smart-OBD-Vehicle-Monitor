import type { Metadata } from "next";
import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = {
  title: "Download Android APK",
  description: "Download the latest official NowTuneUp Android APK.",
};

export default function Download() {
  return (
    <section className="shell py-16">
      <p className="eyebrow">Official Android download</p>
      <h1 className="mt-3 text-5xl font-black">Download NowTuneUp</h1>
      <p className="muted mt-4 max-w-2xl">
        Download the APK directly from this website. NowTuneUp requires Android 8.0 or later.
      </p>
      <div className="mt-9">
        <ReleaseCard release={localRelease} />
        <dl className="panel mt-4 grid gap-4 p-6 md:grid-cols-3">
          <div><dt className="muted">Version</dt><dd>{localRelease.version}</dd></div>
          <div><dt className="muted">Version code</dt><dd>{localRelease.versionCode}</dd></div>
          <div><dt className="muted">File</dt><dd>{localRelease.apkName}</dd></div>
        </dl>
      </div>
      <div className="mt-8 flex gap-4">
        <Link href="/install-guide" className="button secondary">Read installation guide</Link>
        <Link href="/releases" className="button secondary">Release history</Link>
      </div>
      <p className="mt-8 text-amber-200">
        Android may ask you to allow installation from your browser or file manager before installing the APK.
      </p>
    </section>
  );
}
