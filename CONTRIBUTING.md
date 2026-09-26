# Contributing to UChat

Thank you for considering a contribution!

## Ground rules

1. **No secrets in commits.** Tokens, keystores and passwords are rejected by
   CI (gitleaks) — and if you leak a real one, rotate it immediately.
2. **Release builds happen in GitHub Actions only.** Do not commit build
   artifacts. Debug validation locally is fine (`./gradlew testDebugUnitTest`).
3. **No fake functionality.** The app must use the real Ubuntu userspace and
   real processes; mocks belong only in tests.
4. **Argument arrays over shell strings.** Anywhere a process is launched,
   avoid string concatenation (see `Proot.kt`).
5. **Every user-facing failure is an `AppError`** with a copyable, redacted
   diagnostic.

## Dev setup

```bash
git clone https://github.com/SecretArrow/UChat.git
cd UChat/android
./gradlew assembleDebug        # debug builds only
./gradlew testDebugUnitTest    # unit tests
./gradlew lint                 # Android lint
```

## Formatting

Kotlin formatting is enforced with ktfmt via Spotless:

```bash
./gradlew spotlessApply   # or push to main and let autofix.yml do it
```

## Versioning & releases

Never edit `versionName`/`versionCode` manually. Push a tag:

```bash
git tag -a v1.2.3 -m "release 1.2.3"
git push origin v1.2.3
```

The release workflow builds, signs, checks and publishes automatically.

## PR checklist

- [ ] Unit tests for new logic (`app/src/test`)
- [ ] `./gradlew spotlessCheck` passes
- [ ] No TODO placeholders in critical paths
- [ ] Strings externalized (values/ + values-in/)
- [ ] Android edge cases considered (Doze, OEM killers, noexec, ABI differences)
