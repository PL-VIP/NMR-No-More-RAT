package dev.nmrfirewall.fabric;

import java.nio.file.Path;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class AgentSetupScreen extends Screen {
    private final Screen parent;
    private String argument = "";
    private Text status = Text.translatable("no_more_rat.setup.preparing");

    AgentSetupScreen(Screen parent) {
        super(Text.translatable("no_more_rat.setup.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        try {
            Path agent = EmbeddedAgentInstaller.extract();
            argument = EmbeddedAgentInstaller.jvmArgument(agent);
            status = Text.translatable("no_more_rat.setup.ready");
        } catch (Exception failure) {
            status = Text.translatable("no_more_rat.setup.extract_failed", failure.getMessage());
        }
        int y = height / 2 + 72;
        ButtonWidget copy = ButtonWidget.builder(Text.translatable("no_more_rat.setup.copy"), b -> copyArgument())
                .dimensions(width / 2 - 154, y, 150, 20).build();
        copy.active = !argument.isBlank();
        addDrawableChild(copy);
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.setup.continue_unprotected"), b -> close())
                .dimensions(width / 2 + 4, y, 150, 20).build());
    }

    private void copyArgument() {
        client.keyboard.setClipboard(argument);
        status = Text.translatable("no_more_rat.setup.copied");
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 112, 0xFF5555);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.setup.explanation"),
                width / 2, height / 2 - 84, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.setup.step1"),
                width / 2, height / 2 - 58, 0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.setup.step2"),
                width / 2, height / 2 - 42, 0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.setup.step3"),
                width / 2, height / 2 - 26, 0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("no_more_rat.setup.verify"),
                width / 2, height / 2 - 4, 0xFFE080);
        String shownArgument = argument.isBlank()
                ? Text.translatable("no_more_rat.setup.argument_unavailable").getString()
                : argument;
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(shownArgument, Math.max(100, width - 40)),
                width / 2, height / 2 + 20, 0xA0D8FF);
        context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(status.getString(), Math.max(100, width - 40)),
                width / 2, height / 2 + 42, 0xC0C0C0);
    }

    @Override public void close() { client.setScreen(parent); }
}
