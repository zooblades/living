package dev.livinginv.mixin;

import dev.livinginv.Background;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/** Replaces the call that paints the survival inventory panel (176x166). */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Unique
    private static final Identifier LIVINGINV_PANEL = Identifier.tryParse("minecraft:textures/gui/container/inventory.png");

    @Inject(method = "drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void livinginv$replacePanel(Function<Identifier, RenderLayer> layers, Identifier texture, int x, int y,
                                        float u, float v, int width, int height, int textureWidth, int textureHeight,
                                        CallbackInfo ci) {
        if (width == 176 && height == 166 && LIVINGINV_PANEL.equals(texture)
                && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen screen) {
            if (Background.renderPanel((DrawContext) (Object) this, screen, x, y, width, height)) {
                ci.cancel();
            }
        }
    }
}
