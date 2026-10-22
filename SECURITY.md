# Security policy

## Supported version

Security fixes currently target the latest release on the Minecraft 1.21.4 branch.

## Reporting a vulnerability

Do not publish working bypass details, credentials, private logs, or malicious mod samples in a public issue.

When this project is hosted on GitHub, use a private security advisory. Otherwise, contact the repository maintainer through a private channel listed by the hosting platform. Include:

- the affected version and Java/Fabric versions;
- the controlled operation and API path;
- whether the operation occurred before a decision;
- a minimal reproduction without real tokens or personal data;
- relevant redacted logs.

## Scope

Reports about a documented limitation are welcome when they include a practical mitigation or a safe new interception point. Native code, JNI/JNA, `Unsafe`, replacement agents, and malicious modifications to No More RAT itself are outside the guaranteed boundary described in [docs/SECURITY_LIMITATIONS.md](docs/SECURITY_LIMITATIONS.md).
