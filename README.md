# NowTuneUp (NTU)

NowTuneUp is a local-first Android vehicle monitor and its official Next.js product/release website. The Android app reads standardized, read-only OBD-II data through an ELM327-compatible USB adapter. The website explains compatibility and installation, discovers signed APKs from GitHub Releases on the server, and redirects downloads only to trusted GitHub assets.

## Monorepo

```text
android-app/             Kotlin/Compose Android application
web/                     Next.js App Router website
.github/workflows/       Android, web, quality and tagged release CI
scripts/                 Release naming/checksum verification
docs/                    Architecture, security, release and hardware plans
```

## System architecture

```text
Vehicle ECU → ELM327 → USB OTG → Android transport → command queue
    → OBD session/repository → ViewModel/StateFlow → Compose UI
                                      ↓
                               Room + DataStore

Tag → GitHub Actions → tested/signed APK + SHA-256 → GitHub Release
    → Next.js server-only GitHub client → official download redirect
```

No standalone backend exists. GitHub Releases is the production artifact store and Next.js Route Handlers form the small server boundary needed to protect optional GitHub credentials.

## Android application

- Package: `com.nowtuneup.app`; Android 8.0/API 26 minimum; target/compile SDK 35; version 1.0.0 (code 1).
- Compose Material 3 automotive dashboard, portrait/landscape layout, live-data search, read-only DTC scan, local trips, settings, CSV writer, Room and DataStore.
- `ObdTransport` abstraction with debug mock and usb-serial-for-android implementation.
- Serialized, timeout-bounded commands; sequential `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`; Mode 01 PID parsing and read-only Mode 03.
- There is no internet permission and no ECU write/DTC clear command.

Hardware requires Android USB Host/OTG, an ELM327-compatible USB adapter, and an OBD-II vehicle. The serial library supports common CDC ACM, FTDI, CP210x, CH340/CH341, and PL2303 devices. Adapter and vehicle behavior still requires physical testing.

The repository uses a text-only Gradle launcher because committed binary files are not supported. Its first invocation downloads the pinned Gradle 8.14.4 distribution to the user Gradle cache.

```bash
cd android-app
./gradlew clean
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Debug output: `android-app/app/build/outputs/apk/debug/NowTuneUp-debug.apk`.
Release output: `android-app/app/build/outputs/apk/release/NowTuneUp-v1.0.0-release.apk` (unsigned without configured credentials).

Debug builds use mock OBD by default; release builds always default to USB. Mock responses exercise changing telemetry and a stored DTC without hardware.

## Website

The mobile-first dark website includes `/`, `/download`, `/releases`, `/releases/[version]`, `/features`, `/install-guide`, `/supported-devices`, `/privacy`, and `/terms`. Server Components render content; server-only GitHub helpers validate all external data with Zod. APIs:

- `GET /api/releases/latest` returns normalized stable-release metadata.
- `GET /api/downloads?version=1.0.0` validates the semantic version and redirects only to `https://github.com/...`.

```bash
cd web
cp .env.example .env.local
npm ci
npm run typecheck
npm run lint
npm run test
npm run build
```

Vercel can import the repository root directly: the root workspace manifest exposes one pinned Next.js/React runtime and delegates builds to `web`, while the root `vercel.json` selects `web/.next`. Alternatively, set the Vercel Root Directory to `web`. Configure:

| Variable | Required | Exposure |
|---|---:|---|
| `GITHUB_REPOSITORY_OWNER` | Yes | Server only |
| `GITHUB_REPOSITORY_NAME` | Yes | Server only |
| `GITHUB_TOKEN` | No, recommended for rate limits | Server only |
| `NEXT_PUBLIC_SITE_URL` | Yes | Public canonical site URL |

The website never bundles `GITHUB_TOKEN`; public repositories work anonymously.

## Signing and releases

GitHub Actions uses encrypted secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit a keystore or credentials. A tag triggers tests/lint, secret-backed signing, deterministic naming, checksum generation/verification, and GitHub Release upload:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Artifacts are `NowTuneUp-v1.0.0-release.apk` and `NowTuneUp-v1.0.0-release.apk.sha256`. See [release process](docs/release-process.md).

## Safety, privacy, and security

Vehicle data remains on-device unless the user explicitly exports a CSV. NowTuneUp is informational and read-only: it does not flash, code, clear codes, or modify vehicle systems. Park safely before using it; a PID or DTC is not a mechanical diagnosis. Release downloads never come from `web/public` and fallback pages never invent an APK.

## Known limitations

- Physical USB permission/detach behavior, baud negotiation, ECU compatibility, and long-running monitoring require a real-device test matrix.
- Generic DTC descriptions can differ from manufacturer service information.
- GitHub’s anonymous API has lower rate limits; configure the server-only token for production.
- A tagged release requires owner-provided signing secrets.

## Troubleshooting

- **No USB device:** verify USB Host/OTG, cable, adapter power, and chipset support.
- **Permission denied:** reconnect and approve Android’s USB dialog.
- **ELM327 unavailable/`NO DATA`:** turn ignition on and verify protocol/PID support; missing data is never zero.
- **Website release unavailable:** configure repository variables, confirm a non-draft/non-prerelease GitHub Release, branded APK filename, and API rate limit.
