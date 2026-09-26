# Security Policy

## Reporting a vulnerability

**Do not open a public issue for security problems.**

Email: use GitHub's private vulnerability reporting
(`Security → Report a vulnerability`) on this repository.

Include: affected version/tag, device + Android version, reproduction steps,
and impact. You will get an acknowledgment within 72 hours.

## Security model

- UChat never requires root. The Ubuntu environment runs via proot in
  app-private storage.
- Downloaded assets (rootfs, proot) are pinned by SHA-256 and verified before
  use. A mismatch aborts installation.
- Secrets (API keys, tokens) are stored encrypted with Android Keystore
  (AES/GCM). They are never written to logs, diagnostics, exports, the
  database, or preferences.
- All archive extraction enforces path-traversal protection and size limits.
- Errors shown to users go through secret redaction before display or copy.

## What we do NOT accept in the repo

- Keystores, signing passwords, API keys or tokens of any kind
  (CI enforces this with gitleaks).
