# Architecture

## Android data flow

```text
Compose UI → ViewModel → use/session boundary → serialized command queue
    → ObdTransport → USB serial → ELM327 → ECU
                       ↓ responses
PID/DTC parser → nullable domain readings → StateFlow → UI
                       ↓
                  Room / DataStore
```

Transport owns bytes and connection state; protocol code owns allow-listed commands and validation; the session owns initialization and polling; presentation observes domain state. USB code never appears in an Activity, Composable, or ViewModel.

## Website release flow

```text
Server Component / Route Handler → server-only GitHub client
 → GitHub Releases REST API → Zod validation → stable release filter
 → branded APK selection → normalized metadata / trusted redirect
```

Tokens remain server-side. Drafts, prereleases, malformed payloads, missing APK assets, rate limits, and outages produce safe fallback UI/status responses.

## GitHub Actions release flow

```text
v* tag → tests + lint → decode ephemeral keystore → signed assembleRelease
 → branded rename → SHA-256 → checksum verification
 → GitHub Release + APK + checksum → website discovery
```
