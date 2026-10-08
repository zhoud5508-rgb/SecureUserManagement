# Secure User Management

**Secure Programming Project 2 · Member 3 — Security Developer / Code Coordinator**

A Kotlin Android demonstration of local registration, login, profiles, password changes, account lockout, activity records, and user/admin screens. This is an academic demo, not a production identity service.

## Project status

The Android Studio project copy built successfully and was installed on the Medium Phone API 37 emulator. Registration and login were exercised, and the Activity Log showed both records. Logout was verified to return to Welcome. Manual evidence for invalid input, lockout, authorization denial, password change, and Back navigation remains outstanding; unit tests have not yet been run.

## Source project

Download [SecureUserManagement-Member3.zip](SecureUserManagement-Member3.zip), extract it, then open the extracted project folder in Android Studio. The archive contains the full Gradle project, README, and docs.

## Security cases

See [docs/SECURITY_CASES.md](docs/SECURITY_CASES.md) for six code-backed cases, technical explanations, principles, and verification status: password hashing and salt; login lockout; parameterized Room queries (SQL injection prevention); role authorization; encrypted session storage; and security activity logging. SQL injection is described as a prevention control already present, not as a demonstrated exploit.

## Technology, APIs, and libraries

Kotlin, Jetpack Compose Material 3, Navigation Compose, Room, Kotlin Coroutines, Android Lifecycle ViewModel, Gradle, Android Gradle Plugin, Kotlin Compose plugin, and KSP. Security uses Java Cryptography Architecture (PBKDF2WithHmacSHA256, SecureRandom, constant-time comparison) and AndroidX Security Crypto (EncryptedSharedPreferences). External web APIs: none; the app makes no network requests. Dependency versions are documented in the project archive's README and version catalog.

## AI assistance

ChatGPT helped organize the supplied code into a project, review security issues, propose code changes, and draft documentation. The student must review the work and complete the remaining manual checks and course-required evidence.

## Group details

Member names, matric numbers, programme, instructor, and video links remain placeholders until confirmed by the group.
