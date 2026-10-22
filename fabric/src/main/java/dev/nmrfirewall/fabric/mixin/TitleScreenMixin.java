package dev.nmrfirewall.fabric.mixin;

import dev.nmrfirewall.fabric.FirewallScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void nmr$addSecurityButton(CallbackInfo info) {
        addDrawableChild(ButtonWidget.builder(Text.translatable("no_more_rat.open"),
                button -> client.setScreen(new FirewallScreen((Screen) (Object) this)))
                .dimensions(width - 154, 8, 146, 20).build());
    }
}
