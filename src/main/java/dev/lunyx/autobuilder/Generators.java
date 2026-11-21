package dev.lunyx.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.Random;

/** Built-in builds. Everything uses full blocks so placement is orientation-proof. */
final class Generators {
    private Generators() {}

    private static Block pick(Random r, Block... options) {
        return options[r.nextInt(options.length)];
    }

    static Blueprint randomCottage(Random r) {
        Block wall = pick(r, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BRICKS,
                Blocks.WHITE_TERRACOTTA, Blocks.DEEPSLATE_BRICKS, Blocks.BIRCH_PLANKS);
        Block roof;
        do {
            roof = pick(r, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BRICKS, Blocks.STONE_BRICKS,
                    Blocks.DEEPSLATE_BRICKS, Blocks.OAK_PLANKS);
        } while (roof == wall);
        Block frame = pick(r, Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.DARK_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG);
        Block floor = pick(r, Blocks.COBBLESTONE, Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS);

        int w = 7 + r.nextInt(5);
        int d = 7 + r.nextInt(5);
        int h = 4 + r.nextInt(2);

        Canvas c = new Canvas();
        c.fill(0, 0, 0, w - 1, 0, d - 1, floor);
        for (int y = 1; y <= h; y++) {
            c.fill(0, y, 0, w - 1, y, 0, wall);
            c.fill(0, y, d - 1, w - 1, y, d - 1, wall);
            c.fill(0, y, 0, 0, y, d - 1, wall);
            c.fill(w - 1, y, 0, w - 1, y, d - 1, wall);
            c.set(0, y, 0, frame);
            c.set(w - 1, y, 0, frame);
            c.set(0, y, d - 1, frame);
            c.set(w - 1, y, d - 1, frame);
        }
        for (int x = 2; x <= w - 3; x += 3) {
            c.set(x, 2, 0, Blocks.GLASS);
            c.set(x, 2, d - 1, Blocks.GLASS);
        }
        for (int z = 2; z <= d - 3; z += 3) {
            c.set(0, 2, z, Blocks.GLASS);
            c.set(w - 1, 2, z, Blocks.GLASS);
        }
        int cx = w / 2;
        c.clear(cx, 1, 0);
        c.clear(cx, 2, 0);
        for (int y = 1; y <= 2; y++) {
            c.set(cx - 1, y, 0, frame);
            c.set(cx + 1, y, 0, frame);
        }
        c.fill(cx - 1, 0, -1, cx + 1, 0, -1, floor);

        for (int i = 0; i - 1 <= w - i && i - 1 <= d - i; i++) {
            c.fill(i - 1, h + 1 + i, i - 1, w - i, h + 1 + i, d - i, roof);
        }
        return c.build("Random Cottage " + w + "x" + d);
    }

    static Blueprint watchtower() {
        int s = 5;
        Canvas c = new Canvas();
        c.fill(0, 0, 0, s - 1, 0, s - 1, Blocks.COBBLESTONE);
        for (int y = 1; y <= 9; y++) {
            Block b = y % 4 == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS;
            c.fill(0, y, 0, s - 1, y, 0, b);
            c.fill(0, y, s - 1, s - 1, y, s - 1, b);
            c.fill(0, y, 0, 0, y, s - 1, b);
            c.fill(s - 1, y, 0, s - 1, y, s - 1, b);
        }
        c.clear(2, 1, 0);
        c.clear(2, 2, 0);
        c.clear(2, 6, s - 1);
        c.clear(0, 6, 2);
        c.clear(s - 1, 6, 2);
        c.fill(-1, 10, -1, s, 10, s, Blocks.DARK_OAK_PLANKS);
        for (int x = -1; x <= s; x++) {
            for (int z = -1; z <= s; z++) {
                boolean edge = x == -1 || x == s || z == -1 || z == s;
                if (edge && ((x + z) & 1) == 0) c.set(x, 11, z, Blocks.STONE_BRICKS);
            }
        }
        return c.build("Stone Watchtower");
    }

    static Blueprint pyramid() {
        int base = 15;
        int layers = (base + 1) / 2;
        Canvas c = new Canvas();
        for (int i = 0; i < layers; i++) {
            int a = i, b = base - 1 - i;
            Block bl = i == layers - 1 ? Blocks.GOLD_BLOCK : (i % 2 == 0 ? Blocks.SANDSTONE : Blocks.CUT_SANDSTONE);
            c.fill(a, i, a, b, i, b, bl);
        }
        return c.build("Sandstone Pyramid");
    }
}
