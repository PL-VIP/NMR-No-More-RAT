package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyRule;
import dev.nmrfirewall.core.PolicyStore;
import dev.nmrfirewall.core.SecurityRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class FirewallPromptScreen extends Screen {
    private final Screen parent;
    private final SecurityRequest request;
    private final CompletableFuture<Decision> result;
    private final long deadlineNanos;
    private boolean details;

    FirewallPromptScreen(Screen parent, SecurityRequest request, CompletableFuture<Decision> result) {
        super(Text.translatable("no_more_rat.prompt.title"));
        this.parent = parent; this.request = request; this.result = result;
        this.deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds());
    }

    @Override protected void init() {
        int y = height / 2 + 44;
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.block"), b -> finish(Decision.DENY))
                .dimensions(width / 2 - 206, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.allow_once"), b -> finish(Decision.ALLOW))
                .dimensions(width / 2 - 102, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.trust_limited"), b -> persist(Decision.ALLOW))
                .dimensions(width / 2 + 4, y, 112, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_block"), b -> persist(Decision.DENY))
                .dimensions(width / 2 + 122, y, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.details"), b -> details = !details)
                .dimensions(width / 2 - 50, y + 28, 100, 20).build());
    }

    private void persist(Decision decision) {
        try {
            var source = request.source();
            PolicyStore store = new PolicyStore(); var file = NoMoreRatClient.configDirectory().resolve("rules.json");
            PolicyConfig config = store.load(file);
            config.rules.add(0, new PolicyRule(null, true, decision, request.operation(), source.modId(),
                    source.version(), source.sha256(), request.target(), request.operation() == dev.nmrfirewall.core.OperationType.PROCESS_START));
            store.save(file, config); finish(decision);
        } catch (Exception ignored) { finish(Decision.DENY); }
    }

    private void finish(Decision decision) {
        if (result.complete(decision)) client.setScreen(parent);
    }

    @Override public void tick() {
        if (System.nanoTime() >= deadlineNanos) finish(Decision.DENY);
    }

    private int secondsRemaining() {
        long remaining = Math.max(0L, deadlineNanos - System.nanoTime());
        return (int) Math.max(0L, TimeUnit.NANOSECONDS.toSeconds(remaining + TimeUnit.SECONDS.toNanos(1) - 1));
    }

    private static int timeoutSeconds() {
        try { return new PolicyStore().load(NoMoreRatClient.configDirectory().resolve("rules.json")).promptTimeoutSeconds; }
        catch (Exception ignored) { return 15; }
    }

    void dismissIfOpen() {
        if (client.currentScreen == this) client.setScreen(parent);
    }

    @Override public void close() { finish(Decision.DENY); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 88, 0xFF5555);
        String method = request.parameters().getOrDefault("method", "");
        Text line1 = request.operation() == dev.nmrfirewall.core.OperationType.HTTP
                ? Text.translatable("no_more_rat.prompt.http", request.source().name(), method.isBlank() ? "HTTP" : method)
                : Text.translatable("no_more_rat.prompt.operation", request.source().name(), request.operation());
        context.drawCenteredTextWithShadow(textRenderer, line1, width / 2, height / 2 - 60, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.target", request.target()), width / 2, height / 2 - 44, 0xFFE0A0);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.risk", request.riskLevel()), width / 2, height / 2 - 28,
                request.riskLevel().ordinal() >= 2 ? 0xFF7777 : 0xFFE0A0);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(request.riskReason()), width / 2, height / 2 - 12, 0xD8DEE9);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.countdown", secondsRemaining()), width / 2, height / 2 + 4, 0xF0C674);
        if (details) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.source", request.source().callerClass()), width / 2, height / 2 + 20, 0xC0C0C0);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.identity", request.source().confidence(), request.source().sha256()), width / 2, height / 2 + 34, 0xC0C0C0);
        }
    }
}
