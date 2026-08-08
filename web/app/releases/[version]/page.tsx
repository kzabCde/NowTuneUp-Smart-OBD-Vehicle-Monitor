import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocalRelease } from "@/lib/local-release";
import { versionSchema } from "@/lib/validation/releases";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

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
    <section className="shell section-space">
      <Link href="/releases" className="text-sm font-bold text-cyan-300">← Release history</Link>
      <div className="mt-8"><ReleaseCard release={release} /></div>
      <div className="panel-soft mt-4 p-5 text-sm text-slate-400">For integrity verification, compare the APK SHA-256 value on the official download page after downloading.</div>
    </section>
  );
}
