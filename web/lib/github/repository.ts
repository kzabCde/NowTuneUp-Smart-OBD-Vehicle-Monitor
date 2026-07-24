import { ReleaseDataError } from "@/lib/releases/normalize";

type ReleaseEnvironment = Record<string, string | undefined>;

export function releaseRepository(environment: ReleaseEnvironment = process.env) {
  const configuredOwner = environment.GITHUB_REPOSITORY_OWNER?.trim();
  const configuredName = environment.GITHUB_REPOSITORY_NAME?.trim();

  if (configuredOwner && configuredName) {
    return { owner: configuredOwner, name: configuredName };
  }

  const vercelOwner = environment.VERCEL_GIT_REPO_OWNER?.trim();
  const vercelName = environment.VERCEL_GIT_REPO_SLUG?.trim();
  if (vercelOwner && vercelName) {
    return { owner: vercelOwner, name: vercelName };
  }

  const [githubOwner, githubName, ...extra] = (environment.GITHUB_REPOSITORY ?? "").split("/");
  if (githubOwner && githubName && extra.length === 0) {
    return { owner: githubOwner, name: githubName };
  }

  throw new ReleaseDataError("Release service is not configured", 503);
}
