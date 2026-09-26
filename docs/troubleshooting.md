# Troubleshooting

Practical fixes for the problems users actually hit. If your issue is not
here, open a GitHub issue with the diagnostics text (Diagnostics → Copy
diagnostics — it contains no secrets).

## Ubuntu installation failed

**Symptom:** the wizard fails at a step and offers Retry.

- **Network interrupted mid-download** → tap Retry; the download resumes from
  where it stopped (partial files are kept).
- **"Checksum mismatch"** → the download is corrupted. Retry the download; if
  it persists, your network or a proxy is altering files (try another network).
- **"Not enough storage"** → free space. Ubuntu needs roughly
  **1.2–1.5 GB free** at install time and grows with packages. Check
  Settings → Storage.
- **Stuck at apt update / package install** → your DNS may be blocked. Toggle
  Wi-Fi/mobile data and Retry; UChat refreshes `/etc/resolv.conf` on init.

## proot failure / "executable not found"

- On some devices with hardened kernels, proot may need `PROOT_NO_SECCOMP=1`
  (UChat sets this by default).
- **noexec filesystem**: rare, but if app storage is mounted noexec the
  environment cannot start. Reinstall UChat so Android recreates its data dir.

## Permission denied

- UChat uses app-private storage only — no storage permission is required for
  core features. For shared import/export, pick a folder via the system file
  picker (SAF).
- Notification permission denied → background process notifications will not
  show. Everything else works. Re-enable in system settings.

## Android killed my background process

Android (and some OEMs) limit background execution. UChat uses a foreground
service — the approved mechanism — but:

- Disable battery optimization for UChat (Settings → Battery → Unrestricted).
- **Samsung:** Settings → Battery → Background usage limits → remove UChat.
- **Xiaomi/MIUI:** Autostart on, Battery saver → No restrictions.
- **OPPO/vivo/OnePlus:** allow autostart + lock UChat in recents.
- Long downloads survive Doze via the foreground service notification.

## OpenCode / Claude / Node missing

Open Tools → check the tool status. Reinstall from there. The tool installers
run inside Ubuntu and verify the executable after install. If npm is broken:
run `apt-get install -y nodejs npm` inside a terminal session.

## Git authentication problems

- Use SSH keys where possible (generate with `ssh-keygen` inside Ubuntu).
- If a remote uses a token, inject it per-command via environment; never write
  tokens into `.git/config` on shared storage, and never paste them into logs —
  UChat redacts them from diagnostics automatically.

## ZIP / archive extraction failure

Archive imports are scanned for path traversal and size limits; a rejected
archive means it contains unsafe entries (`../`, absolute paths). Extract with
a tool that sanitizes the archive first.

## Insufficient storage warning

Settings → Storage shows per-directory usage. Cleanup removes apt/npm caches
and temporary files. UChat **never** deletes your projects automatically.

## Emulator (x86_64) notes

Android emulators work with the `x86_64` APK. Virtualization can be slow with
the apt phase; give the installer a few extra minutes.
