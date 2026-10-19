package dev.livinginv.e2e;

import dev.livinginv.Background;
import dev.livinginv.LivingInvClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the survival inventory twice (vanilla, then with the mod's animation) and takes screenshots. */
public class PanelTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
            context.waitTicks(10);
            context.takeScreenshot("1-vanilla");

            // test image: rainbow gradient with a black ring
            Path file = LivingInvClient.dir.resolve("test.png");
            try {
                BufferedImage img = new BufferedImage(400, 225, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 225; y++)
                    for (int x = 0; x < 400; x++)
                        img.setRGB(x, y, Color.HSBtoRGB((x + y) / 625f, 0.75f, 0.95f));
                Graphics2D g = img.createGraphics();
                g.setColor(Color.BLACK);
                g.setStroke(new java.awt.BasicStroke(6));
                g.drawOval(150, 60, 100, 100);
                g.dispose();
                ImageIO.write(img, "png", file.toFile());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            context.runOnClient(client -> {
                LivingInvClient.config.selected = "test.png";
                Background.reload();
            });
            context.waitTicks(60);
            context.takeScreenshot("2-modded");
        }
    }
}
