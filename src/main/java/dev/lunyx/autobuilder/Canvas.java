package dev.lunyx.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** Tiny helper for writing blueprints in code. */
public final class Canvas {
    private final Map<Long, Blueprint.Entry> map = new HashMap<>();

    public void set(int x, int y, int z, Block b) {
        map.put(BlockPos.asLong(x, y, z), new Blueprint.Entry(x, y, z, b));
    }

    public void clear(int x, int y, int z) {
        map.remove(BlockPos.asLong(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, b);
    }

    public int size() { return map.size(); }

    public Blueprint build(String name) { return new Blueprint(name, map.values()); }
}
