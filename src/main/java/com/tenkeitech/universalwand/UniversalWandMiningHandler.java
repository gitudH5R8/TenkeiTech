/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayDeque;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.item.VanillaToolBackend;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.common.blocks.GTBlockOre;

/** Server-side bounded chain mining using the vanilla player harvest lifecycle. */
public final class UniversalWandMiningHandler {

    public static final int PREVIEW_MAX_BLOCKS = 65536;
    private static final int QUEUED_POSITIONS_LIMIT = 4096;
    private static int nextPlayer;

    public static UniversalWandSearch createPreviewSearch(World world, net.minecraft.util.MovingObjectPosition hit,
        ItemStack stack) {
        if (world == null || hit == null
            || stack == null
            || ItemUniversalWand.isProtectionMode(stack)
            || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            || !world.blockExists(hit.blockX, hit.blockY, hit.blockZ)) return null;
        int x = hit.blockX, y = hit.blockY, z = hit.blockZ;
        Block target = world.getBlock(x, y, z);
        if (world.isAirBlock(x, y, z) || target.getBlockHardness(world, x, y, z) < 0) return null;
        return createSearch(
            world,
            x,
            y,
            z,
            stack,
            target,
            world.getBlockMetadata(x, y, z),
            isGtOre(world, x, y, z),
            isGtSmallOre(world, x, y, z),
            Math.min(PREVIEW_MAX_BLOCKS, ItemUniversalWand.getMaxMiningCount(stack)));
    }

    private static UniversalWandSearch createSearch(World world, int x, int y, int z, ItemStack stack, Block target,
        int metadata, boolean gtOreTarget, boolean targetSmall, int maxBlocks) {
        boolean includeSmall = ItemUniversalWand.isGtSmallChainEnabled(stack);
        return new UniversalWandSearch(
            x,
            y,
            z,
            Math.max(0, maxBlocks - 1),
            ItemUniversalWand.isEnhancedChain(stack),
            ItemUniversalWand.getEnhancedChainRadius(stack),
            (px, py, pz) -> world.blockExists(px, py, pz)
                && matchesTarget(world, px, py, pz, target, metadata, gtOreTarget, targetSmall, includeSmall));
    }

    private static final java.util.Map<java.util.UUID, Long> KEY_LAST_HELD = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<java.util.UUID> KEY_HELD = java.util.Collections
        .newSetFromMap(new java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>());
    private static final long KEY_GRACE_NANOS = 750_000_000L;
    private static final java.util.Map<java.util.UUID, MiningTask> TASKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, ChainRequest> START_REQUESTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, BreakProfile> LAST_BREAKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String PENDING_DROPS = "tenkeitechUniversalWandPendingDrops";
    private static final ThreadLocal<HarvestScope> HARVEST = new ThreadLocal<>();
    private static final ThreadLocal<LivingDropScope> LIVING_DROPS = new ThreadLocal<>();

    public static LivingDropScope beginLivingDrops(EntityLivingBase victim, DamageSource source) {
        EntityPlayerMP player = dropOwner(victim, source);
        LivingDropScope scope = new LivingDropScope(victim, player);
        LIVING_DROPS.set(scope);
        return scope;
    }

    private static EntityPlayerMP dropOwner(EntityLivingBase victim, DamageSource source) {
        if (victim instanceof EntityPlayer || victim.worldObj.isRemote) return null;
        EntityPlayerMP player = null;
        if (source != null && source.getEntity() instanceof EntityPlayerMP sourcePlayer) player = sourcePlayer;
        if (player == null && victim.func_94060_bK() instanceof EntityPlayerMP creditedPlayer) player = creditedPlayer;
        return player != null && !player.isDead
            && player.worldObj == victim.worldObj
            && ItemUniversalWand.isHeldBy(player) ? player : null;
    }

    /** Direct item spawns belong only to the currently executing death-drop callback. */
    public static final class LivingDropScope implements AutoCloseable {

