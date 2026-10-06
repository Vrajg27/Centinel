# Centinel — Android App (Kotlin + Jetpack Compose)

This is source code only — I built and unit-tested the backend in this
sandbox, but **compiling/running an Android app requires Android Studio and
an SDK/emulator that aren't available in this environment**, so this hasn't
been built or run. It's written to be opened directly in Android Studio and
should compile with only the two setup steps below.

## Architecture
MVVM: `ui/<feature>/XScreen.kt` (Compose) + `XViewModel.kt` → `data/repository/CentinelRepository.kt`
→ `data/api/ApiService.kt` (Retrofit) → your locally running FastAPI backend.

Every module from the spec has a real screen wired to a real backend endpoint:
Dashboard (with rotating security tips), URL Scanner, Website Scanner
(redirect-chain/JS/login-form analysis — distinct from URL Scanner), Email
Scanner (paste text or upload a raw `.eml` file), Email Header Analyzer, SMS
Scanner, QR Scanner (live camera via CameraX + ML Kit), File Scanner (SAF
document picker), Password Analyzer, SSL Certificate Checker, Data Breach
Checker, Scan History (search/filter/delete/PDF report download), Threat
Analytics (donut + bar charts drawn with Compose's own `Canvas` API — see
note below), Notifications, plus Login/Register with JWT + auto-refresh.

PDF reports: each History row has a download icon that fetches
`GET /report/{id}`, saves it to the app's external-files directory (no
storage permission needed on any API level), and opens it via a
`FileProvider`-backed chooser Intent (`data/reports/ReportDownloader.kt`).

**Charts note:** Analytics originally declared a Vico chart-library
dependency, but I removed it in favor of hand-rolled donut/bar charts built
on Compose's own `Canvas`/`drawArc` APIs (`ui/common/Charts.kt`). Vico's
public API has shifted enough across versions that writing to it from
memory — with no way to compile-check in this sandbox — seemed more likely
to hand you a broken build than a plain-Canvas chart. If you'd rather use
Vico (nicer animations, more chart types), re-add the dependency and swap
the two composables in `AnalyticsScreen.kt`.

## 1. Open the project
Open the `android/` folder directly in **Android Studio (Koala or newer)**.
Studio will detect there's no Gradle wrapper jar and offer to regenerate
it automatically — accept that, or run once manually:
```bash
gradle wrapper --gradle-version 8.7
```

## 2. Point the app at your backend
Start the backend first (see `../backend/README.md`). Then in
`app/build.gradle.kts`, `API_BASE_URL` is already set to:
- `http://10.0.2.2:8000/` — correct as-is for the **Android emulator** (this
  is the emulator's alias for your host machine's localhost).
- For a **physical device**, change it to your computer's LAN IP, e.g.
  `http://192.168.1.42:8000/`, and make sure the phone is on the same
  Wi-Fi network as the backend.

## 3. Run
Click Run in Android Studio. Register a user, log in, and the Dashboard's
tool grid gives you every module.

## Push notifications (Firebase Cloud Messaging)

Fully implemented in `notifications/CentinelFirebaseMessagingService.kt` —
displays a local notification when a push arrives, requests the
`POST_NOTIFICATIONS` runtime permission on Android 13+ (from the Dashboard,
the first screen after login), and registers/refreshes the device's FCM
token with the backend automatically (right after login, and again
whenever FCM rotates the token via `onNewToken`). Logging out unregisters
the current device.

**The one remaining manual step is providing your own Firebase project**,
since that's inherently something only you can do:
1. Create a Firebase project, add an Android app with package
   `com.centinel.app`, download `google-services.json` into `app/`.
2. Uncomment `id("com.google.gms.google-services")`
   (both the `plugins {}` block reference and the root `build.gradle.kts`
   declaration are already there, just commented).
3. On the **backend**, set `CENTINEL_FIREBASE_CREDENTIALS_PATH` to a
   Firebase *service account* JSON (Project Settings → Service Accounts →
   Generate new private key — a different file from `google-services.json`
   above) — see `backend/README.md`'s "Push notifications" section.

Without that setup, `FirebaseMessaging.getInstance().token` calls fail
gracefully (caught and logged, not crashed — see
`data/push/FcmToken.kt`), and device registration silently never happens.

Either way, High/Critical alerts always still surface via the in-app
**Notifications** screen (backed by `GET /notifications`), which works with
zero extra setup and doesn't depend on Firebase at all.
