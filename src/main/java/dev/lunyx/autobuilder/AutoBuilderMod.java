package dev.lunyx.autobuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AutoBuilderMod implements ClientModInitializer {
    public static final String ID = "autobuilder";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public static KeyBinding OPEN, CONFIRM, ROTATE, STOP;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.autobuilder." + name, InputUtil.Type.KEYSYM, code, "category.autobuilder"));
    }

    @Override
    public void onInitializeClient() {
        OPEN = key("open", GLFW.GLFW_KEY_B);
        CONFIRM = key("confirm", GLFW.GLFW_KEY_G);
        ROTATE = key("rotate", GLFW.GLFW_KEY_R);
        STOP = key("stop", GLFW.GLFW_KEY_X);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.wasPressed()) {
                if (mc.player != null && mc.currentScreen == null) {
                    Builder.capture(mc);
                    mc.setScreen(new BuilderScreen());
                }
            }
            while (CONFIRM.wasPressed()) Builder.confirmPreview(mc);
            while (ROTATE.wasPressed()) if (Builder.mode == Builder.Mode.PREVIEW) Builder.rotate();
            while (STOP.wasPressed()) Builder.stop();
            Builder.tick(mc);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            List<Builder.Task> list = Builder.ghostTasks();
            if (list.isEmpty() || ctx.consumers() == null) return;
            MatrixStack ms = ctx.matrixStack();
            if (ms == null) return;
            Vec3d cam = ctx.camera().getPos();
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());
            float alpha = Builder.mode == Builder.Mode.BUILDING ? 0.9f : 0.8f;
            ms.push();
            ms.translate(-cam.x, -cam.y, -cam.z);
            int drawn = 0;
            for (Builder.Task t : list) {
                double dx = t.pos.getX() + 0.5 - cam.x, dy = t.pos.getY() + 0.5 - cam.y, dz = t.pos.getZ() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > 96 * 96) continue;
                int c = Builder.blockColor(t.block);
                float r = Math.min(1f, ((c >> 16) & 255) / 255f * 1.25f + 0.15f);
                float g = Math.min(1f, ((c >> 8) & 255) / 255f * 1.25f + 0.15f);
                float b = Math.min(1f, (c & 255) / 255f * 1.25f + 0.15f);
                WorldRenderer.drawBox(ms, vc,
                        t.pos.getX() + 0.03, t.pos.getY() + 0.03, t.pos.getZ() + 0.03,
                        t.pos.getX() + 0.97, t.pos.getY() + 0.97, t.pos.getZ() + 0.97,
                        r, g, b, alpha);
                if (++drawn > 4000) break;
            }
            ms.pop();
        });

        HudRenderCallback.EVENT.register((g, tick) -> hud(g));
        Blueprints.all();
    }

    private static void panel(DrawContext g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A313A);
        g.fill(x, y, x + w, y + h, 0xE0111418);
        g.fill(x, y, x + w, y + 1, 0xFF4CC9F0);
    }

    private static String k(KeyBinding kb) { return kb.getBoundKeyLocalizedText().getString(); }

    private static void hud(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int cx = g.getScaledWindowWidth() / 2;

        switch (Builder.mode) {
            case PREVIEW -> {
                String t = "PREVIEW   [" + k(CONFIRM) + "] build   [" + k(ROTATE) + "] rotate   [" + k(STOP) + "] cancel";
                int w = tr.getWidth(t) + 20;
                panel(g, cx - w / 2, 10, w, 20);
                g.drawTextWithShadow(tr, t, cx - w / 2 + 10, 17, 0xFFE6EDF3);
            }
            case BUILDING -> {
                int w = 210, h = Builder.status.isEmpty() ? 28 : 40;
                int x = cx - w / 2;
                panel(g, x, 10, w, h);
                String t = "Building  " + Builder.done() + " / " + Builder.total() + "   " + Builder.speed + "/s";
                g.drawTextWithShadow(tr, t, x + 8, 16, 0xFFE6EDF3);
                int bw = w - 16;
                float f = Builder.total() == 0 ? 1f : Builder.done() / (float) Builder.total();
                g.fill(x + 8, 28, x + 8 + bw, 31, 0xFF222A33);
                g.fill(x + 8, 28, x + 8 + (int) (bw * f), 31, 0xFF4CC9F0);
                if (!Builder.status.isEmpty()) g.drawTextWithShadow(tr, Builder.status, x + 8, 36, 0xFFFFB454);
            }
            default -> {
                if (Builder.recentlyFinished()) {
                    String t = "Build complete";
                    int w = tr.getWidth(t) + 20;
                    panel(g, cx - w / 2, 10, w, 20);
                    g.drawTextWithShadow(tr, t, cx - w / 2 + 10, 17, 0xFF7EE787);
                }
            }
        }
    }
}
