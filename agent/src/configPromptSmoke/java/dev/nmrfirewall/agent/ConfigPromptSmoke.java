package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Dialog-only test: does not execute the operation or save an allow rule. */
public final class ConfigPromptSmoke {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Render thread");
        Path root = Files.createTempDirectory(Path.of("build"), "config-dialog-test-");
        Path firewall = Files.createDirectories(root.resolve("config/no-more-rat"));
        Path target = root.resolve("config/demo/settings.json");
        var source = new SourceIdentity("nmr-test", "NMR harmless dialog test — no operation will run", "test", "fixture", "a".repeat(64), "Fixture", SourceIdentity.Confidence.HIGH);
        var request = new SecurityRequest(OperationType.FILE_WRITE, source, target.toAbsolutePath().toString(), Map.of("api", "java.nio.file.Files#writeString"));
        System.out.println("Fixture eligibility=" + ConfigWritePrompt.eligible(request, firewall)
                + "; headless=" + java.awt.GraphicsEnvironment.isHeadless());
        System.out.println("Fixture root=" + firewall.toAbsolutePath().normalize() + "; real=" + firewall.toRealPath());
        var result = ConfigWritePrompt.ask(request, firewall, 60).get(65, TimeUnit.SECONDS);
        if (Files.exists(target)) throw new AssertionError("The dialog test must never write the requested file");
        System.out.println("NMR_CONFIG_DIALOG_OK: " + result + "; no operation executed; no rule saved");
    }
}
