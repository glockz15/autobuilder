package dev.lunyx.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class BuilderScreen extends Screen {
    private static final int BG = 0xF0111418, EDGE = 0xFF2A313A, ACCENT = 0xFF4CC9F0, TEXT = 0xFFE6EDF3,
            MUTED = 0xFF8B97A3, WARN = 0xFFFFB454, ROW_SEL = 0xFF1B2832, ROW_HOVER = 0xFF161C22;
    private static final int ROW_H = 20;

    private record Btn(int x, int y, int w, int h, String label, boolean enabled, boolean primary, Runnable action) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final Comparator<Blueprint.Entry> ISO =
            Comparator.<Blueprint.Entry>comparingInt(e -> e.x() + e.z()).thenComparingInt(Blueprint.Entry::y);

    private int px, py, pw, ph, listX, listY, listW, listH, rx, rw, prevY, prevH;
    private int selected, scroll;
    private List<Map.Entry<Block, Integer>> mats = List.of();
    private Blueprint isoFor;
    private int isoRot = -1;
    private List<Blueprint.Entry> iso = List.of();

    public BuilderScreen() {
        super(Text.literal("Auto Builder"));
    }

    @Override
    protected void init() {
        List<Blueprint> all = Blueprints.all();
        select(Math.max(0, all.indexOf(Builder.blueprint)));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }

    private void layout() {
        pw = Math.min(width - 24, 540);
        ph = Math.min(height - 24, 292);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listX = px + 12;
        listY = py + 38;
        listW = 148;
        listH = ph - 38 - 40;
        rx = px + 172;
        rw = pw - 172 - 12;
        prevY = py + 38;
        prevH = Math.max(70, ph - 134);
    }

    private void select(int i) {
        List<Blueprint> all = Blueprints.all();
        selected = Math.max(0, Math.min(i, all.size() - 1));
        Blueprint bp = all.get(selected);
        Builder.select(bp);
        Map<Block, Integer> counts = new HashMap<>();
        for (Blueprint.Entry e : bp.entries) counts.merge(e.block(), 1, Integer::sum);
        List<Map.Entry<Block, Integer>> l = new ArrayList<>(counts.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        mats = l;
        isoFor = null;
    }

    // ---------- rendering ----------

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        layout();
        TextRenderer tr = textRenderer;
        g.fill(0, 0, width, height, 0x99000000);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, EDGE);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 2, ACCENT);

        g.drawTextWithShadow(tr, "AUTO BUILDER", px + 12, py + 13, ACCENT);
        String st = switch (Builder.mode) {
            case IDLE -> "Ready";
            case PREVIEW -> "Previewing";
            case BUILDING -> "Building " + Builder.done() + " / " + Builder.total();
        };
        g.drawTextWithShadow(tr, st, px + pw - 12 - tr.getWidth(st), py + 13, MUTED);
        g.fill(px + 12, py + 28, px + pw - 12, py + 29, EDGE);

        drawList(g, mx, my);
        drawPreview(g);
        drawInfo(g);
        for (Btn b : buttons()) drawButton(g, b, mx, my);
    }

    private void drawList(DrawContext g, int mx, int my) {
        TextRenderer tr = textRenderer;
        List<Blueprint> all = Blueprints.all();
        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, EDGE);
        g.fill(listX, listY, listX + listW, listY + listH, 0xFF0C0F13);
        int rows = Math.max(1, listH / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - rows)));
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int r = 0; r < rows && scroll + r < all.size(); r++) {
            int i = scroll + r;
            int ry = listY + r * ROW_H;
            boolean hover = mx >= listX && mx < listX + listW && my >= ry && my < ry + ROW_H;
            if (i == selected) {
                g.fill(listX, ry, listX + listW, ry + ROW_H, ROW_SEL);
                g.fill(listX, ry, listX + 2, ry + ROW_H, ACCENT);
            } else if (hover) {
                g.fill(listX, ry, listX + listW, ry + ROW_H, ROW_HOVER);
            }
            Blueprint bp = all.get(i);
            String cnt = Integer.toString(bp.entries.size());
            int cw = tr.getWidth(cnt);
            g.drawTextWithShadow(tr, tr.trimToWidth(bp.name, listW - cw - 22), listX + 8, ry + 6, i == selected ? TEXT : 0xFFB8C2CC);
            g.drawTextWithShadow(tr, cnt, listX + listW - cw - 6, ry + 6, MUTED);
        }
        g.disableScissor();
    }

    private void drawPreview(DrawContext g) {
        int bx = rx, by = prevY, bw = rw, bh = prevH;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, EDGE);
        g.fill(bx, by, bx + bw, by + bh, 0xFF0C0F13);
        Blueprint bp = Builder.blueprint;
        if (bp == null || bp.entries.isEmpty()) return;
        int rot = Builder.rotation;
        if (isoFor != bp || isoRot != rot) {
            List<Blueprint.Entry> l = new ArrayList<>(bp.rotated(rot));
            l.sort(ISO);
            iso = l;
            isoFor = bp;
            isoRot = rot;
        }
        int w = bp.width(rot), d = bp.depth(rot), h = bp.sy;
        int u = 2;
        for (int k = 12; k >= 2; k -= 2) {
            int W = (w + d) * k;
            int H = (h + 1) * k + (w + d - 2) * k / 2;
            if (W <= bw - 8 && H <= bh - 8) { u = k; break; }
        }
        int W = (w + d) * u;
        int H = (h + 1) * u + (w + d - 2) * u / 2;
        int ox = bx + (bw - W) / 2 + (d - 1) * u;
        int oy = by + (bh - H) / 2 + (h - 1) * u;
        g.enableScissor(bx, by, bx + bw, by + bh);
        for (Blueprint.Entry e : iso) {
            int sx = ox + (e.x() - e.z()) * u;
            int sy = oy + (e.x() + e.z()) * (u / 2) - e.y() * u;
            int c = Builder.blockColor(e.block());
            g.fill(sx, sy, sx + 2 * u, sy + u, shade(c, 1.12f));
            g.fill(sx, sy + u, sx + u, sy + 2 * u, shade(c, 0.78f));
            g.fill(sx + u, sy + u, sx + 2 * u, sy + 2 * u, shade(c, 0.58f));
        }
        g.disableScissor();
    }

    private void drawInfo(DrawContext g) {
        TextRenderer tr = textRenderer;
        Blueprint bp = Builder.blueprint;
        if (bp == null) return;
        int rot = Builder.rotation;
        int iy = prevY + prevH + 6;
        String meta = bp.width(rot) + " x " + bp.sy + " x " + bp.depth(rot) + "  |  " + bp.entries.size() + " blocks  |  " + (rot * 90) + "\u00B0";
        int mw = tr.getWidth(meta);
        g.drawTextWithShadow(tr, tr.trimToWidth(bp.name, rw - mw - 10), rx, iy, TEXT);
        g.drawTextWithShadow(tr, meta, rx + rw - mw, iy, MUTED);

        boolean creative = Builder.isCreative(client);
        int colW = rw / 2;
        for (int i = 0; i < Math.min(6, mats.size()); i++) {
            Map.Entry<Block, Integer> m = mats.get(i);
            int cx = rx + (i % 2) * colW;
            int cy = iy + 14 + (i / 2) * 11;
            String name = m.getKey().getName().getString();
            int need = m.getValue();
            int have = creative || client.player == null ? need : Builder.count(client.player, m.getKey().asItem());
            boolean short_ = have < need;
            String line = need + "x " + name + (short_ ? "  (" + have + ")" : "");
            g.drawTextWithShadow(tr, tr.trimToWidth(line, colW - 6), cx, cy, short_ ? WARN : MUTED);
        }
        if (mats.size() > 6) {
            g.drawTextWithShadow(tr, "+" + (mats.size() - 6) + " more", rx + rw - 40, iy + 14 + 33 - 11, MUTED);
        }
    }

    private void drawButton(DrawContext g, Btn b, int mx, int my) {
        boolean hover = b.enabled() && b.hit(mx, my);
        int fill = !b.enabled() ? 0xFF14181D : b.primary() ? (hover ? 0xFF5FD4F7 : ACCENT) : (hover ? 0xFF26303A : 0xFF1B222A);
        int edge = b.primary() && b.enabled() ? ACCENT : EDGE;
        g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, edge);
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), fill);
        int col = !b.enabled() ? 0xFF4A545E : b.primary() ? 0xFF081016 : TEXT;
        g.drawText(textRenderer, b.label(), b.x() + (b.w() - textRenderer.getWidth(b.label())) / 2, b.y() + (b.h() - 8) / 2, col, false);
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ---------- buttons ----------

    private List<Btn> buttons() {
        List<Btn> l = new ArrayList<>();
        int fy = py + ph - 30;
        l.add(new Btn(px + 12, fy, 70, 20, "Reroll", selected == 0, false, () -> { Blueprints.reroll(); select(0); }));
        l.add(new Btn(px + 90, fy, 70, 20, "Reload", true, false, () -> { Blueprints.reload(); select(selected); }));
        int x = rx;
        l.add(new Btn(x, fy, 66, 20, "Preview", Builder.blueprint != null, false, () -> { Builder.startPreview(); close(); }));
        x += 70;
        l.add(new Btn(x, fy, 66, 20, "Build", Builder.blueprint != null, true, () -> { Builder.buildAtCaptured(client); close(); }));
        x += 70;
        l.add(new Btn(x, fy, 60, 20, "Rotate", true, false, Builder::rotate));
        x += 64;
        l.add(new Btn(x, fy, 50, 20, "Stop", Builder.mode != Builder.Mode.IDLE, false, Builder::stop));
        int sx = px + pw - 12 - 70;
        l.add(new Btn(sx, fy, 18, 20, "-", Builder.speed > 3, false, () -> Builder.speed--));
        l.add(new Btn(sx + 52, fy, 18, 20, "+", Builder.speed < 14, false, () -> Builder.speed++));
        return l;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        if (button == 0) {
            for (Btn b : buttons()) {
                if (b.enabled() && b.hit(mx, my)) { b.action().run(); return true; }
            }
            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                int i = scroll + (int) ((my - listY) / ROW_H);
                if (i < Blueprints.all().size()) select(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (AutoBuilderMod.OPEN.matchesKey(keyCode, scanCode)) { close(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN) { select(selected + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_UP) { select(selected - 1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
