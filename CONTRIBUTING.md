# Contributing

Thank you for helping improve No More RAT.

## Before opening a change

- Use Java 21.
- Keep enforcement fail-closed: an exception, timeout, missing identity, or missing UI must not silently allow a controlled operation.
- Do not claim an API is protected without a test proving that denial happens before the original call.
- Do not weaken TLS verification, log credentials, or add telemetry.

## Build and test

```powershell
.\gradlew.bat clean build
```

Every change to bytecode instrumentation should include or update an agent test. Network coverage should verify both the API and method where relevant, especially GET and POST.

## Translations

English is the source and fallback locale. Copy:

```text
fabric/src/main/resources/assets/no_more_rat/lang/en_us.json
```

to the correct Minecraft locale filename, translate values only, and preserve the complete key set. Keep security language precise: “blocked” means the original operation was not executed.

## Pull requests

Describe the threat or usability problem, the behavior before and after the change, and the tests run. Keep unrelated refactors separate from security fixes.

For security vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of opening a public issue with exploit details.
