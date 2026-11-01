# Security policy

## Supported versions

Security fixes target the current release on `main`: **No More RAT 1.2.4** for every Minecraft version shipped with that release (1.20.1–1.21.11 and 26.1–26.3). The `1.0.0-1.21.4` and `1.1.0-1.21.4` branches are retained as historical source for older releases and do not receive new security fixes.

## Reporting a vulnerability

Do not publish working bypass details, credentials, private logs, or malicious mod samples in a public issue.

When this project is hosted on GitHub, use a private security advisory. Otherwise, contact the repository maintainer through a private channel listed by the hosting platform. Include:

- the affected version and Java/Fabric versions;
- the controlled operation and API path;
- whether the operation occurred before a decision;
- a minimal reproduction without real tokens or personal data;
- relevant redacted logs.

## Scope

Reports about a documented limitation are welcome when they include a practical mitigation or a safe new interception point. Native code, JNI/JNA, `Unsafe`, replacement agents, and malicious modifications to No More RAT itself are outside the guaranteed boundary described in [docs/SECURITY_LIMITATIONS.md](../docs/SECURITY_LIMITATIONS.md).
