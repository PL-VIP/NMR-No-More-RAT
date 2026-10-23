package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

final class FirewallPromptScreen extends Screen {
    private final Screen parent;
    private final SecurityRequest request;
    private final CompletableFuture<Decision> result;
    private final long deadlineNanos;
    private final boolean preview;
    private int page;
    private boolean confirmingTrust;
    private List<OrderedText> lines = List.of();
    private int perPage;
    private int pages;

    FirewallPromptScreen(Screen parent, SecurityRequest request, CompletableFuture<Decision> result) {
        this(parent, request, result, false);
    }

    private FirewallPromptScreen(Screen parent, SecurityRequest request, CompletableFuture<Decision> result, boolean preview) {
        super(Text.translatable("no_more_rat.prompt.title"));
        this.parent = parent; this.request = request; this.result = result;
        this.preview = preview;
        this.deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(preview ? 300 : timeoutSeconds());
    }

    static FirewallPromptScreen preview(Screen parent) {
        var inspected = HttpInspection.url("https://example.invalid:8443/download.jar?key=fake-preview");
        var parameters = new java.util.HashMap<>(inspected.parameters());
        parameters.putAll(java.util.Map.of("method", "POST", "bodyBytes", "256", "sensitiveHeaders", "true",
                "contentType", "application/json", "redirects", "disabled"));
        var source = new SourceIdentity("preview", "NMR Preview", "demo", "no file", "unknown", "Preview", SourceIdentity.Confidence.UNKNOWN);
        return new FirewallPromptScreen(parent, new SecurityRequest(OperationType.HTTP, source, inspected.target(), parameters),
                new CompletableFuture<>(), true);
    }

    @Override protected void init() {
        clearChildren();
        int left = 12, gap = 4, half = (width - 24 - gap) / 2;
        int y = height - 76;
        button(t("action.block"), left, y, half, () -> finish(Decision.DENY));
        button(t("action.allow_once"), left + half + gap, y, half, () -> finish(Decision.ALLOW));
        ButtonWidget trust = button(t(confirmingTrust ? "action.confirm_trust" : "action.trust_limited"),
                left, y + 24, half, () -> {
                    if (confirmingTrust) persist(Decision.ALLOW);
                    else { confirmingTrust = true; page = 0; init(); }
                });
        trust.active = !preview && request.source().confidence() == SourceIdentity.Confidence.HIGH
                && request.source().sha256().matches("[a-fA-F0-9]{64}");
        button(t("action.always_block"), left + half + gap, y + 24, half, () -> persist(Decision.DENY)).active = !preview;
        List<Text> content = content();
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text row : content) wrapped.addAll(textRenderer.wrapLines(row, Math.max(80, width - 28)));
        lines = List.copyOf(wrapped);
        perPage = Math.max(1, (height - 122) / 12);
        pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        button(Text.literal("<"), left, height - 28, 30, () -> { page--; init(); }).active = page > 0;
        button(Text.literal(">"), left + 94, height - 28, 30, () -> { page++; init(); }).active = page < pages - 1;
    }

    private List<Text> content() {
        List<Text> rows = new ArrayList<>();
        if (preview) rows.add(t("prompt.preview_notice"));
        if (confirmingTrust) rows.add(t("prompt.trust_scope"));
        rows.add(Text.translatable("no_more_rat.prompt.operation", request.source().name(), request.operation()));
        rows.add(Text.translatable("no_more_rat.prompt.mod_id", request.source().modId()));
        rows.add(Text.translatable("no_more_rat.prompt.target", request.operation() == OperationType.HTTP
                ? HttpInspection.displayTarget(request.target()) : SecretRedactor.redact(request.target())));
        rows.add(Text.translatable("no_more_rat.prompt.risk", t("risk." + request.riskLevel().name().toLowerCase(Locale.ROOT))));
        if (request.operation() == OperationType.HTTP) {
            var p = request.parameters();
            rows.add(Text.translatable("no_more_rat.http.endpoint", p.getOrDefault("host", "?"), p.getOrDefault("port", "?")));
            rows.add(Text.translatable("no_more_rat.http.method", p.getOrDefault("method", "?")));
            rows.add(Text.translatable("no_more_rat.http.size", known(p.get("bodyBytes"))));
            rows.add(Text.translatable("no_more_rat.http.type", known(p.get("contentType"))));
            rows.add(t("http.body." + ("empty".equals(p.get("bodyInspection")) ? "empty" : "unknown")));
            rows.add(t("http.headers." + ("true".equals(p.get("sensitiveHeaders")) ? "sensitive" : "unknown")));
            rows.add(t("http.redirects." + switch (p.getOrDefault("redirects", "unknown")) {
                case "enabled" -> "enabled"; case "disabled" -> "disabled"; default -> "unknown";
            }));
            if ("http".equals(p.get("scheme"))) rows.add(t("http.plaintext"));
            if ("true".equals(p.get("urlPrivateParts"))) rows.add(t("http.private_url"));
            if ("true".equals(p.get("codeOrArchive"))) rows.add(t("http.archive"));
            rows.add(t("http.reason"));
        } else rows.add(t("consequence." + request.operation().name().toLowerCase(Locale.ROOT)));
        rows.add(Text.translatable("no_more_rat.prompt.identity", request.source().confidence(), request.source().sha256()));
        rows.add(Text.translatable("no_more_rat.prompt.source", request.source().callerClass()));
        return rows;
    }

    private ButtonWidget button(Text text, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(text, b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private void persist(Decision decision) {
        if (preview) { finish(Decision.DENY); return; }
        if (result.isDone() || System.nanoTime() >= deadlineNanos) { finish(Decision.DENY); return; }
        try {
            var source = request.source();
            PolicyStore store = new PolicyStore();
            var file = NoMoreRatClient.configDirectory().resolve("rules.json");
            PolicyConfig config = store.load(file);
            config.rules.add(0, new PolicyRule(null, true, decision, request.operation(), source.modId(),
                    source.version(), source.sha256(), PolicyRule.exactTarget(request.target()),
                    request.operation() == OperationType.PROCESS_START));
            store.save(file, config);
            finish(decision);
        } catch (Exception failure) { finish(Decision.DENY); }
    }

    private void finish(Decision decision) {
        if (System.nanoTime() >= deadlineNanos) decision = Decision.DENY;
        if (result.complete(decision)) client.setScreen(parent);
    }

    @Override public void tick() {
        if (System.nanoTime() >= deadlineNanos) finish(Decision.DENY);
    }

    private int secondsRemaining() {
        return (int) Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(
                Math.max(0L, deadlineNanos - System.nanoTime()) + TimeUnit.SECONDS.toNanos(1) - 1));
    }

    private static int timeoutSeconds() {
        try { return new PolicyStore().load(NoMoreRatClient.configDirectory().resolve("rules.json")).promptTimeoutSeconds; }
        catch (Exception failure) { return 15; }
    }

    void dismissIfOpen() { if (client.currentScreen == this) client.setScreen(parent); }
    @Override public void close() { finish(Decision.DENY); }
    private static Text t(String key) { return Text.translatable("no_more_rat." + key); }
    private static Text known(String value) { return value == null || "unknown".equals(value) ? t("http.unknown") : Text.literal(value); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFF7777);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.prompt.countdown", secondsRemaining()), width / 2, 26, 0xF0C674);
        int y = 44;
        for (int i = page * perPage; i < Math.min(lines.size(), (page + 1) * perPage); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), 14, y, 0xE1E8EF);
            y += 12;
        }
        context.drawTextWithShadow(textRenderer, (page + 1) + "/" + pages, 50, height - 23, 0xAAAAAA);
    }
}