        private final LivingDropScope previous = LIVING_DROPS.get();
        private final EntityLivingBase victim;
        private final EntityPlayerMP player;
        private final ItemStack wand;
        private final UniversalWandStorage.Destination destination;
        private final UniversalWandDropBuffer drops = new UniversalWandDropBuffer();

        private LivingDropScope(EntityLivingBase victim, EntityPlayerMP player) {
            this.victim = victim;
            this.player = player;
            wand = player == null ? null : player.getHeldItem();
            destination = player == null ? null : new UniversalWandStorage.Destination(wand);
        }

        private boolean owns(World world) {
            return player != null && destination.collects()
                && !player.isDead
                && player.worldObj == world
                && victim.worldObj == world
                && player.getHeldItem() == wand
                && ItemUniversalWand.isHeldBy(player);
        }

        @Override
        public void close() {
            if (LIVING_DROPS.get() != this) return;
            if (previous == null) LIVING_DROPS.remove();
            else LIVING_DROPS.set(previous);
            if (!drops.isEmpty()) releaseSoloDrops(
                drops,
                destination,
                player,
                victim.worldObj,
                (int) Math.floor(victim.posX),
                (int) Math.floor(victim.posY),
                (int) Math.floor(victim.posZ));
        }
    }

    /** Attribution is limited to synchronous callbacks of this exact player's harvest. */
    public static final class HarvestScope implements AutoCloseable {

        private final HarvestScope previous;
        private final EntityPlayerMP player;
        private final World world;
        private final int x;
        private final int y;
        private final int z;
        private final ItemStack wand;
        private final boolean blockHarvest;
        private final boolean dropToPlayer;
        private VanillaToolBackend.HeldTool shears;
        private boolean harvested;
        private final UniversalWandStorage.Destination destination;
        private final UniversalWandDropBuffer soloDrops;

        private HarvestScope(EntityPlayerMP player, int x, int y, int z, boolean blockHarvest, boolean autoHarvest) {
            this.previous = HARVEST.get();
            this.player = player;
            this.world = player.worldObj;
            this.x = x;
            this.y = y;
            this.z = z;
            this.wand = player.getHeldItem();
            this.blockHarvest = blockHarvest;
            this.dropToPlayer = autoHarvest && Config.universalWandDropToPlayer;
            this.destination = new UniversalWandStorage.Destination(wand);
            this.soloDrops = autoHarvest || destination.collects() ? new UniversalWandDropBuffer() : null;
            HARVEST.set(this);
        }

        public void complete(boolean harvested) {
            this.harvested = harvested;
        }

        private boolean ownsHeldTool() {
            return player.getHeldItem() == wand || shears != null && shears.isHeld();
        }

        private boolean isOrigin(MiningTask task) {
            return x == task.originX && y == task.originY && z == task.originZ;
        }

        @Override
        public void close() {
            if (shears != null) shears.close();
            if (previous == null) HARVEST.remove();
            else HARVEST.set(previous);
            if (soloDrops != null && !soloDrops.isEmpty()) releaseSoloDrops(
                soloDrops,
                destination,
                player,
                dropToPlayer ? player.worldObj : world,
                dropToPlayer ? (int) Math.floor(player.posX) : x,
                dropToPlayer ? (int) Math.floor(player.posY) : y,
                dropToPlayer ? (int) Math.floor(player.posZ) : z);
            if (blockHarvest && !harvested && !ACTIVE.get()) {
                BreakProfile profile = LAST_BREAKS.get(player.getUniqueID());
                if (profile != null && profile.world == world && profile.x == x && profile.y == y && profile.z == z)
                    LAST_BREAKS.remove(player.getUniqueID(), profile);
                MiningTask task = TASKS.get(player.getUniqueID());
                if (task != null && task.player == player && task.world == world && isOrigin(task)) {
                    if (task.originCounted) task.brokenCount--;
                    finishTask(task, true, true);
                }
            }
        }
    }

    public static HarvestScope beginHarvest(EntityPlayerMP player, int x, int y, int z) {
        return new HarvestScope(player, x, y, z, true, false);
    }

