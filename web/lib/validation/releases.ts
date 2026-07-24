import { z } from "zod";
export const assetSchema=z.object({name:z.string(),size:z.number().nonnegative(),browser_download_url:z.string().url(),download_count:z.number().nonnegative().optional()});
export const githubReleaseSchema=z.object({tag_name:z.string(),name:z.string().nullable().optional(),body:z.string().nullable(),draft:z.boolean(),prerelease:z.boolean(),published_at:z.string().datetime().nullable(),assets:z.array(assetSchema)});
export const releasesSchema=z.array(githubReleaseSchema);
export const versionSchema=z.string().regex(/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/);
export type GithubRelease=z.infer<typeof githubReleaseSchema>;
export type ReleaseInfo={version:string;tagName:string;versionCode:number;apkName:string;apkSize:number;downloadUrl:string;downloadCount?:number;sha256:string|null;minimumAndroid:string;publishedAt:string;releaseNotes:string};
