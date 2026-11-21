package dev.lunyx.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Build state + tick logic. Places through the normal interaction path, one block at a time. */
public final class Builder {
    public enum Mode { IDLE, PREVIEW, BUILDING }

    public static final class Task {
        public final BlockPos pos;
        public final Block block;
        int attempts;

        Task(BlockPos pos, Block block) {
            this.pos = pos;
            this.block = block;
        }
    }

    public static Mode mode = Mode.IDLE;
    public static Blueprint blueprint;
    public static int rotation = 0;
    /** Blocks per second. A fast human right-clicking tops out around 5. */
    public static int speed = 7;
    public static String status = "";
    public static BlockPos captured;

    private static BlockPos anchor;
    private static List<Task> ghost = List.of();
    private static String ghostKey = "";
    private static final List<Task> pending = new ArrayList<>();
    private static int total, skipped;
    private static double budget;
    private static long finishedAt = -1;

    private static final Direction[] ORDER = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
    };

    private Builder() {}

    // ---------- state API ----------

    public static void select(Blueprint bp) { blueprint = bp; ghostKey = ""; }

    public static void rotate() { rotation = (rotation + 1) & 3; ghostKey = ""; }

    public static void capture(MinecraftClient mc) { captured = target(mc); }

    public static void startPreview() {
        if (blueprint == null) return;
        mode = Mode.PREVIEW;
        status = "";
        ghostKey = "";
    }

    public static void confirmPreview(MinecraftClient mc) {
        if (mode == Mode.PREVIEW && anchor != null) begin(anchor);
    }

    public static void buildAtCaptured(MinecraftClient mc) {
        BlockPos a = captured != null ? captured : target(mc);
        if (a != null) begin(a);
    }

    public static void stop() {
        mode = Mode.IDLE;
        pending.clear();
        status = "";
    }

    public static int done() { return total - pending.size() - skipped; }
    public static int total() { return total; }
    public static boolean recentlyFinished() { return finishedAt > 0 && System.currentTimeMillis() - finishedAt < 4000; }

    public static List<Task> ghostTasks() {
        return switch (mode) {
            case BUILDING -> pending;
            case PREVIEW -> ghost;
            default -> List.of();
        };
    }

    public static int blockColor(Block b) {
        int c = b.getDefaultMapColor().color;
        return c == 0 ? 0xB4DCE6 : c;
    }

    public static boolean isCreative(MinecraftClient mc) {
        return mc.interactionManager != null && mc.interactionManager.getCurrentGameMode().isCreative();
    }

    public static int count(ClientPlayerEntity p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().main) if (s.isOf(item)) n += s.getCount();
        return n;
    }

    // ---------- internals ----------

    private static void begin(BlockPos a) {
        List<Task> t = tasks(blueprint, rotation, a);
        pending.clear();
        pending.addAll(t);
        total = t.size();
        skipped = 0;
        budget = 0;
        finishedAt = -1;
        status = "";
        mode = Mode.BUILDING;
    }

    private static List<Task> tasks(Blueprint bp, int rot, BlockPos a) {
        int w = bp.width(rot), d = bp.depth(rot);
        int ox = a.getX() - w / 2, oz = a.getZ() - d / 2, oy = a.getY();
        List<Task> out = new ArrayList<>();
        for (Blueprint.Entry e : bp.rotated(rot)) {
            if (e.block().asItem() == Items.AIR) continue;
            out.add(new Task(new BlockPos(ox + e.x(), oy + e.y(), oz + e.z()), e.block()));
        }
        return out; // already bottom-up
    }

    public static BlockPos target(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return null;
        HitResult hr = p.raycast(32.0, 1.0f, false);
        if (hr instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            return b.getBlockPos().offset(b.getSide());
        }
        return p.getBlockPos().offset(p.getHorizontalFacing(), 5);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            if (mode != Mode.IDLE) stop();
            return;
        }
        switch (mode) {
            case PREVIEW -> {
                BlockPos t = target(mc);
                if (t != null) anchor = t;
                refreshGhost();
            }
            case BUILDING -> {
                if (mc.currentScreen == null) run(mc);
            }
            default -> {}
        }
    }

    private static void refreshGhost() {
        if (blueprint == null || anchor == null) { ghost = List.of(); return; }
        String key = System.identityHashCode(blueprint) + ":" + rotation + ":" + anchor.asLong();
        if (!key.equals(ghostKey)) {
            ghostKey = key;
            ghost = tasks(blueprint, rotation, anchor);
        }
    }

    private static void run(MinecraftClient mc) {
        prune(mc.world);
        if (pending.isEmpty()) {
            mode = Mode.IDLE;
            finishedAt = System.currentTimeMillis();
            status = "";
            mc.player.sendMessage(Text.literal("[Auto Builder] Done - " + (total - skipped) + " blocks placed"
                    + (skipped > 0 ? ", " + skipped + " skipped (spot occupied)" : "")), false);
            return;
        }
        budget = Math.min(budget + speed / 20.0, 1.5);
        while (budget >= 1.0) {
            budget -= 1.0;
            if (!placeOne(mc)) { budget = 0; break; }
        }
    }

    private static void prune(ClientWorld w) {
        for (Iterator<Task> it = pending.iterator(); it.hasNext(); ) {
            Task t = it.next();
            BlockState s = w.getBlockState(t.pos);
            if (s.isOf(t.block)) {
                it.remove();
            } else if (!s.isReplaceable() || t.attempts >= 4) {
                it.remove();
                skipped++;
            }
        }
    }

    private static boolean placeOne(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        boolean creative = isCreative(mc);
        Vec3d eye = p.getEyePos();
        double reach = Math.min(p.getBlockInteractionRange(), 5.0) - 0.3;

        Task best = null;
        Direction bestSide = null;
        BlockPos bestSup = null;
        Vec3d bestHit = null;
        double bestScore = Double.MAX_VALUE;
        LinkedHashSet<Item> missing = new LinkedHashSet<>();
        boolean far = false, unsupported = false, blocked = false;

        for (Task t : pending) {
            if (best != null && t.pos.getY() > best.pos.getY()) break; // pending is sorted bottom-up

            Direction side = null;
            BlockPos sup = null;
            for (Direction d : ORDER) {
                BlockPos n = t.pos.offset(d);
                BlockState ns = w.getBlockState(n);
                if (!ns.isAir() && !ns.isReplaceable() && ns.getFluidState().isEmpty() && !ns.hasBlockEntity()) {
                    side = d.getOpposite();
                    sup = n;
                    break;
                }
            }
            if (side == null) { unsupported = true; continue; }

            Vec3d hit = Vec3d.ofCenter(sup).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hit);
            if (dist > reach) { far = true; continue; }

            Item item = t.block.asItem();
            if (!creative && count(p, item) == 0) { missing.add(item); continue; }
            if (!w.canPlace(t.block.getDefaultState(), t.pos, ShapeContext.of(p))) { blocked = true; continue; }

            double score = t.pos.getY() * 1000.0 + dist;
            if (score < bestScore) {
                bestScore = score;
                best = t;
                bestSide = side;
                bestSup = sup;
                bestHit = hit;
            }
        }

        if (best == null) {
            if (!missing.isEmpty()) {
                StringJoiner j = new StringJoiner(", ");
                int n = 0;
                for (Item i : missing) { if (n++ == 3) { j.add("..."); break; } j.add(i.getName().getString()); }
                status = "Out of: " + j;
            } else if (far) status = "Move closer to the build";
            else if (blocked) status = "Step out of the way";
            else if (unsupported) status = "No solid surface to build on";
            return false;
        }

        if (!equip(mc, p, best.block.asItem(), creative)) { status = "Could not get " + best.block.getName().getString(); return false; }

        ActionResult res = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(bestHit, bestSide, bestSup, false));
        if (res.isAccepted()) p.swingHand(Hand.MAIN_HAND);
        best.attempts++;
        status = "";
        return true;
    }

    /** Gets the item into the selected hotbar slot: conjured in creative, pulled from the inventory in survival. */
    private static boolean equip(MinecraftClient mc, ClientPlayerEntity p, Item item, boolean creative) {
        PlayerInventory inv = p.getInventory();
        if (inv.getMainHandStack().isOf(item)) return true;
        if (creative) {
            ItemStack s = new ItemStack(item, item.getMaxCount());
            inv.setStack(inv.selectedSlot, s);
            mc.interactionManager.clickCreativeStack(s, 36 + inv.selectedSlot);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) { inv.selectedSlot = i; return true; }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(item)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, inv.selectedSlot, SlotActionType.SWAP, p);
                return true;
            }
        }
        return false;
    }
}
