# Security Case Notes

These cases map the supplied Kotlin source and the security changes in this project copy to the Exercise 1 principles. A successful build does not prove every control works. Record screenshots, emulator/device details, GitHub source links, and unlisted YouTube evidence for each targeted case before claiming full verification.

## Current evidence status

- **Build:** Android Studio `assembleDebug` succeeded; the app installed on the Medium Phone API 37 emulator. One Kotlin compile warning remains.
- **Observed flow:** registration completed; login succeeded; Activity Log displayed `Registration` and `Successful Login` records for the synthetic test account.
- **Navigation change:** Activity Log now visibly presents `Back` and `Logout` actions in the top bar. Logout was tapped and returned to Welcome. The Back action is visible and wired to the previous screen, but its tap-through evidence still needs recording.
- **Not yet evidenced:** generic invalid-login response, three-attempt lockout and expiry, SQL-like login input rejection, password validation/change ownership, USER denial of admin operations, ADMIN operation success, and logout session clearing.
- **Unit tests:** included, but the unit-test task has not been run in this verification pass.

## Case 1 — Authorization / Role-Based Access Control

**Baseline evidence:** `UserRepository.getAllUsers()`, `getAllLogs()`, `toggleAdminStatus()`, and `unlockUser()` initially called the DAO without verifying the signed-in actor's role. Hiding the Admin screen in Compose did not protect the repository methods.

**Risk / principles:** A normal user could invoke a privileged path and read other accounts/logs or alter a role/lock state. This affects Authorization and Data Integrity.

**Implementation:** `UserRepository.requireAdmin()` loads the actor ID from the session and checks the actor's current role from Room before privileged reads. Role and unlock actions perform the same check and log the acting admin. `AdminUserSummary` limits the returned user columns.

**Manual run test:** With a regular USER session, invoke each admin repository operation and confirm it is denied; confirm admin screens/data remain unavailable. With a controlled test ADMIN account, confirm permitted admin operations still work. Do not hard-code or publish admin credentials.

**Limit:** this is a local app. The local database is not a trusted remote authorization boundary; a device owner can modify app data.

## Case 2 — Confidentiality / Password data minimization

**Baseline evidence:** the original all-users query returned complete `UserEntity` objects containing `passwordHash` and `salt`, even though the management screen only displayed username, email, role, and lock status.

**Risk / principles:** unnecessary credential material increases exposure if a caller logs, displays, exports, or accidentally forwards a user list. This affects Confidentiality.

**Implementation:** `UserDao.getAll()` selects only `id`, `username`, `email`, `role`, and `accountLocked` into `AdminUserSummary`. Password derivation uses a random per-account salt; verification compares hash bytes with `MessageDigest.isEqual`.

**Manual run test:** register two accounts with the same password, inspect the local database in an isolated emulator, and confirm neither plaintext password appears and the salts differ. Inspect the admin user list/model and confirm no hash or salt fields are returned.

**Limit:** PBKDF2 hashes and local Room records remain sensitive. The app has no encrypted database and should use synthetic data only.

## Case 3 — SQL Injection prevention / Data Integrity

**Baseline evidence:** the source uses Room bind parameters such as `WHERE username = :username` and `WHERE id = :id`. No string concatenation of user input into SQL was found in the supplied DAO queries. This is a positive control, not a vulnerability claim.

**Risk / principles:** building SQL by concatenating an attacker-controlled username could change query meaning and expose or modify records. Parameter binding keeps input as a value. This relates to Data Integrity and Confidentiality.

**Implementation/control:** Room's parameterized queries are preserved; no user-input SQL concatenation is used. Registration username validation rejects characters outside the accepted username set. This case is a prevention control already present in the baseline, not a vulnerability fix.

**Manual run test:** attempt login with a SQL-like username such as `' OR 1=1 --` and confirm it is treated as a literal username and does not authenticate. Review all DAO queries for bound parameters.

