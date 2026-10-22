package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyRule;
import dev.nmrfirewall.core.PolicyStore;
import dev.nmrfirewall.core.SecurityRequest;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class FirewallPromptScreen extends Screen {
    private final Screen parent;
    private final SecurityRequest request;
    private final CompletableFuture<Decision> result;
    private boolean details;

    FirewallPromptScreen(Screen parent, SecurityRequest request, CompletableFuture<Decision> result) {
        super(Text.translatable("no_more_rat.prompt.title"));
        this.parent = parent; this.request = request; this.result = result;
    }

    @Override protected void init() {
        int y = height / 2 + 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.block"), b -> finish(Decision.DENY))
                .dimensions(width / 2 - 206, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.allow_once"), b -> finish(Decision.ALLOW))
                .dimensions(width / 2 - 102, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_allow"), b -> persist(Decision.ALLOW))
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

    void dismissIfOpen() {
        if (client.currentScreen == this) client.setScreen(parent);
    }

    @Override public void close() { finish(Decision.DENY); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 72, 0xFF5555);
        String method = request.parameters().getOrDefault("method", "");
        Text line1 = request.operation() == dev.nmrfirewall.core.OperationType.HTTP
                ? Text.translatable("no_more_rat.prompt.http", request.source().name(), method.isBlank() ? "HTTP" : method)
                : Text.translatable("no_more_rat.prompt.operation", request.source().name(), request.operation());
        context.drawCenteredTextWithShadow(textRenderer, line1, width / 2, height / 2 - 42, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.target", request.target()), width / 2, height / 2 - 26, 0xFFE0A0);
        if (details) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.source", request.source().callerClass()), width / 2, height / 2 - 10, 0xC0C0C0);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("no_more_rat.prompt.identity", request.source().confidence(), request.source().sha256()), width / 2, height / 2 + 4, 0xC0C0C0);
        }
    }
}
