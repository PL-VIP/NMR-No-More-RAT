# Known gaps and limitations

No More RAT reduces risk from common Java-based remote-access malware, but code running inside the same JVM cannot create an unbypassable security boundary.

## Uncovered or partially covered paths

- JNI, JNA, native libraries, and direct operating-system calls;
- `MethodHandle` invocation and reflection performed from excluded/trusted bytecode; direct `Method.invoke`, `Constructor.newInstance`, and `setAccessible` call sites in mod classes are guarded;
- `Unsafe`, another JVM agent, retransformation, or bytecode changes after No More RAT transforms a class;
- code injected by Mixin directly into excluded Minecraft, Fabric, or Netty classes;
- custom protocol implementations that ultimately use an uncovered native path;
- `FileOutputStream` and `RandomAccessFile` constructors; common NIO writes are covered, but not every Java IO write;
- reading a secret without later passing it through a monitored channel;
- direct memory access, heap dumps, or secret capture through an uncovered path;
- removing the agent JVM argument, replacing the agent JAR, or deliberately corrupting its configuration;
- reliable attribution after a library completely loses the original callback code/class context.

## HTTPS

The agent controls the start of known HTTP/HTTPS calls, but it is not a man-in-the-middle proxy. It does not disable certificate validation and does not read encrypted transport data. For `HttpClient` and `HttpURLConnection`, it obtains the method and URI/URL before transmission. Some third-party libraries do not expose the destination or body reliably, so the log may contain an API call site instead of a complete address. An event must not be interpreted as full payload inspection.

## Files

Checks run before known NIO writes. The agent does not track complete data flow, so it does not label an ordinary `Files.copy` call as a download unless the network source is reliably known. It does not yet calculate the SHA-256 of a completed download because the current hook runs before the operation. This is an explicit unimplemented feature, not hidden placeholder behavior.

## Processes and secrets

A process is blocked before `Runtime.exec` or `ProcessBuilder.start`. The logged target is currently the API call name; command arguments and environment variables are not recorded. This limits diagnostics but prevents accidental secret logging. The redactor also masks common tokens, passwords, Bearer values, and JWTs.

## Recommended layered defense

Use a separate operating-system account, restrict directory permissions, enable a default-deny system firewall, install mods only from trusted sources, verify SHA-256 hashes, and run Minecraft in a sandbox or container where practical. Strong egress guarantees require enforcement outside the JVM.

## Local policy data

For supported NIO write paths, No More RAT denies untrusted mod attempts to alter files below `config/no-more-rat/`, including `rules.json` and the extracted agent. The trusted No More RAT UI is excluded from its own call-site hooks so it can save a decision. This is a defence against ordinary instrumented Java mod code, not protection against native code, an additional agent, an uninstrumented file API, or another process with access to the same user account.