**Portfolio wording:** describe this as “SQL Injection Prevention” rather than “SQL Injection vulnerability fixed,” because the supplied query was already parameterized.

## Case 4 — Login test / Account lockout and response consistency

**Baseline evidence:** failed attempts increment a per-user counter and lock an account after three failures. The original code returned a distinct message for a locked account, revealing account state.

**Risk / principles:** attackers can guess credentials; detailed responses can support account enumeration. This affects Authentication and Confidentiality.

**Implementation:** locked and invalid-credential paths use the same generic message. The lockout threshold is three failed attempts and the lock duration is five minutes; lockout events are logged without recording passwords.

**Manual run test:** register a test account, enter a wrong password three times, confirm subsequent attempts are rejected for the configured lock period, and compare the message for an unknown username with the message for a locked account.

**Limit:** client-only lockout can be reset by clearing local app data and does not provide server-side rate limiting.

## Case 5 — Input validation / Account creation

**Baseline evidence:** the supplied source accepted a six-character password and used a very basic email check; the original code did not restrict username characters/length consistently.

**Risk / principles:** weak or malformed values degrade account security and data quality. This affects Authentication, Confidentiality, and Data Integrity.

**Implementation:** registration and password-change validation require at least 12 characters, trim and check usernames against a 3–32 character allowlist, validate email format, and require confirmation match. A unique Room index enforces username uniqueness, with a 1→2 migration rather than destructive fallback.

**Automated coverage in source:** `ValidationUtilsTest` checks valid registration, short password, malformed email/username, mismatch, and blank login. The unit-test task has not been run in this verification pass.

**Manual run test:** try blank fields, a short password, malformed email, whitespace/symbol-heavy username, mismatched confirmation, and a valid registration. Confirm invalid values are rejected before insertion.

## Case 6 — Password-change ownership, truthful result, and audit attribution

**Baseline evidence:** password-change accepted a user ID parameter, and the screen immediately displayed success after launching an asynchronous operation. Admin actions were originally logged against the affected user's identity rather than the acting admin.

**Risk / principles:** a caller could target another user's record through a repository call; misleading success feedback obscures failures; logs can misattribute actions. This affects Authentication, Data Integrity, and Non-Repudiation/accountability.

**Implementation:** password changes require the user ID to match the current session, the current password is verified before replacement, and the UI reports success/failure only when the repository returns. Admin role/unlock events record the authenticated admin as actor and include the target username in the event description.

**Manual run test:** supply a wrong current password and confirm the UI reports failure and the stored password remains unchanged; perform a valid change and confirm the success appears only after completion. Review the activity log for actor/action/time.

**Limit:** local Room logs can be altered by someone controlling the device. They support traceability but are not cryptographically tamper-evident and do not provide legal-grade non-repudiation.

## Mapping to Exercise 1

| Exercise 1 principle | Demonstrated cases |
|---|---|
| Authentication | 4, 5, 6 |
| Authorization | 1, 6 |
| Confidentiality | 2, 3, 4, 5 |
| Data Integrity | 1, 3, 5, 6 |
| Non-Repudiation | 6 (with stated local-log limitation) |

## Additional usability fix (not a seventh security case)

The Activity Log previously had no clearly visible navigation controls. `MainActivity.kt` now places **Back** and **Logout** actions in the top app bar. Back returns to the prior screen; Logout clears the local session, records the logout event, and returns to Welcome. The updated app has built and installed; both controls are visible. Logout was manually tapped and returned to Welcome. Record tap-through evidence for Back before marking that action manually verified.

## Evidence checklist per case

- [ ] GitHub link to the relevant code and change.
- [ ] Before/after test steps and actual observed result.
- [ ] Unlisted YouTube video showing the live test (use `https://www.youtube.com/embed/VIDEO_ID` in the portfolio).
- [ ] 2–3 sentence reflection written/confirmed by the student.
- [ ] No real passwords, personal user data, or fabricated test result in screenshots/video.
