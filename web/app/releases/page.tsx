import type { Metadata } from "next";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = { title: "Release history" };

export default function Releases() {
  return (
    <section className="shell py-16">
      <p className="eyebrow">Changelog</p>
      <h1 className="mt-3 text-5xl font-black">Release history</h1>
      <div className="grid mt-8">
        <ReleaseCard release={localRelease} compact />
      </div>
    </section>
  );
}
