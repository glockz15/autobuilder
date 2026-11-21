package dev.lunyx.autobuilder;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Build registry. Index 0 is always the random build.
 * Drop .json files into config/autobuilder/builds to add your own:
 * {
 *   "name": "My Build",
 *   "fills":  [ { "from": [0,0,0], "to": [4,0,4], "block": "minecraft:stone_bricks" } ],
 *   "blocks": [ { "x": 2, "y": 1, "z": 2, "block": "minecraft:lantern" } ]
 * }
 */
public final class Blueprints {
    private static final List<Blueprint> LIST = new ArrayList<>();
    private static final Random RNG = new Random();

    private Blueprints() {}

    public static List<Blueprint> all() {
        if (LIST.isEmpty()) reload();
        return LIST;
    }

    public static void reload() {
        LIST.clear();
        LIST.add(Generators.randomCottage(RNG));
        LIST.add(Generators.watchtower());
        LIST.add(Generators.pyramid());
        loadFiles();
    }

    public static void reroll() {
        all();
        LIST.set(0, Generators.randomCottage(RNG));
    }

    private static void loadFiles() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("autobuilder").resolve("builds");
        try {
            Files.createDirectories(dir);
            boolean any;
            try (Stream<Path> s = Files.list(dir)) {
                any = s.anyMatch(p -> p.toString().endsWith(".json"));
            }
            if (!any) Files.writeString(dir.resolve("example_pad.json"), SAMPLE);
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".json")).sorted().forEach(Blueprints::loadFile);
            }
        } catch (IOException e) {
            AutoBuilderMod.LOGGER.warn("Could not read builds folder", e);
        }
    }

    private static void loadFile(Path p) {
        try (Reader r = Files.newBufferedReader(p)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString()
                    : p.getFileName().toString().replace(".json", "");
            Canvas c = new Canvas();
            if (o.has("fills")) {
                for (JsonElement el : o.getAsJsonArray("fills")) {
                    JsonObject f = el.getAsJsonObject();
                    Block b = block(f.get("block").getAsString());
                    if (b == null) continue;
                    JsonArray a = f.getAsJsonArray("from"), z = f.getAsJsonArray("to");
                    c.fill(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                            z.get(0).getAsInt(), z.get(1).getAsInt(), z.get(2).getAsInt(), b);
                }
            }
            if (o.has("blocks")) {
                for (JsonElement el : o.getAsJsonArray("blocks")) {
                    JsonObject e = el.getAsJsonObject();
                    Block b = block(e.get("block").getAsString());
                    if (b == null) continue;
                    c.set(e.get("x").getAsInt(), e.get("y").getAsInt(), e.get("z").getAsInt(), b);
                }
            }
            if (c.size() > 0) LIST.add(c.build(name));
        } catch (Exception e) {
            AutoBuilderMod.LOGGER.warn("Skipping bad build file {}: {}", p.getFileName(), e.toString());
        }
    }

    private static Block block(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return null;
        Block b = Registries.BLOCK.getOrEmpty(ident).orElse(null);
        return b == null || b == Blocks.AIR ? null : b;
    }

    private static final String SAMPLE = """
            {
              "name": "Example Pad",
              "fills": [
                { "from": [0, 0, 0], "to": [6, 0, 6], "block": "minecraft:polished_andesite" },
                { "from": [0, 1, 0], "to": [0, 3, 0], "block": "minecraft:stone_bricks" },
                { "from": [6, 1, 0], "to": [6, 3, 0], "block": "minecraft:stone_bricks" },
                { "from": [0, 1, 6], "to": [0, 3, 6], "block": "minecraft:stone_bricks" },
                { "from": [6, 1, 6], "to": [6, 3, 6], "block": "minecraft:stone_bricks" }
              ],
              "blocks": [
                { "x": 3, "y": 1, "z": 3, "block": "minecraft:sea_lantern" }
              ]
            }
            """;
}
