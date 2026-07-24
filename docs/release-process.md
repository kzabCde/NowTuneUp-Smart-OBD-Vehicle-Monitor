# Release process

1. Confirm `android-app` tests/lint/build and all `web` checks pass.
2. Update Android `versionName`, `versionCode`, release notes, and expected output name together.
3. Configure repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
4. Merge to the protected release branch, then create and push an annotated semantic tag:

```bash
git tag -a v1.0.0 -m "NowTuneUp 1.0.0"
git push origin v1.0.0
```

5. Watch **Publish Android release**. It tests, lints, restores the temporary key, builds/signs, renames, hashes, verifies, and publishes both files.
6. Download both assets, run `sha256sum -c NowTuneUp-v1.0.0-release.apk.sha256`, and install on a clean test device.
7. Confirm `/api/releases/latest`, `/download`, and the download redirect show the new stable release. Never mark draft or prerelease artifacts as the latest stable version.

Rollback by marking the GitHub Release unavailable, fixing forward with a new version/tag, and documenting the superseded build. Never overwrite an existing tag or signed asset.
