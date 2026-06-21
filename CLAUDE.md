# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Keep this file current (directive to Claude).** Treat CLAUDE.md as living documentation. Whenever a change you make falsifies something here or adds a durable architectural fact, build/toolchain bump, new module/convention, or hard-won gotcha worth knowing next time, **update CLAUDE.md in the same change** so it never drifts from the code. Update what's stale rather than appending duplicates; keep it concise and accurate. Do not document transient/in-progress work here (that belongs in memory).

## Overview

Stingle Photos is an Android app (Java) for end-to-end encrypted photo/video cloud backup, sync, and sharing. It talks to the Stingle API at `https://api.stingle.org/`. The defining constraint of this codebase is that **the server is untrusted**: all media and metadata are encrypted client-side with libsodium before upload, and only the user holds the keys. The server stores the bare minimum unencrypted data (email, signup date, plan, storage usage, file/album counts and sizes, device count) — no file contents, no plaintext metadata, no IP logs, no analytics. When writing code that sends anything to the server, assume it must reveal nothing about the user's content.

## Build & Run

Gradle wrapper, two product flavors × two build types. A `keystore.properties` at the repo root is **required** for any build (even debug) because the release `signingConfig` is loaded unconditionally in [StinglePhotos/build.gradle](StinglePhotos/build.gradle); the committed file has placeholder values.

```bash
# Build (flavor = playstore | fdroid, type = Debug | Release)
./gradlew assemblePlaystoreDebug
./gradlew assembleFdroidRelease
./gradlew installPlaystoreDebug      # build + install on device/emulator

# Lint
./gradlew :StinglePhotos:lintPlaystoreDebug
```

There are **no unit/instrumentation tests** in this project (`src/test`, `src/androidTest` do not exist) — verification is manual on a device.

**Toolchain (modernized 2026):** Gradle **8.13**, AGP **8.11.1**, `compileSdk 36`, `targetSdk 35`, `minSdk 23`, Java **17** source/target. NDK is no longer pinned (`ndkVersion` removed; libsodium ships prebuilt). The 2021-era stack was upgraded: **ExoPlayer 2.7.3 → AndroidX Media3 1.4.1**, **CameraX alpha → 1.5.3 stable**, **Material → Material 3 + edge-to-edge**, **Play Billing 5 → 7.1.1** (Stripe/web-billing removed). `lazysodium-android 5.2.0` is used specifically because it ships **16 KB page-aligned** `libsodium.so` (required by Android 15+); do not downgrade it.

### Flavors and build types
The app ships through two channels and the flavor split exists specifically to keep them clean:
- **playstore** — distributed on Google Play. Bundles Google Play Billing (`com.android.billingclient`, declared as `playstoreImplementation` so it is excluded from the other flavor). Full billing implementation lives in `src/playstore/.../Billing/`.
- **fdroid** — distributed on F-Droid, which **forbids proprietary/closed-source dependencies**. This flavor must contain **only FOSS dependencies**. `src/fdroid/.../Billing/PlayBillingProxy.java` is a no-op stub that replaces Play Billing.

**Hard rule:** never add a closed-source library as plain `implementation` — that pulls it into both flavors and breaks the F-Droid build/policy. Scope any proprietary dependency (billing, Google/Firebase services, analytics, etc.) to `playstoreImplementation` and provide an open-source or stub equivalent under `src/fdroid/`. When touching billing/payments or any such integration, edit **both** flavor variants or the fdroid build breaks. The currently-known proprietary dependency is Google Play Billing; keep that list at zero for the fdroid flavor.
- **debug** build type sets `applicationIdSuffix '.debug'` (installs side-by-side) and `ALLOW_INSECURE_TLS = true`; **release** sets it `false`. `BuildConfig.ALLOW_INSECURE_TLS` gates the trust-all `TrustManager` in [Net/HttpsClient.java](StinglePhotos/src/main/java/org/stingle/photos/Net/HttpsClient.java).

### Modules
`settings.gradle` includes only `:StinglePhotos` and `:picasso`. `picasso/` is a **vendored fork of Square's Picasso** image loader (customized to decrypt thumbnails on the fly) — prefer fixing it here over upgrading the upstream dependency. The top-level `imagerecognition/` directory contains only build output and is not an active Gradle module.

## Architecture

