package dev.livinginv.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.livinginv.Background;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the call that paints the survival inventory panel (176x166). */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @Unique
    private static final Identifier LIVINGINV_PANEL = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    @Inject(method = "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V",
            at = @At("HEAD"), cancellable = true)
    private void livinginv$replacePanel(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
                                        int width, int height, int textureWidth, int textureHeight, CallbackInfo ci) {
        if (Background.inInventoryBackground && width == 176 && height == 166 && LIVINGINV_PANEL.equals(texture)) {
            if (Background.renderPanel((GuiGraphicsExtractor) (Object) this, x, y, width, height)) {
                ci.cancel();
            }
        }
    }
}
