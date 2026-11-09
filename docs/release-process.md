# Release process

1. Confirm `android-app` tests/lint/build and all `web` checks pass.
2. Update Android `versionName`, `versionCode`, expected output name, and the matching `docs/releases/vX.Y.Z.md` release notes together.
3. Configure repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
4. Merge to the protected release branch, then either run **Publish Android release** manually with `1.0.0`, or create and push an annotated semantic tag:

```bash
git tag -a v1.0.0 -m "NowTuneUp 1.0.0"
git push origin v1.0.0
```

5. Watch **Publish Android release**. It tests, lints, restores the temporary key, builds/signs, renames, hashes, verifies, and publishes both files.
   The workflow rejects a release when its semantic version does not match the Android `versionName`. A manual run creates the corresponding `v1.0.0` GitHub Release and tag from the selected branch.
6. Download both assets, run `sha256sum -c NowTuneUp-v1.0.0-release.apk.sha256`, and install on a clean test device.
7. Confirm `/api/releases/latest`, `/download`, and the download redirect show the new stable release. Never mark draft or prerelease artifacts as the latest stable version.

Rollback by marking the GitHub Release unavailable, fixing forward with a new version/tag, and documenting the superseded build. Never overwrite an existing tag or signed asset.

## Verified website synchronization (1.18.0 onward)

The release workflow now rejects missing signing credentials and existing release
versions. Before publication, it verifies the APK package/version, checksum and
signing identity against the website's current production APK. Local unsigned
builds are not publishable.

After successful publication, the reusable **Sync verified Android release to
website** workflow downloads the actual stable assets, verifies their checksum,
size, signature, application ID and source version, then updates both the bundled
`NowTuneUp.apk` and `web/lib/local-release.ts`. The website's version text follows
that verified metadata. The workflow runs web checks and opens a release pull
request against `main`; it does not push directly to `main`. Merge that PR after
its required checks to deploy the website's new APK download.

Manual recovery: run the website sync workflow from the release ref with its
published version input. Existing release branches/assets are preserved. The
synchronizer refuses drafts, prereleases, mismatched signing identities,
untrusted download URLs and mismatched artifact sizes/checksums.
