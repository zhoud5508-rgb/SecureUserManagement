# Secure User Management

**Secure Programming Project 2 · Member 3 — Security Developer / Code Coordinator**

This Android application demonstrates local user registration and sign-in, profile and password changes, per-account lockout, activity records, and user/admin screens. It is an academic security demonstration. It is not a production identity service: it has no server-side trust boundary, no remote abuse controls, and its local Room database can be inspected or modified on a compromised device.

## Project status

This deliverable is a cleaned project copy assembled from the supplied `SecureUserManagementProject.kt` and the Android Studio project scaffold. The original Android Studio folder was not modified; work was completed in this project copy. Android Studio successfully built and installed the debug app on the Medium Phone API 37 emulator. Registration and login were exercised, and the Activity Log displayed Registration and Successful Login records. The Activity Log now has visible Back and Logout controls; Logout was tapped and returned to Welcome. Invalid-input, lockout, authorization-denial, password-change, and Back-navigation evidence remains outstanding. See `docs/SECURITY_CASES.md` for the case-by-case status.

## Features

- Kotlin Android app with Jetpack Compose Material 3 screens and Navigation Compose.
- Room database for users and activity records; user searches use Room's bound query parameters.
- Password derivation with PBKDF2-HMAC-SHA256, per-user random salt, and constant-time hash comparison.
- Registration and password-change validation; database uniqueness constraint for usernames.
- Failed-login attempt counter and temporary account lockout with a generic credential error.
- Encrypted preferences for the local session.
- Repository-level checks for privileged user/log reads and admin changes; admin user lists return a summary model without password hashes or salts.
- Password changes are limited to the signed-in account and the UI waits for the operation result before showing success.
- Android backup is disabled for this local credential demo.

## Technology stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | AndroidX Navigation Compose |
| Local persistence | Room Database |
| Async work/state | Kotlin Coroutines, Android Lifecycle ViewModel |
| Password storage | Java Cryptography Architecture `SecretKeyFactory` with `PBKDF2WithHmacSHA256`; Android `SecureRandom` |
| Session storage | AndroidX Security Crypto `EncryptedSharedPreferences` |
| Build | Gradle Wrapper, Android Gradle Plugin, Kotlin Compose plugin, KSP |
| Minimum Android | API 24 (Android 7.0) |

## Libraries and APIs

The versions below are recorded from this project copy's version catalog or source setup. Confirm dependency availability in Android Studio before release.

| Library/API | Purpose | Version |
|---|---|---|
| [AndroidX Compose BOM / Material 3](https://developer.android.com/jetpack/compose) | Compose UI components | BOM 2026.05.01 |
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | Compose host activity | 1.13.0 |
| [AndroidX Navigation Compose](https://developer.android.com/jetpack/androidx/releases/navigation) | Screen navigation | 2.9.6 |
| [AndroidX Lifecycle ViewModel Compose / Runtime](https://developer.android.com/jetpack/androidx/releases/lifecycle) | Lifecycle-aware state and coroutines | 2.10.0 |
| [AndroidX Room Runtime / KTX / Compiler](https://developer.android.com/jetpack/androidx/releases/room) | Local database and DAO generation | 2.8.4 |
| [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) | Encrypted local session preferences | 1.1.0-alpha06 |
| [Kotlinx Coroutines Android](https://kotlinlang.org/docs/coroutines-overview.html) | Background database work | 1.11.0 |
| [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin) | Android build | 9.4.1 |
| [Kotlin / Compose plugin](https://kotlinlang.org/) | Kotlin and Compose compilation | 2.2.10 |
| [KSP](https://github.com/google/ksp) | Room compiler integration | 2.3.8 |
| Android platform APIs | `Context`, `Application`, `SQLiteConstraintException`, `android.util.Base64`, app lifecycle and system time | Android SDK |
| Java Cryptography APIs | `SecureRandom`, `PBEKeySpec`, `SecretKeyFactory`, `MessageDigest.isEqual` | JCA |

**External web APIs:** none. The app makes no network requests and does not require the Internet permission. It does not use Retrofit, Firebase, or a remote backend. The open-source libraries used by the project are the AndroidX/Jetpack Compose, Activity, Navigation, Lifecycle, Room, Security Crypto, Kotlin Coroutines, Kotlin/KSP, and Gradle/Android Gradle Plugin dependencies listed above. Their exact pinned versions are in `gradle/libs.versions.toml`; there are no additional web-service SDKs.

**Resources:** the linked rows above point to the maintainers' documentation/release pages. Android platform APIs and Java Cryptography Architecture APIs come from the Android SDK/JDK rather than third-party web services. Check the course's required license-attribution format before final submission if a separate license appendix is requested.

## Source layout

```text
SecureUserManagementProject/
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/example/secureusermanagement/
│   │   ├── UserData.kt          # entities, DAO, database, routes
│   │   ├── SecurityUtils.kt     # password hashing and validation
│   │   ├── UserRepository.kt    # data access and access checks
│   │   ├── AppViewModel.kt      # UI state and async actions
│   │   └── MainActivity.kt      # Compose navigation and screens
│   ├── src/test/.../ValidationUtilsTest.kt
│   └── src/main/res/            # themes, strings, launcher assets
├── gradle/                      # wrapper and version catalog
├── app/build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Run in Android Studio

1. Open this folder (`SecureUserManagementProject`) in Android Studio.
2. Let Gradle sync. Install the Android SDK platform and build tools requested by the Gradle configuration if Android Studio prompts.
3. Choose an emulator or Android device and run the `app` configuration.
4. Register a synthetic test user and try login, invalid credentials, lockout, password change, profile, and activity log. Check Back and Logout from Activity Log.
5. Admin controls require a test admin role. This project intentionally does not ship a hard-coded admin username/password or an insecure self-service role selector. For the admin case, use an isolated test database and a documented test fixture/Android Studio Database Inspector; never seed real credentials or publish a backdoor.

## Tests

Run from the project folder:

```text
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

`ValidationUtilsTest` covers valid registration, weak password rejection, malformed email/username, mismatched passwords, and blank login input. Manual case procedures and current evidence status are in `docs/SECURITY_CASES.md`. The Android Studio `assembleDebug` build succeeded and installed on the API 37 emulator; the unit-test task has not yet been run, so no unit-test pass is claimed.

## Secure Programming principles

- **Authentication:** password verification, failed-login tracking, and temporary lockout.
- **Authorization:** current persisted role is checked for privileged repository reads and writes; changing a password is bound to the current user.
- **Confidentiality:** passwords are not stored in plaintext; admin list queries do not select hash or salt fields; Android backup is disabled.
- **Data Integrity:** input validation, unique username index, password-change owner check, and parameterized database operations.
- **Non-Repudiation / accountability:** security actions are recorded with timestamps; admin operations identify the acting admin. Local logs are not tamper-proof and cannot provide strong non-repudiation.

## AI assistance acknowledgement

ChatGPT was used to organize the supplied project source into files, review code for security issues, prepare security-case explanations, suggest code changes, and draft this README and the portfolio content. The student must review every change, run the app and tests, replace placeholders with verified evidence, and follow the course's AI-use policy.

## Team and links

- Group: [Group name]
- Programme: [Programme]
- Instructor: [Instructor name]
- Member 3: [Name] · [Matric No.] · Security Developer / Code Coordinator
- GitHub target supplied by the student: https://github.com/zhoud5508-rgb/SecureUserManagement (project upload still pending)

Personal details and video links remain placeholders until verified by the group. The supplied GitHub target has not yet been checked or uploaded from this environment; the project copy does not yet have a Git remote.
