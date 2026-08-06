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

## Optional: push notifications (Firebase Cloud Messaging)
The FCM plumbing (`notifications/CentinelFirebaseMessagingService.kt`) is
stubbed but not wired up, since it needs a real Firebase project:
1. Create a Firebase project, add an Android app with package
   `com.centinel.app`, download `google-services.json` into `app/`.
2. Uncomment `id("com.google.gms.google-services")` in `app/build.gradle.kts`
   and in the root `build.gradle.kts`.
3. Add a `POST /notifications/register-device` endpoint on the backend to
   receive the FCM token from `onNewToken()`, and use the Firebase Admin SDK
   server-side to actually send pushes when a High/Critical scan happens.

Until then, the app still surfaces High/Critical alerts via the in-app
**Notifications** screen (backed by `GET /notifications`), which works with
zero extra setup.
