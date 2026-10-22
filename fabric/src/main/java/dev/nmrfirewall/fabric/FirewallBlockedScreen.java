package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.SecurityRequest;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class FirewallBlockedScreen extends Screen {
    private final Screen parent;
    private final SecurityRequest request;

    FirewallBlockedScreen(Screen parent, SecurityRequest request) {
        super(Text.translatable("no_more_rat.blocked.title"));
        this.parent = parent;
        this.request = request;
    }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.understood"), button -> close())
                .dimensions(width / 2 - 60, height / 2 + 54, 120, 20).build());
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        String method = request.parameters().getOrDefault("method", "");
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 74, 0xFF5555);
        Text action = request.operation() == OperationType.HTTP
                ? Text.translatable("no_more_rat.blocked.http", request.source().name(), method.isBlank() ? "HTTP" : method)
                : Text.translatable("no_more_rat.blocked.operation", request.source().name(), request.operation());
        context.drawCenteredTextWithShadow(textRenderer, action, width / 2, height / 2 - 42, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(Text.translatable("no_more_rat.prompt.target", request.target()).getString(), Math.max(100, width - 40)),
                width / 2, height / 2 - 22, 0xFFE080);
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(Text.translatable("no_more_rat.blocked.class", request.source().callerClass()).getString(), Math.max(100, width - 40)),
                width / 2, height / 2 - 2, 0xC0C0C0);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.blocked.render_thread"),
                width / 2, height / 2 + 20, 0xA0D8FF);
    }

    @Override public void close() { client.setScreen(parent); }
}