### Global state — `StinglePhotosApplication`
[StinglePhotosApplication.java](StinglePhotos/src/main/java/org/stingle/photos/StinglePhotosApplication.java) is the service locator. It holds the singleton `Crypto`, the `MemoryCache`, and — critically — the **decrypted master key in memory** (`getKey()` / `setKey()`). The key is only present while the app is unlocked; locking clears it. `getApiUrl()` composes the server URL with `API_VERSION` (currently `v2`). Sync status is also tracked via static fields here.

### Crypto — the heart of the app
[Crypto/Crypto.java](StinglePhotos/src/main/java/org/stingle/photos/Crypto/Crypto.java) wraps **lazysodium / libsodium** (native via JNA) and defines the on-disk encrypted file format (`.sp` files) and the encrypted key file format (`SPK`). The full cryptographic design is documented at https://stingle.org/security/. Core primitives:

- **File data:** each file gets a random 256-bit master key. File bodies are encrypted in chunks with **XChaCha20-Poly1305**, where each block gets its own key **derived via Blake2B** from the master key. Independent per-block keys are what allow seeking within encrypted video without decrypting the whole file.
- **Encrypted header:** the per-file master key plus all sensitive metadata (filename, file type, original size, video duration) live in an encrypted header sealed with the user's public key using `crypto_box_seal` (**X25519** + XSalsa20-Poly1305). The server never sees plaintext metadata.
- **Identity keys:** X25519 keypair generated at signup. The private key is itself encrypted with a password-derived key before any cloud backup.
- **Password KDF:** **Argon2id** at selectable difficulty. The `KDF_DIFFICULTY_NORMAL/HARD/ULTRA` constants map to libsodium's interactive/moderate/sensitive opslimit+memlimit profiles — interactive (~64 MB) for the locally-stored key, moderate (~256 MB) for the cloud-backed key. Argon2's memory-hardness is the deliberate defense against FPGA/ASIC cracking.
- **Auth token:** a *separate* Argon2 salt produces the authentication token sent to the server (NOT the encryption key). The server re-hashes it with SHA512-PBKDF2 before storing, so the plaintext token is never at rest server-side. Keep the auth-token derivation distinct from the encryption-key derivation when touching login/signup.

Any change to header constants, versions, KDF difficulty mapping, or chunking must stay backward-compatible with already-uploaded files and existing accounts. `MnemonicUtils` handles the BIP39-style recovery phrase for backing up/restoring the private key.

### Auth — `Auth/`
`KeyManagement` (generate/import/backup keys, key bundles), `LoginManager` (login/logout/lock flows, password prompts), `BiometricsManagerWrapper` (fingerprint/face unlock). For biometrics, the app generates a 256-bit key, encrypts the user's password with it (AES256/PKCS7), and stores **that key in the Android Keystore** — the password itself is never stored in plaintext, and biometric unlock effectively decrypts the password to derive the key.

### Networking — `Net/`
`HttpsClient` is a hand-rolled `HttpsURLConnection` client (form POST + multipart upload) run on `AsyncTask.THREAD_POOL_EXECUTOR`. Every API response is parsed into a `StingleResponse` (status + parts + infos). There is no Retrofit/OkHttp in the main app.

Beyond TLS, sensitive API requests are **double-encrypted at the application layer**: the serialized request is encrypted with `crypto_box_easy` using the server's public key and the user's private key. This both proves the request came from the key holder and protects payloads even if TLS is compromised. The server's public key is stored locally (`server_public`). When adding or modifying endpoints, preserve this layering rather than relying on TLS alone.

### Local database — `Db/`
Raw SQLite (no Room). `StingleDb` + `StingleDbContract` define schema; `Db/Query/*Db.java` are per-table DAOs (`FilesDb`, `AlbumsDb`, `AlbumFilesDb`, `ContactsDb`, `GalleryTrashDb`, `ImportedIdsDb`). `Db/Objects/*` are the row models (`StingleDbFile`, `StingleDbAlbum`, `StingleContact`). The DB stores **encrypted metadata**; values are decrypted on read via `Crypto`.

### Sync engine — `Sync/`
`SyncManager` is the orchestrator. It runs as background work via `SyncWorker` (`androidx.work` WorkManager, scheduled periodically; also kicked on boot by `BootCompleteReceiver`). The actual work is split into `Sync/SyncSteps/`:
- `FSSync` — reconcile local filesystem with DB
- `ImportMedia` — import device gallery media into the encrypted store
- `SyncCloudToLocalDb` — pull server changes into local DB
- `UploadToCloud` — encrypt + upload pending local files

