package dev.livinginv.mixin;

import dev.livinginv.Background;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the single call that paints the survival inventory panel
 * (textures/gui/container/inventory.png, 176x166) and paints the animation instead.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Unique
    private static final Identifier LIVINGINV_PANEL = Identifier.tryParse("minecraft:textures/gui/container/inventory.png");

    @Inject(method = "drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void livinginv$replacePanel(Identifier texture, int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        if (width == 176 && height == 166 && LIVINGINV_PANEL.equals(texture)
                && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen screen) {
            if (Background.renderPanel((DrawContext) (Object) this, screen, x, y, width, height)) {
                ci.cancel();
            }
        }
    }
}
