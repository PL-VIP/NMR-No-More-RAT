# No More RAT 1.2.1

## Commit summary

Allow configuration consent before Minecraft's GUI is available.

## Release description

- Fix `ASK` for supported configuration writes during startup: show an independent permission window instead of automatically denying on the render thread.
- Display mod identity, SHA-256, full target path, API and remaining time; support Polish and English.
- Keep explicit blocks, protected NMR data, timeout and window-close denial intact. Consent applies only to the current operation.
- Keep ordinary configuration reads unintercepted.

Update both the mod and agent. This build targets **Minecraft 1.21.4**. It does not guarantee startup when a mod receives a denial and cannot handle it.

Validation: agent tests and real-agent enforcement checks passed; Minecraft 1.21.4 JAR built and verified. The host could not run the symbolic-link test, and the optional external-sample test was skipped. The suspicious test JAR was not executed.

The harmless desktop-dialog fixture returned `ALLOW` while the simulated render thread waited. It confirmed that the requested file was not created and no permission rule was saved. This checks the new dialog separately from any third-party mod.
