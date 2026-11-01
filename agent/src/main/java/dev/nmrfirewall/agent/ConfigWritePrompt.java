package dev.nmrfirewall.agent;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.SecurityRequest;
import dev.nmrfirewall.core.SourceIdentity;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Separate event thread: works before Minecraft's screen system is initialized. */
final class ConfigWritePrompt {
    private static CompletableFuture<Decision> promptTail = CompletableFuture.completedFuture(Decision.DENY);
    private ConfigWritePrompt() { }

    static boolean eligible(SecurityRequest request, Path firewallDirectory) {
        if (request.operation() != OperationType.FILE_WRITE
                || request.source().confidence() != SourceIdentity.Confidence.HIGH
                || !request.source().sha256().matches("[a-fA-F0-9]{64}")) return false;
        try {
            Path firewall = firewallDirectory.toAbsolutePath().normalize();
            Path config = firewall.getParent();
            if (config == null || !firewall.getFileName().toString().equals("no-more-rat")
                    || !config.getFileName().toString().equals("config")) return false;
            Path target = Path.of(request.target()).toAbsolutePath().normalize();
            // The agent's data is never delegated to an untrusted mod.  Every other
            // file write configured as ASK gets a desktop decision, including writes
            // made before Minecraft's own screen system exists.
            return !target.startsWith(firewall);
        } catch (Exception invalid) { return false; }
    }

    static CompletableFuture<Decision> ask(SecurityRequest request, Path firewallDirectory, int seconds) {
        if (!eligible(request, firewallDirectory)) {
            return CompletableFuture.completedFuture(Decision.DENY);
        }
        synchronized (ConfigWritePrompt.class) {
            CompletableFuture<Decision> queued = promptTail.handle((ignored, failure) -> Decision.DENY)
                    .thenCompose(ignored -> show(request, firewallDirectory, seconds));
            promptTail = queued.handle((ignored, failure) -> Decision.DENY);
            return queued;
        }
    }

    /** One native dialog at a time: concurrent startup writes must not silently deny each other. */
    private static CompletableFuture<Decision> show(SecurityRequest request, Path firewallDirectory, int seconds) {
        if (GraphicsEnvironment.isHeadless()) return CompletableFuture.completedFuture(Decision.DENY);
        return showSwing(request, polish(firewallDirectory), seconds);
    }

    private static CompletableFuture<Decision> showSwing(SecurityRequest request, boolean polish, int seconds) {
        CompletableFuture<Decision> result = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            SwingUtilities.invokeLater(() -> {
                if (result.isDone()) return;
                JDialog dialog;
                try { dialog = new JDialog(); }
                catch (Throwable unavailable) { result.complete(Decision.DENY); return; }
                JLabel countdown = new JLabel();
                Runnable updateCountdown = () -> {
                    long remaining = Math.max(0, (deadline - System.nanoTime() + 999_999_999L) / 1_000_000_000L);
                    countdown.setText(polish ? " Automatyczna odmowa za " + remaining + " s " : " Automatic denial in " + remaining + " s ");
                    if (remaining == 0) result.complete(Decision.DENY);
                };
                Timer timer = new Timer(250, event -> updateCountdown.run());
                result.whenComplete((decision, failure) -> SwingUtilities.invokeLater(() -> { timer.stop(); dialog.dispose(); }));
                try {
                    dialog.setTitle(polish ? "No More RAT — zapis pliku" : "No More RAT — file write");
                    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                    dialog.addWindowListener(new WindowAdapter() {
                        @Override public void windowClosing(WindowEvent event) { result.complete(Decision.DENY); }
                    });
                    String text = message(request, polish);
                    JTextArea details = new JTextArea(text);
                    details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
                    JScrollPane scroll = new JScrollPane(details); scroll.setPreferredSize(new Dimension(620, 260));
                    JButton deny = new JButton(polish ? "Odmów" : "Deny");
                    JButton allow = new JButton(polish ? "Zezwól w tej sesji" : "Allow for this session");
                    deny.addActionListener(event -> result.complete(Decision.DENY));
                    allow.addActionListener(event -> result.complete(System.nanoTime() < deadline ? Decision.ALLOW : Decision.DENY));
                    JPanel actions = new JPanel(); actions.add(deny); actions.add(allow);
                    updateCountdown.run(); dialog.add(countdown, BorderLayout.NORTH);
                    dialog.add(scroll, BorderLayout.CENTER); dialog.add(actions, BorderLayout.SOUTH);
                    dialog.getRootPane().setDefaultButton(deny);
                    dialog.pack(); dialog.setLocationRelativeTo(null); dialog.setAlwaysOnTop(true);
                    if (result.isDone() || System.nanoTime() >= deadline) { result.complete(Decision.DENY); dialog.dispose(); return; }
                    timer.start(); dialog.setVisible(true); deny.requestFocusInWindow();
                } catch (Throwable unavailable) { result.complete(Decision.DENY); }
            });
        } catch (Throwable unavailable) { result.complete(Decision.DENY); }
        return result;
    }

    private static String message(SecurityRequest request, boolean polish) {
        return (polish ? "Mod prosi o zapis pliku lub utworzenie katalogu.\nOperacja czeka na Twoją decyzję.\n\nMod: "
                : "A mod requests a file write or directory creation.\nThe operation is waiting for your decision.\n\nMod: ")
                + request.source().name() + " [" + request.source().modId() + "]\n"
                + "SHA-256: " + request.source().sha256() + "\n"
                + (polish ? "Ścieżka: " : "Path: ") + request.target() + "\n"
                + "API: " + request.parameters().getOrDefault("api", "FILE_WRITE") + "\n\n"
                + (polish ? "Zgoda obowiązuje do zamknięcia gry dla tego samego moda, API i celu. "
                + "Zamknięcie okna lub upływ czasu oznacza odmowę."
                : "Approval lasts until the game closes for this mod, API and target. "
                + "Closing the window or timing out denies it.");
    }

    private static boolean polish(Path firewallDirectory) {
        try (var lines = Files.lines(firewallDirectory.toAbsolutePath().getParent().getParent().resolve("options.txt"))) {
            return lines.limit(1024).anyMatch(line -> line.equals("lang:pl_pl"));
        } catch (Exception unavailable) { return false; }
    }
}