    public static HarvestScope beginInteraction(EntityPlayerMP player, int x, int y, int z) {
        return new HarvestScope(player, x, y, z, false, false);
    }

    static HarvestScope beginAutoHarvest(EntityPlayerMP player, int x, int y, int z) {
        return new HarvestScope(player, x, y, z, false, true);
    }

    public static void beginBlockShearing(EntityPlayerMP player, int x, int y, int z) {
        HarvestScope scope = HARVEST.get();
        if (scope != null && scope.blockHarvest
            && scope.player == player
            && scope.shears == null
            && scope.x == x
            && scope.y == y
            && scope.z == z
            && scope.ownsHeldTool()) scope.shears = VanillaToolBackend.beginBlockShearing(player, x, y, z);
    }

    public static boolean hasShearedBlock(EntityPlayer player, int x, int y, int z) {
        HarvestScope scope = HARVEST.get();
        return scope != null && scope.player == player
            && scope.x == x
            && scope.y == y
            && scope.z == z
            && scope.shears != null
            && scope.shears.shearedBlock();
    }

    private static final class MiningTask {

        final EntityPlayerMP player;
        ItemStack stack;
        final ArrayDeque<int[]> positions = new ArrayDeque<>();
        final UniversalWandSearch search;
        final World world;
        final boolean dropToPlayer;
        final UniversalWandStorage.Destination destination;
        final UniversalWandDropBuffer drops = new UniversalWandDropBuffer();
        final int originX;
        final int originY;
        final int originZ;
        boolean originCounted;
        int brokenCount;

        MiningTask(EntityPlayerMP player, ItemStack stack, UniversalWandSearch search, int initialBrokenCount,
            int originX, int originY, int originZ) {
            this.player = player;
            this.stack = stack;
            this.search = search;
            this.world = player.worldObj;
            this.dropToPlayer = Config.universalWandDropToPlayer;
            this.destination = new UniversalWandStorage.Destination(stack);
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.originCounted = initialBrokenCount > 0;
            this.brokenCount = initialBrokenCount;
        }
    }

    private static final class ChainRequest {

        final EntityPlayerMP player;
        final World world;
        final int x;
        final int y;
        final int z;

        ChainRequest(EntityPlayerMP player, int x, int y, int z) {
            this.player = player;
            this.world = player.worldObj;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class BreakProfile {

        final int x;
        final int y;
        final int z;
        final Block target;
        final World world;
        final int metadata;
        final boolean gtOreTarget;
        final boolean targetSmall;
        final ItemStack stack;
        final long createdAt;

        BreakProfile(int x, int y, int z, Block target, World world, int metadata, boolean gtOreTarget,
            boolean targetSmall, ItemStack stack) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = target;
            this.world = world;
            this.metadata = metadata;
            this.gtOreTarget = gtOreTarget;
            this.targetSmall = targetSmall;
            this.stack = stack;
            this.createdAt = System.nanoTime();
        }
    }

    public static void setKeyHeld(EntityPlayerMP player, boolean held) {
        if (player == null) return;
        java.util.UUID id = player.getUniqueID();
        if (held && UniversalWandFeature.CHAIN.allowed()
            && player.getHeldItem() != null
            && player.getHeldItem()
                .getItem() instanceof ItemUniversalWand) {
            KEY_HELD.add(id);
            KEY_LAST_HELD.put(id, System.nanoTime());
        } else {
            KEY_HELD.remove(id);
            KEY_LAST_HELD.remove(id);
            START_REQUESTS.remove(id);
        }
    }

