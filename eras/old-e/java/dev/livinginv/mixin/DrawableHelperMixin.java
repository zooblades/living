package dev.livinginv.mixin;

import dev.livinginv.Background;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the call that paints the survival inventory panel (176x166). */
@Mixin(DrawableHelper.class)
public abstract class DrawableHelperMixin {
    @Inject(method = "drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V", at = @At("HEAD"), cancellable = true)
    private  void livinginv$replacePanel(MatrixStack matrices, int x, int y, int u, int v, int width, int height, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (width == 176 && height == 166 && mc.currentScreen instanceof InventoryScreen) {
            if (Background.renderPanel(matrices, (InventoryScreen) mc.currentScreen, x, y, width, height)) {
                ci.cancel();
            }
        }
    }
}
