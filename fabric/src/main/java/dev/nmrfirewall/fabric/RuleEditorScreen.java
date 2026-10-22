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

    RuleEditorScreen(Screen parent) { super(Text.translatable("no_more_rat.rule.title")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 155;
        modId = field(x, 54, "no_more_rat.rule.mod_id");
        hash = field(x, 82, "no_more_rat.rule.hash");
        operation = field(x, 110, "no_more_rat.rule.operation");
        target = field(x, 138, "no_more_rat.rule.target");
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_allow"), b -> save(Decision.ALLOW))
                .dimensions(x, 174, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.always_block"), b -> save(Decision.DENY))
                .dimensions(x + 162, 174, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.action.cancel"), b -> close())
                .dimensions(width / 2 - 50, 204, 100, 20).build());
    }

    private TextFieldWidget field(int x, int y, String translationKey) {
        Text label = Text.translatable(translationKey);
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 310, 20, label);
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
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 22, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 234, 0xFFCC66);
    }
    @Override public void close() { client.setScreen(parent); }
}