    public static void requestChainStart(EntityPlayerMP player, int x, int y, int z) {
        if (!UniversalWandFeature.CHAIN.allowed()) return;
        if (player != null) START_REQUESTS.put(player.getUniqueID(), new ChainRequest(player, x, y, z));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        UniversalWandActions.tick();
        if (!UniversalWandFeature.CHAIN.allowed()) {
            KEY_HELD.clear();
            KEY_LAST_HELD.clear();
            START_REQUESTS.clear();
            LAST_BREAKS.clear();
        }
        for (java.util.Map.Entry<java.util.UUID, ChainRequest> entry : START_REQUESTS.entrySet()) {
            ChainRequest request = entry.getValue();
            EntityPlayerMP player = request.player;
            if (KEY_HELD.contains(entry.getKey()) && !TASKS.containsKey(entry.getKey())
                && !player.isDead
                && player.worldObj == request.world
                && request.world.playerEntities.contains(player)
                && request.world.blockExists(request.x, request.y, request.z)) {
                ItemStack held = player.getHeldItem();
                if (held != null && held.getItem() instanceof ItemUniversalWand
                    && !ItemUniversalWand.isProtectionMode(held)
                    && player.canPlayerEdit(request.x, request.y, request.z, 1, held)
                    && isReachable(player, request.x, request.y, request.z)) {
                    BreakProfile profile = LAST_BREAKS.get(entry.getKey());
                    boolean profileMatches = profile != null && profile.world == player.worldObj
                        && profile.stack == held
                        && profile.x == request.x
                        && profile.y == request.y
                        && profile.z == request.z
                        && System.nanoTime() - profile.createdAt < KEY_GRACE_NANOS * 4L;
                    if (profileMatches && player.worldObj.isAirBlock(request.x, request.y, request.z)) {
                        enqueueChain(
                            player,
                            request.x,
                            request.y,
                            request.z,
                            held,
                            profile.target,
                            profile.metadata,
                            profile.gtOreTarget,
                            profile.targetSmall,
                            1);
                    } else if (player.worldObj.blockExists(request.x, request.y, request.z)
                        && !player.worldObj.isAirBlock(request.x, request.y, request.z)
                        && UniversalWandActions.isTarget(player, request.x, request.y, request.z)) {
                            enqueueChain(player, request.x, request.y, request.z, held, 0);
                        }
                }
            }
            START_REQUESTS.remove(entry.getKey(), request);
        }
        java.util.List<MiningTask> tasks = new java.util.ArrayList<>(TASKS.values());
        if (!tasks.isEmpty()) java.util.Collections.rotate(tasks, -(Math.floorMod(nextPlayer++, tasks.size())));
        int searchRemaining = Config.universalWandGlobalSearchPositionsPerTick;
        int harvestRemaining = Config.universalWandGlobalBlocksPerTick;
        java.util.Map<java.util.UUID, Integer> searched = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Integer> attempted = new java.util.HashMap<>();
        for (MiningTask task : tasks) {
            EntityPlayerMP player = task.player;
            ItemStack heldStack = player.getHeldItem();
            if (!KEY_HELD.contains(player.getUniqueID())) {
                finishTask(task, true, true);
                continue;
            }
            if (player.isDead || player.worldObj == null
                || heldStack == null
                || heldStack != task.stack
                || ItemUniversalWand.isProtectionMode(heldStack)
                || player.worldObj != task.world) {
                finishTask(task, !player.isDead, true);
                continue;
            }
            ACTIVE.set(true);
            try {
                int blocksPerTick = Math.min(Config.universalWandBlocksPerTick, harvestRemaining);
                int processed = 0;
                if (!task.originCounted) {
                    if (blocksPerTick == 0) continue;
                    processed++;
                    harvestRemaining--;
                    attempted.merge(player.getUniqueID(), 1, Integer::sum);
                    if (!harvestPosition(task, task.originX, task.originY, task.originZ)) {
                        finishTask(task, true, true);
                        continue;
                    }
                    task.originCounted = true;
                    task.brokenCount++;
                }
                int budget = Math.min(
                    searchRemaining,
                    Math.min(
                        Config.universalWandSearchPositionsPerTick,
                        QUEUED_POSITIONS_LIMIT - task.positions.size()));
                int scanned = task.search.advance(budget, (x, y, z) -> task.positions.addLast(new int[] { x, y, z }));
                searchRemaining -= scanned;
                searched.put(player.getUniqueID(), scanned);
                while (processed < blocksPerTick && !task.positions.isEmpty()) {
                    if (player.isDead || player.worldObj != task.world
                        || player.getHeldItem() != task.stack
                        || ItemUniversalWand.isProtectionMode(task.stack)
                        || !KEY_HELD.contains(player.getUniqueID())) {
                        finishTask(task, !player.isDead, true);
                        break;
                    }
                    processed++;
                    harvestRemaining--;
                    attempted.merge(player.getUniqueID(), 1, Integer::sum);
                    int[] pos = task.positions.removeFirst();
                    if (harvestPosition(task, pos[0], pos[1], pos[2])) {
                        task.brokenCount++;
                    }
                }
            } catch (RuntimeException failure) {
                finishTask(task, true, true);
                TenkeiTech.LOG.error("Universal Wand chain stopped after a harvest failure", failure);
            } finally {
                ACTIVE.set(false);
            }
            if (task.positions.isEmpty() && task.search.isFinished()) {
                finishTask(task, true, true);
            }
        }
        UniversalWandAutoHarvestHandler.tick(searchRemaining, harvestRemaining, searched, attempted);
    }

