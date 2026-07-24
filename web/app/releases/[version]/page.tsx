import type { Metadata } from "next";
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
  return <section className="shell py-16"><ReleaseCard release={release} /></section>;
}
