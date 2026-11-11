# Sticky Notes (Android)

A minimal, private, offline notes app: Kotlin + Jetpack Compose (Material 3) + Room.

## Features
- Quiet "paper and ink" design: flat cards, hairline borders, no shadows, neutral palette
- 8 muted paper tints (light + dark), new notes start Plain
- Pin notes to the top, search title + body
- Auto-save on back and when the app goes to the background; emptied notes are discarded
- Long-press a note → Pin / Delete, with **Undo** snackbar
- Share a note to any app
- **Optional app lock** (Settings): fingerprint, face or the phone's PIN/pattern/password.
  While on, notes are hidden from screenshots and the recent-apps preview
- Dark mode, themed (monochrome) launcher icon
- No internet permission, no tracking: notes live in a local Room database
- minSdk 28 (Android 9), targetSdk 36

## App lock: what it protects
The lock stops someone who picks up your unlocked phone from reading your notes. It re-locks
after the app has been in the background for 30 seconds, and always on a fresh start.

It is a *privacy* lock, not encryption: the notes database itself isn't encrypted with a key
tied to your fingerprint. Someone with a rooted phone and hacking tools (e.g. Frida) could
bypass the prompt or read the database directly. Encrypting the database (SQLCipher + an Android
Keystore key unlocked by biometrics) would close that gap, but if you add a new fingerprint
the key is destroyed and the notes become unreadable, so it needs a recovery plan first.

## Project layout
```
app/src/main/java/dev/rushi/stickynotes/
  StickyNotesApp.kt        app-wide scope + repository
  MainActivity.kt          navigation (list ↔ editor)
  data/                    Note entity, DAO, Room DB, repository
  ui/list/                 board screen + ViewModel
  ui/editor/               editor screen + ViewModel
  ui/theme/                Material theme + note colours
```

## Option A — just for you (no Android Studio needed)
1. Push this folder to a GitHub repo.
2. The **Build** workflow (`.github/workflows/build.yml`) runs automatically.
3. Open the run → download the `sticky-notes-debug-apk` artifact → unzip → copy the APK to your phone
   and install it (allow "Install unknown apps" for your file manager).

## Option B — Android Studio
Open the `StickyNotes` folder in Android Studio (Narwhal or newer). It will offer to add the Gradle wrapper —
accept, let it sync, then press **Run** with your phone connected (USB debugging on).
To add the wrapper from a terminal instead: `gradle wrapper --gradle-version 8.14.3`.

## Publishing on Google Play

### 1. Before the first upload
- **Change `applicationId`** in `app/build.gradle.kts` to something you own
  (e.g. `com.yourname.stickynotes`). It can never change once published.
- Bump `versionCode` (+1) and `versionName` for every upload.

### 2. Create your upload key (once — back it up!)
```bash
keytool -genkeypair -v -keystore release.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```
Create `keystore.properties` in the project root (it's git-ignored):
```
storeFile=release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```
Build locally: `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`

**Or build in GitHub Actions:** add repo secrets `KEYSTORE_BASE64` (`base64 -w0 release.jks`),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The workflow then also produces
`sticky-notes-release-aab`.

Use **Play App Signing** (the default) — Google holds the real app key; yours is only the upload key,
so a lost upload key can be reset.

### 3. Play Console
1. Create a developer account (one-time US$25 fee, identity verification).
2. Create app → upload the `.aab`.
3. Store listing: use `store/icon-512.png` and `store/feature-graphic.png`, plus 2–8 phone screenshots.
4. **Privacy policy URL** is required — host `PRIVACY.md` (GitHub Pages, a gist, or your Cloudflare Pages site).
5. Data safety form: answer **"No data collected or shared."**
6. Content rating questionnaire → "Utility/Productivity", no sensitive content.
7. Target audience: 18+ (or 13+) keeps you out of the Families policy requirements.

### 4. New personal accounts: the closed test rule
If your personal developer account was created after 13 Nov 2023, you must run a **closed test with
at least 12 testers opted in for 14 continuous days** before you can apply for production access.
Internal testing does not count. Organisation accounts are exempt.
