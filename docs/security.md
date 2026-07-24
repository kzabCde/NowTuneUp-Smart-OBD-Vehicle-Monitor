# Security model

The Android command surface is allow-listed and read-only. No DTC clear (Mode 04), manufacturer control, ECU coding, or flashing command belongs in 1.0. Vehicle data stays in local Room/DataStore storage unless the user exports it through Android’s document picker.

Signing material exists only as encrypted GitHub secrets and an ephemeral runner file. Logs and artifacts must never contain credentials. The website validates GitHub data, keeps its optional token server-only, encodes repository coordinates, accepts semantic versions only, and redirects APK requests only to HTTPS `github.com` assets. Production should use protected environments, least-privilege workflow permissions, dependency review, Dependabot, and branch protection.

## Web runtime policy

Production builds pin Node.js to the Vercel-supported 22.x line instead of an open-ended minimum. Next.js is pinned to the patched 15.5.7 release for CVE-2025-66478, with the corresponding patched React and React DOM 19.1.2 runtime forced through npm overrides. Root and workspace versions must remain identical; `npm run verify:web-runtime` enforces that invariant before every production build.
