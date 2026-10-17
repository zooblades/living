package dev.livinginv.mixin;

import dev.livinginv.Background;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    // Runs after the vanilla dim/blur, before the inventory panel and slots are drawn.
    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void livinginv$drawAnimation(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        Background.render(context);
    }
}
