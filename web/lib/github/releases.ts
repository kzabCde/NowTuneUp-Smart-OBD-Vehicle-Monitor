import "server-only";
import { normalizeRelease, ReleaseDataError, stableReleases } from "@/lib/releases/normalize";
import type { ReleaseInfo } from "@/lib/validation/releases";
function repository(){const owner=process.env.GITHUB_REPOSITORY_OWNER;const name=process.env.GITHUB_REPOSITORY_NAME;if(!owner||!name)throw new ReleaseDataError("Release service is not configured",503);return {owner,name}}
async function requestReleases():Promise<unknown>{const {owner,name}=repository();const headers:HeadersInit={Accept:"application/vnd.github+json","X-GitHub-Api-Version":"2022-11-28"};if(process.env.GITHUB_TOKEN)headers.Authorization=`Bearer ${process.env.GITHUB_TOKEN}`;const response=await fetch(`https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}/releases?per_page=30`,{headers,next:{revalidate:900}});if(response.status===403||response.status===429)throw new ReleaseDataError("GitHub release service is temporarily rate limited",503);if(!response.ok)throw new ReleaseDataError("GitHub release service is unavailable",502);return response.json()}
async function checksumFor(release:import("@/lib/validation/releases").GithubRelease, apkName:string):Promise<string|null>{
 const asset=release.assets.find(item=>item.name===`${apkName}.sha256`);if(!asset)return null;
 try{const response=await fetch(asset.browser_download_url,{next:{revalidate:900}});if(!response.ok)return null;const value=(await response.text()).trim().split(/\s+/)[0];return /^[a-fA-F0-9]{64}$/.test(value)?value.toLowerCase():null}catch{return null}
}
export async function getStableReleases():Promise<ReleaseInfo[]>{const results=await Promise.all(stableReleases(await requestReleases()).map(async release=>{try{const normalized=normalizeRelease(release);return {...normalized,sha256:await checksumFor(release,normalized.apkName)}}catch{return null}}));return results.filter((release):release is ReleaseInfo=>release!==null)}
export async function getLatestRelease():Promise<ReleaseInfo>{const release=(await getStableReleases())[0];if(!release)throw new ReleaseDataError("No stable APK release is available",404);return release}
export async function getRelease(version:string):Promise<ReleaseInfo>{const release=(await getStableReleases()).find(r=>r.version===version);if(!release)throw new ReleaseDataError("Release was not found",404);return release}
