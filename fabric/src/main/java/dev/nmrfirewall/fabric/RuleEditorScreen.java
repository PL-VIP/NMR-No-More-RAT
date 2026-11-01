package dev.nmrfirewall.fabric;

import dev.nmrfirewall.core.Decision;
import dev.nmrfirewall.core.OperationType;
import dev.nmrfirewall.core.PolicyConfig;
import dev.nmrfirewall.core.PolicyRule;
import dev.nmrfirewall.core.PolicyStore;
import java.nio.file.Path;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class RuleEditorScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget modId, hash, target, operation;
    private Text status = Text.translatable("no_more_rat.rule.wildcard_help");
    private int statusY;

    RuleEditorScreen(Screen parent) { super(Text.translatable("no_more_rat.rule.title")); this.parent = parent; }

    @Override protected void init() {
        String[] previous = modId == null ? null : new String[]{modId.getText(), hash.getText(), operation.getText(), target.getText()};
        clearChildren();
        int panel = Math.min(310, width - 24);
        int x = (width - panel) / 2;
        int top = Math.max(32, Math.min(54, height - 200));
        int spacing = Math.max(22, Math.min(28, (height - top - 90) / 4));
        modId = field(x, top, "no_more_rat.rule.mod_id");
        hash = field(x, top + spacing, "no_more_rat.rule.hash");
        operation = field(x, top + spacing * 2, "no_more_rat.rule.operation");
        target = field(x, top + spacing * 3, "no_more_rat.rule.target");
        int actionsY = top + spacing * 4;
        statusY = actionsY + 52;
        if (previous != null) {
            modId.setText(previous[0]); hash.setText(previous[1]); operation.setText(previous[2]); target.setText(previous[3]);
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_allow"), b -> save(Decision.ALLOW))
                .dimensions(x, actionsY, (panel - 8) / 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_block"), b -> save(Decision.DENY))
                .dimensions(x + (panel + 8) / 2, actionsY, (panel - 8) / 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.cancel"), b -> close())
                .dimensions(width / 2 - 50, actionsY + 24, 100, 20).build());
    }

    private TextFieldWidget field(int x, int y, String translationKey) {
        Text label = Text.translatable(translationKey);
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, Math.min(310, width - 24), 20, label);
        field.setMaxLength(4096);
        field.setPlaceholder(label); addDrawableChild(field); return field;
    }

    private void save(Decision decision) {
        try {
            OperationType type = "*".equals(operation.getText().trim()) || operation.getText().isBlank()
                    ? null : OperationType.valueOf(operation.getText().trim().toUpperCase(java.util.Locale.ROOT));
            String hashValue = normalize(hash.getText());
            if (decision == Decision.ALLOW && hashValue == null) {
                status = Text.translatable("no_more_rat.rule.hash_required"); return;
            }
            Path file = NoMoreRatClient.configDirectory().resolve("rules.json");
            PolicyStore store = new PolicyStore(); PolicyConfig config = store.load(file);
            config.rules.add(0, new PolicyRule(null, true, decision, type, normalize(modId.getText()), null,
                    hashValue, normalize(target.getText()), type == OperationType.PROCESS_START));
            store.save(file, config); close();
        } catch (Exception failure) { status = Text.translatable("no_more_rat.error_detail", failure.getMessage()); }
    }

    private static String normalize(String value) { return value == null || value.isBlank() || "*".equals(value.trim()) ? null : value.trim(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xF0182029);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        int y = statusY;
        for (var line : textRenderer.wrapLines(status, Math.max(80, width - 24))) {
            if (y > height - 10) break;
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, 0xFFFFCC66);
            y += 10;
        }
    }
    @Override public void close() { client.setScreen(parent); }
}