Sync status (`STATUS_IDLE/REFRESHING/UPLOADING/...`) is broadcast via `LocalBroadcastManager` and mirrored on `StinglePhotosApplication`.

### Async pattern
The codebase predates coroutines and uses Android's deprecated `AsyncTask` extensively — see the ~40 tasks in `AsyncTasks/` (`AsyncTasks/Gallery`, `AsyncTasks/Sync`). New background work should follow the existing `AsyncTask` + `OnAsyncTaskFinish` callback convention to stay consistent unless deliberately modernizing.

### Sharing & albums — `Sharing/`
Each album has its **own X25519 keypair**. Files in an album are encrypted to the album's public key, not to individual users. To share, the owner decrypts the album's private key with their own private key, then re-encrypts that album private key to each recipient's public key — **files themselves are never re-encrypted or re-uploaded** when sharing. `SharingPermissions` encodes per-member rights. This is why sharing is cheap and why revoking access is about key/membership management, not data movement.

### UI
Activity-based (`GalleryActivity` is the main screen after login; `ViewItemActivity` for full-screen viewing; `CameraXActivity` for in-app encrypted capture via CameraX). Fragments under `Gallery/`, `Sharing/`, `Billing/`. `viewBinding` is enabled. Theme and locale are applied in `Application.onCreate` via `Util/Helpers`.

**Material 3 + edge-to-edge:** themes are `Theme.Material3.DayNight`; the brand red is `md_primary` (light) / `#F44336` (night — M3's default dark tint is pink, so it's overridden). Activities run edge-to-edge; system-bar/cutout insets are applied as padding/margin via the `Helpers.applyTopInsetPadding/applyBottomInsetPadding/...` helpers rather than `fitsSystemWindows`. Video playback uses **Media3** (`ViewItemAsyncTask` builds the player; `Video/StingleDataSource[Factory]` decrypt chunks on the fly — Media3's `read()` returns partial reads, so the loop in `StingleDataSource.readFully()` is required).

**Gallery grid layout-stability (hard-won — keep it this way):** the gallery `RecyclerView` is a `DragSelectRecyclerView` with a date-grouped `GridLayoutManager` (`AutoFitGridLayoutManager`, full-span date headers) and a draggable date scrollbar. On a huge library, **any child `requestLayout()` while idle relayouts the list and drifts the scroll anchor** (manifests as auto-scroll / position shift after a scrollbar jump). So cells are kept rigidly fixed-size and child views must never trigger layout on content change: thumbnails use `Gallery/Helpers/FixedShapeableImageView` (no-op `requestLayout`), cell + date-header roots are pinned to fixed heights in `onCreateViewHolder`, the date-header and video-duration `TextView`s are pinned to fixed measured sizes (`setText` relayouts whenever *either* dimension is `wrap_content`), overlay icons toggle `INVISIBLE`/`VISIBLE` (never `GONE`, which relayouts), and `GalleryFragment.updateDataSet()` saves/restores the first-visible position with `scrollToPositionWithOffset` (NOTE: `RecyclerView.getScrollY()/setScrollY()` are no-ops on a RecyclerView). When editing gallery item layouts/binding, preserve these invariants.

## Conventions & gotchas
- `minSdk 23`, `targetSdk 35`, `compileSdk 36`, Java 17 source level (see Toolchain above).
- Background work that must survive the app being backgrounded (sync, uploads, thumbnail download) goes through **WorkManager** (`SyncWorker`, run synchronously) or a **foreground service** (`Sync/ThumbsDownloadService`, `CameraX/MediaEncryptService`, type `dataSync`) — app-managed foreground services get "Stop FGS timeout"-killed on Android 15 once backgrounded, so prefer WorkManager for background sync/upload. Camera capture hands its upload to `SyncManager.startOneTimeSync()`.
- Security vulnerabilities go to security@stingle.org per [SECURITY.md](SECURITY.md), **not** public GitHub issues.
- Preferences are split between `DEFAULT_PREFS` and `STICKY_PREFS` (server URL, tokens) — the latter survives logout-style clears. Use the `Helpers` preference wrappers.
- `versionCode` / `versionName` in `build.gradle` are bumped per release (see git history).
