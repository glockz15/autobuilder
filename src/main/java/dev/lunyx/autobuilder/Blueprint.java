package dev.lunyx.autobuilder;

import net.minecraft.block.Block;

import java.util.*;

/** An immutable set of blocks, normalised so the min corner sits at 0,0,0. */
public final class Blueprint {
    public record Entry(int x, int y, int z, Block block) {}

    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt(Entry::y).thenComparingInt(Entry::x).thenComparingInt(Entry::z);

    public final String name;
    public final List<Entry> entries;
    public final int sx, sy, sz;

    public Blueprint(String name, Collection<Entry> raw) {
        this.name = name;
        if (raw.isEmpty()) {
            this.entries = List.of();
            this.sx = 1;
            this.sy = 1;
            this.sz = 1;
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Entry e : raw) {
            minX = Math.min(minX, e.x()); maxX = Math.max(maxX, e.x());
            minY = Math.min(minY, e.y()); maxY = Math.max(maxY, e.y());
            minZ = Math.min(minZ, e.z()); maxZ = Math.max(maxZ, e.z());
        }
        List<Entry> list = new ArrayList<>(raw.size());
        for (Entry e : raw) list.add(new Entry(e.x() - minX, e.y() - minY, e.z() - minZ, e.block()));
        list.sort(ORDER);
        this.entries = Collections.unmodifiableList(list);
        this.sx = maxX - minX + 1;
        this.sy = maxY - minY + 1;
        this.sz = maxZ - minZ + 1;
    }

    public int width(int rot) { return (rot & 1) == 0 ? sx : sz; }
    public int depth(int rot) { return (rot & 1) == 0 ? sz : sx; }

    /** Entries rotated clockwise by rot * 90 degrees, sorted bottom-up. */
    public List<Entry> rotated(int rot) {
        rot &= 3;
        if (rot == 0) return entries;
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            int x = e.x(), z = e.z(), nx, nz;
            switch (rot) {
                case 1 -> { nx = sz - 1 - z; nz = x; }
                case 2 -> { nx = sx - 1 - x; nz = sz - 1 - z; }
                default -> { nx = z; nz = sx - 1 - x; }
            }
            out.add(new Entry(nx, e.y(), nz, e.block()));
        }
        out.sort(ORDER);
        return out;
    }
}
