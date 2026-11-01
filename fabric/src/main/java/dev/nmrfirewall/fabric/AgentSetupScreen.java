package dev.nmrfirewall.fabric;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;

final class AgentSetupScreen extends Screen {
    private final Screen parent;
    private String argument = "";
    private Text status = Text.translatable("no_more_rat.setup.preparing");
    private boolean extractionAttempted;
    private List<OrderedText> lines = List.of();
    private int page, perPage, pages;

    AgentSetupScreen(Screen parent) {
        super(Text.translatable("no_more_rat.setup.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearChildren();
        if (!extractionAttempted) try {
            extractionAttempted = true;
            Path agent = EmbeddedAgentInstaller.extract();
            argument = EmbeddedAgentInstaller.jvmArgument(agent);
            status = Text.translatable("no_more_rat.setup.ready");
        } catch (Exception failure) {
            status = Text.translatable("no_more_rat.setup.extract_failed", failure.getMessage());
        }
        int panel = Math.min(500, width - 24);
        int left = (width - panel) / 2;
        int half = (panel - 4) / 2;
        int y = height - 28;
        ButtonWidget copy = ButtonWidget.builder(Text.translatable("no_more_rat.setup.copy"), b -> copyArgument())
                .dimensions(left, y, half, 20).build();
        copy.active = !argument.isBlank();
        addDrawableChild(copy);
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.setup.continue_unprotected"), b -> close())
                .dimensions(left + half + 4, y, half, 20).build());
        List<OrderedText> wrapped = new ArrayList<>();
        for (String key : List.of("explanation", "step1", "step2", "step3", "verify")) {
            wrapped.addAll(textRenderer.wrapLines(Text.translatable("no_more_rat.setup." + key), panel));
            wrapped.add(Text.empty().asOrderedText());
        }
        wrapped.addAll(textRenderer.wrapLines(argument.isBlank()
                ? Text.translatable("no_more_rat.setup.argument_unavailable") : Text.literal(argument), panel));
        wrapped.addAll(textRenderer.wrapLines(status, panel));
        lines = List.copyOf(wrapped);
        perPage = Math.max(1, (height - 92) / 12);
        pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; init(); })
                .dimensions(left, height - 54, 30, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; init(); })
                .dimensions(left + panel - 30, height - 54, 30, 20).build()).active = page < pages - 1;
    }

    private void copyArgument() {
        client.keyboard.setClipboard(argument);
        status = Text.translatable("no_more_rat.setup.copied");
        init();
        page = pages - 1;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0182029);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFF5555);
        int left = (width - Math.min(500, width - 24)) / 2;
        int y = 32;
        for (int i = page * perPage; i < Math.min(lines.size(), (page + 1) * perPage); i++) {
            context.drawTextWithShadow(textRenderer, lines.get(i), left, y, 0xFFE0E0E0);
            y += 12;
        }
        context.drawCenteredTextWithShadow(textRenderer, (page + 1) + "/" + pages, width / 2, height - 48, 0xFFAAAAAA);
    }

    @Override public void close() { client.setScreen(parent); }
}
