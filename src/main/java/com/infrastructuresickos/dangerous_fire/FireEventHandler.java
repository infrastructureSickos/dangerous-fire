package com.infrastructuresickos.dangerous_fire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles lava/magma ignition and hydra fire spread.
 * Registered manually on the FORGE bus — do NOT add @Mod.EventBusSubscriber.
 *
 * Natural fire extinguishment is detected via per-level fire tracking:
 *  1. Fire positions are added to a tracked set when fire is placed.
 *  2. Each server level tick, tracked positions are scanned; those no longer
 *     holding fire are checked — if not broken by a player, spread triggers.
 *  3. Player-broken fires are marked in a short-lived exclusion set.
 */
public class FireEventHandler {

    private static final Random RANDOM = new Random();

    // levelKey → set of BlockPos known to contain fire
    private final Map<ResourceKey<Level>, Set<BlockPos>> trackedFires = new HashMap<>();
    // levelKey → set of BlockPos recently broken by a player (cleared each tick after processing)
    private final Map<ResourceKey<Level>, Set<BlockPos>> playerBroken = new HashMap<>();

    private Set<BlockPos> fires(Level level) {
        return trackedFires.computeIfAbsent(level.dimension(), k -> new HashSet<>());
    }

    private Set<BlockPos> playerBreaks(Level level) {
        return playerBroken.computeIfAbsent(level.dimension(), k -> new HashSet<>());
    }

    // -------------------------------------------------------------------------
    // Track when fire is placed in the world
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        if (event.getPlacedBlock().getBlock() instanceof BaseFireBlock) {
            fires(level).add(event.getPos().immutable());
        }

        // Lava/magma ignition on placement
        BlockState placed = event.getPlacedBlock();
        if (placed.is(Blocks.LAVA)) {
            tryIgniteAround(level, event.getPos(), DFConfig.INSTANCE.lavaIgnitionRadius.get());
        } else if (placed.is(Blocks.MAGMA_BLOCK)) {
            tryIgniteAround(level, event.getPos(), DFConfig.INSTANCE.magmaIgnitionRadius.get());
        }
    }

    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;

        BlockState state = level.getBlockState(event.getPos());
        // Track new fire that appeared via neighbor update (e.g. fire spread from existing fire)
        if (state.getBlock() instanceof BaseFireBlock) {
            fires(level).add(event.getPos().immutable());
        }
        // Lava/magma ignition on neighbor update
        if (state.is(Blocks.LAVA)) {
            tryIgniteAround(level, event.getPos(), DFConfig.INSTANCE.lavaIgnitionRadius.get());
        } else if (state.is(Blocks.MAGMA_BLOCK)) {
            tryIgniteAround(level, event.getPos(), DFConfig.INSTANCE.magmaIgnitionRadius.get());
        }
    }

    // -------------------------------------------------------------------------
    // Track player-broken fire to suppress spread
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide()) return;
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof BaseFireBlock)) return;
        // Record that a player broke this fire position
        playerBreaks(level).add(event.getPos().immutable());
    }

    // -------------------------------------------------------------------------
    // Per-tick scan: detect naturally extinguished fires
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof Level level)) return;
        if (level.isClientSide()) return;

        Set<BlockPos> fireSet = fires(level);
        Set<BlockPos> brokenByPlayer = playerBreaks(level);

        List<BlockPos> gone = new ArrayList<>();
        for (BlockPos pos : fireSet) {
            if (!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) {
                gone.add(pos);
            }
        }

        for (BlockPos pos : gone) {
            fireSet.remove(pos);
            if (!brokenByPlayer.remove(pos)) {
                // Not broken by a player — natural extinguishment
                onNaturalFireOut(level, pos);
            }
        }

        // Any remaining player-broken entries that weren't in fireSet — clear them
        brokenByPlayer.clear();
    }

    // -------------------------------------------------------------------------
    // Level unload: release tracking state
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        trackedFires.remove(level.dimension());
        playerBroken.remove(level.dimension());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Scans a cube of radius r around origin. For each flammable block found,
     * attempts to place fire on an adjacent air block above or to the side.
     */
    private void tryIgniteAround(Level level, BlockPos origin, int radius) {
        double chance = DFConfig.INSTANCE.ignitionChance.get();
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (!level.getBlockState(candidate).isFlammable(level, candidate, Direction.UP)) continue;
            if (RANDOM.nextDouble() >= chance) continue;

            // Try to place fire on an air block adjacent to this flammable block
            for (Direction dir : Direction.values()) {
                BlockPos firePos = candidate.relative(dir);
                if (level.getBlockState(firePos).isAir()
                        && BaseFireBlock.canBePlacedAt(level, firePos, dir)) {
                    BlockPos immutable = firePos.immutable();
                    level.setBlockAndUpdate(immutable, BaseFireBlock.getState(level, immutable));
                    fires(level).add(immutable);
                    break;
                }
            }
        }
    }

    /**
     * Hydra spread: naturally-extinguished fire spawns up to 2 new fires nearby.
     */
    private void onNaturalFireOut(Level level, BlockPos pos) {
        if (RANDOM.nextDouble() >= DFConfig.INSTANCE.spreadChance.get()) return;

        int dist = DFConfig.INSTANCE.spreadDistance.get();
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos candidate : BlockPos.betweenClosed(
                pos.offset(-dist, -dist, -dist),
                pos.offset(dist, dist, dist))) {
            if (candidate.equals(pos)) continue;
            if (!level.getBlockState(candidate).isAir()) continue;
            if (!BaseFireBlock.canBePlacedAt(level, candidate, Direction.UP)) continue;
            candidates.add(candidate.immutable());
        }

        if (candidates.isEmpty()) return;

        int spawned = 0;
        while (spawned < 2 && !candidates.isEmpty()) {
            int idx = RANDOM.nextInt(candidates.size());
            BlockPos firePos = candidates.remove(idx);
            level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
            fires(level).add(firePos);
            spawned++;
        }
    }
}