    private static boolean harvestPosition(MiningTask task, int x, int y, int z) {
        EntityPlayerMP player = task.player;
        if (!task.world.blockExists(x, y, z) || !task.search.matches(x, y, z)
            || task.world.getBlock(x, y, z)
                .getBlockHardness(task.world, x, y, z) < 0
            || !player.canPlayerEdit(x, y, z, 1, task.stack)
            || net.minecraft.server.MinecraftServer.getServer()
                .isBlockProtected((net.minecraft.world.WorldServer) task.world, x, y, z, player))
            return false;
        return player.theItemInWorldManager.tryHarvestBlock(x, y, z);
    }

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>() {

        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    private static final ThreadLocal<Boolean> FORCED_BREAK = new ThreadLocal<>();

    public static final class ForcedBreakScope implements AutoCloseable {

        private final Boolean previous = FORCED_BREAK.get();

        private ForcedBreakScope() {
            FORCED_BREAK.set(true);
        }

        @Override
        public void close() {
            if (previous == null) FORCED_BREAK.remove();
            else FORCED_BREAK.set(previous);
        }
    }

    public static ForcedBreakScope beginForcedBreak() {
        return new ForcedBreakScope();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (ACTIVE.get() || Boolean.TRUE.equals(FORCED_BREAK.get())
            || event.isCanceled()
            || !(event.getPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        UniversalWandActions.applyKeyRequest(player);
        World world = player.worldObj;
        Block target = world.getBlock(event.x, event.y, event.z);
        int metadata = world.getBlockMetadata(event.x, event.y, event.z);
        boolean gtOreTarget = isGtOre(world, event.x, event.y, event.z);
        boolean targetSmall = isGtSmallOre(world, event.x, event.y, event.z);
        MiningTask task = TASKS.get(player.getUniqueID());
        if (task != null && !task.originCounted
            && task.originX == event.x
            && task.originY == event.y
            && task.originZ == event.z) {
            task.originCounted = true;
            task.brokenCount++;
        }
        ItemStack stack = player.getHeldItem();
        Long lastHeld = KEY_LAST_HELD.get(player.getUniqueID());
        boolean chainEligible = stack != null && stack.getItem() instanceof ItemUniversalWand
            && UniversalWandFeature.CHAIN.allowed()
            && KEY_HELD.contains(player.getUniqueID())
            && lastHeld != null
            && System.nanoTime() - lastHeld <= KEY_GRACE_NANOS;
        if (chainEligible) {
            LAST_BREAKS.put(
                player.getUniqueID(),
                new BreakProfile(event.x, event.y, event.z, target, world, metadata, gtOreTarget, targetSmall, stack));
        } else {
            LAST_BREAKS.remove(player.getUniqueID());
        }
        if (!chainEligible || ItemUniversalWand.isProtectionMode(stack)
            || target.getBlockHardness(world, event.x, event.y, event.z) < 0) return;
        enqueueChain(player, event.x, event.y, event.z, stack, target, metadata, gtOreTarget, targetSmall, 1);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) clearPlayer(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP player) releasePendingDrops(player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP player
            && player.ticksExisted % 20 == 0) releasePendingDrops(player);
    }

    private static MiningTask currentDropTask(World world) {
        HarvestScope scope = HARVEST.get();
        if (scope == null || scope.world != world || world.isRemote) return null;
        MiningTask task = TASKS.get(scope.player.getUniqueID());
        return task != null && task.world == world
            && scope.blockHarvest
            && task.player == scope.player
            && task.stack == scope.wand
            && scope.ownsHeldTool()
            && (ACTIVE.get() || scope.isOrigin(task)) ? task : null;
    }

    private static final ThreadLocal<Boolean> RELEASING = new ThreadLocal<>();

    private static UniversalWandDropBuffer currentDropBuffer(World world) {
        if (Boolean.TRUE.equals(RELEASING.get())) return null;
        HarvestScope scope = HARVEST.get();
        if (scope == null || scope.world != world || world.isRemote) return null;
        MiningTask task = currentDropTask(world);
        return task == null ? scope.soloDrops : task.drops;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        UniversalWandDropBuffer drops = currentDropBuffer(event.world);
        if (drops == null || event.harvester != HARVEST.get().player) return;
        for (ItemStack stack : event.drops) {
            if (event.isSilkTouching || event.world.rand.nextFloat() <= event.dropChance) drops.collect(stack);
        }
        event.drops.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (Boolean.TRUE.equals(RELEASING.get())) return;
        UniversalWandDropBuffer drops = currentDropBuffer(event.world);
        LivingDropScope living = LIVING_DROPS.get();
        if (living != null) drops = living.owns(event.world) ? living.drops : null;
        if (drops == null || !(event.entity instanceof EntityItem item)) return;
        ItemStack stack = item.getEntityItem();
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return;
        drops.collect(stack);
        // World.spawnEntityInWorld otherwise ignores event cancellation for forced entities.
        item.forceSpawn = false;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.entityLiving instanceof EntityPlayer) return;
        EntityPlayerMP player = dropOwner(event.entityLiving, event.source);
        if (player == null) return;
        UniversalWandCombatHandler.addConfiguredDrops(event, player);
        if (event.drops.isEmpty()) return;
        UniversalWandStorage.Destination destination = new UniversalWandStorage.Destination(player.getHeldItem());
        if (!destination.collects()) return;
        UniversalWandDropBuffer drops = new UniversalWandDropBuffer();
        for (EntityItem item : event.drops) drops.collect(item.getEntityItem());
        if (drops.isEmpty()) return;
        event.drops.clear();
        event.setCanceled(true);
        releaseSoloDrops(
            drops,
            destination,
            player,
            event.entityLiving.worldObj,
            (int) Math.floor(event.entityLiving.posX),
            (int) Math.floor(event.entityLiving.posY),
            (int) Math.floor(event.entityLiving.posZ));
    }

    private static void finishTask(MiningTask task, boolean notify, boolean allowTaskWorld) {
        if (!TASKS.remove(task.player.getUniqueID(), task)) return;
        EntityPlayerMP player = task.player;
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        task.drops.readFromNbt(persisted.getTagList(PENDING_DROPS, 10));
        persisted.removeTag(PENDING_DROPS);
        if (allowTaskWorld) distributeDrops(task.drops, player, task.destination);
        if (!task.drops.isEmpty()) {
            boolean released = false;
            if (!task.dropToPlayer && allowTaskWorld) {
                released = tryReleaseDrops(
                    task.drops,
                    task.world,
                    task.originX + 0.5D,
                    task.originY + 0.5D,
                    task.originZ + 0.5D);
            }
            if (!released && (allowTaskWorld || player.worldObj != task.world)) {
                released = tryReleaseDrops(task.drops, player.worldObj, player.posX, player.posY, player.posZ);
            }
            if (!released && allowTaskWorld) {
                tryReleaseDrops(task.drops, task.world, task.originX + 0.5D, task.originY + 0.5D, task.originZ + 0.5D);
            }
            if (!task.drops.isEmpty()) persisted.setTag(PENDING_DROPS, task.drops.writeToNbt());
        }
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        if (notify) player
            .addChatMessage(new ChatComponentTranslation("tenkeitech.universal_wand.chain_complete", task.brokenCount));
    }

    private static void releasePendingDrops(EntityPlayerMP player) {
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(PENDING_DROPS, 9)) return;
        UniversalWandDropBuffer drops = new UniversalWandDropBuffer();
        drops.readFromNbt(persisted.getTagList(PENDING_DROPS, 10));
        tryReleaseDrops(drops, player.worldObj, player.posX, player.posY, player.posZ);
        if (drops.isEmpty()) persisted.removeTag(PENDING_DROPS);
        else persisted.setTag(PENDING_DROPS, drops.writeToNbt());
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    /** Building refunds use the existing legal-stack distribution and persistent overflow recovery. */
    public static void refundBuildingMaterials(EntityPlayerMP player, java.util.List<ItemStack> materials) {
        refundBuildingMaterials(player, materials, new UniversalWandStorage.Destination(false, true));
    }

    public static void refundBuildingMaterials(EntityPlayerMP player, java.util.List<ItemStack> materials,
        UniversalWandStorage.Destination destination) {
        if (materials.isEmpty()) return;
        UniversalWandDropBuffer drops = new UniversalWandDropBuffer();
        for (ItemStack material : materials) drops.collect(material);
        releaseSoloDrops(
            drops,
            destination,
            player,
            player.worldObj,
            (int) Math.floor(player.posX),
            (int) Math.floor(player.posY),
            (int) Math.floor(player.posZ));
    }

    private static boolean tryReleaseDrops(UniversalWandDropBuffer drops, World world, double x, double y, double z) {
        Boolean previous = RELEASING.get();
        RELEASING.set(true);
        try {
            return drops.flush(world, x, y, z);
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.error("Universal Wand drops retained after an item spawn failure", failure);
            return false;
        } finally {
            if (previous == null) RELEASING.remove();
            else RELEASING.set(previous);
        }
    }

    private static void distributeDrops(UniversalWandDropBuffer drops, EntityPlayerMP player,
        UniversalWandStorage.Destination destination) {
        Boolean previous = RELEASING.get();
        RELEASING.set(true);
        try {
            drops.distribute(player, destination);
        } catch (RuntimeException failure) {
            TenkeiTech.LOG.error("Universal Wand distribution stopped; remaining drops retained", failure);
        } finally {
            if (previous == null) RELEASING.remove();
            else RELEASING.set(previous);
        }
    }

    private static void releaseSoloDrops(UniversalWandDropBuffer drops, UniversalWandStorage.Destination destination,
        EntityPlayerMP player, World world, int x, int y, int z) {
        NBTTagCompound persisted = player.getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        drops.readFromNbt(persisted.getTagList(PENDING_DROPS, 10));
        persisted.removeTag(PENDING_DROPS);
        distributeDrops(drops, player, destination);
        if (!tryReleaseDrops(drops, world, x + 0.5D, y + 0.5D, z + 0.5D))
            tryReleaseDrops(drops, player.worldObj, player.posX, player.posY, player.posZ);
        if (!drops.isEmpty()) persisted.setTag(PENDING_DROPS, drops.writeToNbt());
        player.getEntityData()
            .setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    public static void distributeBedrock(EntityPlayerMP player, ItemStack wand, int x, int y, int z) {
        UniversalWandDropBuffer drops = new UniversalWandDropBuffer();
        drops.collect(new ItemStack(net.minecraft.init.Blocks.bedrock));
        releaseSoloDrops(drops, new UniversalWandStorage.Destination(wand), player, player.worldObj, x, y, z);
    }

    /** Called before server player/world saves, while the destination chunks are still available. */
    public static void finishAllChains() {
        for (MiningTask task : TASKS.values()) finishTask(task, false, true);
        KEY_HELD.clear();
        KEY_LAST_HELD.clear();
        START_REQUESTS.clear();
        LAST_BREAKS.clear();
        UniversalWandActions.clearAll();
        UniversalWandAutoHarvestHandler.clearAll();
        nextPlayer = 0;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        UniversalWandAutoHarvestHandler.unload(event.world);
        for (java.util.Map.Entry<java.util.UUID, MiningTask> entry : TASKS.entrySet()) {
            if (entry.getValue().world == event.world) {
                finishTask(entry.getValue(), false, false);
                clearPlayer(entry.getKey());
            }
        }
        for (java.util.Map.Entry<java.util.UUID, ChainRequest> entry : START_REQUESTS.entrySet()) {
            if (entry.getValue().player.worldObj == event.world) clearPlayer(entry.getKey());
        }
    }

    private static void clearPlayer(java.util.UUID id) {
        MiningTask task = TASKS.get(id);
        if (task != null) finishTask(task, false, true);
        KEY_HELD.remove(id);
        KEY_LAST_HELD.remove(id);
        START_REQUESTS.remove(id);
        LAST_BREAKS.remove(id);
        UniversalWandActions.clear(id);
        UniversalWandAutoHarvestHandler.clear(id);
    }

    private static boolean isReachable(EntityPlayerMP player, int x, int y, int z) {
        double dx = player.posX - (x + 0.5D);
        double dy = player.posY + player.getEyeHeight() - (y + 0.5D);
        double dz = player.posZ - (z + 0.5D);
        double reach = player.theItemInWorldManager.getBlockReachDistance() + 1D;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private static void enqueueChain(EntityPlayerMP player, int sx, int sy, int sz, ItemStack stack,
        int initialBrokenCount) {
        World world = player.worldObj;
        enqueueChain(
            player,
            sx,
            sy,
            sz,
            stack,
            world.getBlock(sx, sy, sz),
            world.getBlockMetadata(sx, sy, sz),
            isGtOre(world, sx, sy, sz),
            isGtSmallOre(world, sx, sy, sz),
            initialBrokenCount);
    }

    private static void enqueueChain(EntityPlayerMP player, int sx, int sy, int sz, ItemStack stack, Block target,
        int metadata, boolean gtOreTarget, boolean targetSmall, int initialBrokenCount) {
        if (TASKS.containsKey(player.getUniqueID())) return;
        World world = player.worldObj;
        int maxBlocks = ItemUniversalWand.getMaxMiningCount(stack);
        UniversalWandSearch search = createSearch(
            world,
            sx,
            sy,
            sz,
            stack,
            target,
            metadata,
            gtOreTarget,
            targetSmall,
            maxBlocks);
        MiningTask task = new MiningTask(player, stack, search, initialBrokenCount, sx, sy, sz);
        TASKS.put(player.getUniqueID(), task);
    }

    private static boolean matchesTarget(World world, int x, int y, int z, Block target, int metadata,
        boolean gtOreTarget, boolean targetSmall, boolean includeSmall) {
        if (gtOreTarget) {
            if (includeSmall) return isGtOre(world, x, y, z);
            return targetSmall ? isGtSmallOre(world, x, y, z) : isGtLargeVeinOre(world, x, y, z);
        }
        return world.getBlock(x, y, z) == target && world.getBlockMetadata(x, y, z) == metadata;
    }

    /** GTNH beta-3 ore classification, shared by previews and actual mining. */
    private static boolean isGtOre(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof GTBlockOre;
    }

    private static boolean isGtSmallOre(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof GTBlockOre ore && ore.isSmallOre(world.getBlockMetadata(x, y, z));
    }

    private static boolean isGtLargeVeinOre(World world, int x, int y, int z) {
        return isGtOre(world, x, y, z) && !isGtSmallOre(world, x, y, z);
    }

}
