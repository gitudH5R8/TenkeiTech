/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.integration.gt5u.WandAssemblyAcceleration;
import com.tenkeitech.integration.gt5u.WandResearchAcceleration;
import com.tenkeitech.item.ItemUniversalWand;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEBrickedBlastFurnace;

/** Coordinate-owned acceleration; only permanent tasks belong to the world ledger. */
public final class UniversalWandAccelerationHandler {

    private static final int DURATION = 600;
    private static SavedTasks saved;
    private static final Map<TargetKey, Task> TASKS = new ConcurrentHashMap<>();
    private static final java.util.Queue<Runnable> MANAGEMENT = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static String history(UUID owner, int page) {
        ensureSavedTasks();
        java.util.List<Map.Entry<TargetKey, Task>> entries = TASKS.entrySet()
            .stream()
            .filter(entry -> entry.getValue().mode != 0 && entry.getValue().owner.equals(owner))
            .sorted(
                java.util.Comparator.comparingInt((Map.Entry<TargetKey, Task> entry) -> entry.getKey().dimension)
                    .thenComparingInt(entry -> entry.getKey().x)
                    .thenComparingInt(entry -> entry.getKey().y)
                    .thenComparingInt(entry -> entry.getKey().z))
            .collect(java.util.stream.Collectors.toList());
        int pages = Math.max(1, (entries.size() + 7) / 8);
        int current = Math.max(0, Math.min(page, pages - 1));
        StringBuilder result = new StringBuilder().append(current)
            .append(',')
            .append(pages)
            .append(',')
            .append(entries.size());
        for (int i = current * 8; i < Math.min(entries.size(), current * 8 + 8); i++) {
            TargetKey key = entries.get(i)
                .getKey();
            Task task = entries.get(i)
                .getValue();
            if (task.displayName.isEmpty()) captureName(key, task);
            result.append(';')
                .append(key.dimension)
                .append(',')
                .append(key.x)
                .append(',')
                .append(key.y)
                .append(',')
                .append(key.z)
                .append(',')
                .append(task.rate)
                .append(',')
                .append(task.mode)
                .append(',')
                .append(encode(task.displayKey))
                .append(',')
                .append(encode(task.displayName))
                .append(',')
                .append(task.gtMode ? 1 : 0);
        }
        return result.toString();
    }

    public static void requestManagement(net.minecraft.entity.player.EntityPlayerMP player, int action, int dimension,
        int x, int y, int z) {
        MANAGEMENT.add(() -> {
            if (player.isDead || player.worldObj == null
                || !player.worldObj.playerEntities.contains(player)
                || !ItemUniversalWand.isHeldBy(player)) return;
            if (action == 0 || action == 1) {
                ItemStack stack = player.getHeldItem();
                if (!ItemUniversalWand.isTimeAccelerationEnabled(stack)) return;
                int[] rates = java.util.stream.IntStream
                    .concat(
                        java.util.stream.IntStream.of(0),
                        java.util.Arrays.stream(com.tenkeitech.Config.universalWandAccelerationRates))
                    .toArray();
                int index = 0;
                for (int i = 0; i < rates.length; i++)
                    if (rates[i] == ItemUniversalWand.getAccelerationRate(stack)) index = i;
                int rate = rates[Math.floorMod(index + (action == 1 ? 1 : -1), rates.length)];
                if (ItemUniversalWand.setAccelerationRate(player, stack, rate))
                    UniversalWandNetwork.syncAccelerationNotice(player, rate);
            } else if (action == 4) {
                TASKS.forEach(
                    (key, task) -> {
                        if (task.mode != 0 && task.owner.equals(player.getUniqueID())) captureName(key, task);
                    });
            } else if (action == 2 || action == 3) {
                TASKS.entrySet()
                    .stream()
                    .filter(
                        entry -> entry.getValue().mode != 0 && entry.getValue().owner.equals(player.getUniqueID())
                            && (action == 3 || entry.getKey()
                                .equals(new TargetKey(dimension, x, y, z))))
                    .collect(java.util.stream.Collectors.toList())
                    .forEach(entry -> remove(entry.getKey(), entry.getValue()));
            }
        });
    }

    private static final ThreadLocal<TileEntity> UPDATING = new ThreadLocal<>();
    private static final ThreadLocal<TileEntity> COMPLETING_RECIPE = new ThreadLocal<>();

    public static void use(EntityPlayerMP player, int x, int y, int z) {
        if (player == null || player.worldObj == null
            || !ItemUniversalWand.isHeldBy(player)
            || !ItemUniversalWand.isTimeAccelerationEnabled(player.getHeldItem())) return;
        ensureSavedTasks();
        World world = player.worldObj;
        if (!loaded(world, x, z) || !UniversalWandActions.isTarget(player, x, y, z)
            || !player.canPlayerEdit(x, y, z, 1, player.getHeldItem())
            || !world.canMineBlock(player, x, y, z)) return;
        MovingObjectPosition hit = UniversalWandActions.rayTrace(player);
        PlayerInteractEvent interaction = ForgeEventFactory
            .onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, x, y, z, hit.sideHit, world);
        if (interaction.isCanceled() || interaction.useBlock == cpw.mods.fml.common.eventhandler.Event.Result.DENY
            || interaction.useItem == cpw.mods.fml.common.eventhandler.Event.Result.DENY) return;
        TargetKey key = new TargetKey(player.dimension, x, y, z);
        Task task = TASKS.get(key);
        if (task != null && !task.owner.equals(player.getUniqueID())) {
            return;
        }
        int rate = ItemUniversalWand.getAccelerationRate(player.getHeldItem());
        if (rate == 0) {
            if (task != null) {
                remove(key, task);
                UniversalWandRightClick.handled(player, true);
            }
            return;
        }
        TileEntity tile = player.worldObj.getTileEntity(x, y, z);
        Block block = world.getBlock(x, y, z);
        if (!(tile == null ? block.getTickRandomly() : isValid(tile))) {
            return;
        }
        if (task == null) {
            task = new Task(player.getUniqueID());
            TASKS.put(key, task);
        }
        task.rate = rate;
        task.mode = ItemUniversalWand.getAccelerationMode(player.getHeldItem());
        task.gtMode = ItemUniversalWand.isAccelerationGtMode(player.getHeldItem());
        task.remaining = task.mode == 0 ? DURATION : -1;
        task.probe = new ActivityProbe();
        captureName(key, task);
        task.throttled = false;
        save(key, task);
        sync(key, task);
        UniversalWandRightClick.handled(player, true);
    }

    private static boolean loaded(World world, int x, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    public static boolean isUpdating(TileEntity tile) {
        return UPDATING.get() == tile;
    }

    public static boolean isCompletingRecipe(TileEntity tile) {
        return tile != null && COMPLETING_RECIPE.get() == tile;
    }

    private static boolean isValid(TileEntity tile) {
        return tile != null && !tile.isInvalid() && tile.canUpdate();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ensureSavedTasks();
        if (!UniversalWandFeature.ACCELERATION.allowed()) {
            discardDisabledTasks();
            return;
        }
        Runnable management;
        while ((management = MANAGEMENT.poll()) != null) management.run();
        for (Map.Entry<TargetKey, Task> entry : TASKS.entrySet()) {
            Task task = entry.getValue();
            if (task.remaining == 0) {
                remove(entry.getKey(), task);
                continue;
            }
            try {
                accelerate(entry.getKey(), task);
                if (task.remaining > 0) task.remaining--;
                if (++task.syncTicks >= 20) {
                    task.syncTicks = 0;
                    sync(entry.getKey(), task);
                }
            } catch (Exception failure) {
                remove(entry.getKey(), task);
                TenkeiTech.LOG.warn(
                    "Universal wand acceleration stopped in dimension {} at {}, {}, {}",
                    entry.getKey().dimension,
                    entry.getKey().x,
                    entry.getKey().y,
                    entry.getKey().z,
                    failure);
            } finally {
                UPDATING.remove();
            }
        }
    }

    private static void accelerate(TargetKey key, Task task) {
        World world = DimensionManager.getWorld(key.dimension);
        if (world == null || !loaded(world, key.x, key.z)) {
            task.probe = new ActivityProbe();
            task.throttled = false;
            return;
        }
        TileEntity tile = world.getTileEntity(key.x, key.y, key.z);
        Block block = world.getBlock(key.x, key.y, key.z);
        if (!(tile == null ? block.getTickRandomly() : isValid(tile))) return;
        int extra = task.rate;
        task.throttled = task.mode == 1 && task.probe.isIdle(tile);
        if (task.throttled) extra = Math.min(extra, 4);
        if (task.gtMode && tile instanceof IGregTechTileEntity gt && accelerateGt(gt, tile, extra)) return;
        for (int i = 0; i < extra; i++) {
            // Stop this batch when the target changes; the next server tick resolves the new target.
            if (!loaded(world, key.x, key.z) || world.getBlock(key.x, key.y, key.z) != block
                || world.getTileEntity(key.x, key.y, key.z) != tile) break;
            if (tile != null) {
                if (!isValid(tile)) break;
                if (task.gtMode) UPDATING.set(tile);
                if (task.gtMode && tile instanceof IGregTechTileEntity gt
                    && gt.getMetaTileEntity() instanceof WandAssemblyAcceleration assembly
                    && (assembly.tenkeitech$isAssemblyBlocked() || !gt.isActive())) break;
                tile.updateEntity();
            } else {
                // Each requested extra tick invokes the target directly; its own growth conditions still apply.
                if (!block.getTickRandomly()) break;
                block.updateTick(world, key.x, key.y, key.z, world.rand);
            }
        }
    }

    private static boolean accelerateGt(IGregTechTileEntity gt, TileEntity tile, int rate) {
        IMetaTileEntity meta = gt.getMetaTileEntity();
        if (!gt.isActive()) return true;
        if (meta instanceof WandResearchAcceleration research) {
            research.tenkeitech$accelerateResearch(rate);
            return true;
        }
        if (meta instanceof WandAssemblyAcceleration) return false;
        if (meta instanceof MTEBasicMachine || meta instanceof MTEMultiBlockBase
            || meta instanceof MTEBrickedBlastFurnace) {
            World world = tile.getWorldObj();
            Block block = world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord);
            long remaining = rate;
            while (remaining > 0 && isValid(tile)
                && gt.isActive()
                && gt.isAllowedToWork()
                && gt.getMetaTileEntity() == meta
                && loaded(world, tile.xCoord, tile.zCoord)
                && world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord) == tile
                && world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord) == block) {
                int maximum = gtMaximum(meta);
                int current = gtProgress(meta);
                // Negative progress is the single-block machine's low-energy recovery state.
                if (maximum <= 0 || current < 0) break;
                long untilComplete = Math.max(1L, (long) maximum - current);
                if (remaining < untilComplete) {
                    setGtProgress(meta, (int) ((long) current + remaining));
                    break;
                }
                // Skip waiting ticks; let the native lifecycle own completion and the next recipe.
                int boundary = maximum - 1;
                setGtProgress(meta, boundary);
                UPDATING.set(tile);
                COMPLETING_RECIPE.set(tile);
                try {
                    tile.updateEntity();
                } finally {
                    COMPLETING_RECIPE.remove();
                    UPDATING.remove();
                }
                remaining -= untilComplete;
                if (gtMaximum(meta) == maximum && gtProgress(meta) >= boundary) break;
            }
            return true;
        }
        return gt.getMaxProgress() < 2;
    }

    private static int gtProgress(IMetaTileEntity meta) {
        if (meta instanceof MTEBasicMachine machine) return machine.mProgresstime;
        if (meta instanceof MTEMultiBlockBase machine) return machine.mProgresstime;
        return ((MTEBrickedBlastFurnace) meta).mProgresstime;
    }

    private static int gtMaximum(IMetaTileEntity meta) {
        if (meta instanceof MTEBasicMachine machine) return machine.mMaxProgresstime;
        if (meta instanceof MTEMultiBlockBase machine) return machine.mMaxProgresstime;
        return ((MTEBrickedBlastFurnace) meta).mMaxProgresstime;
    }

    private static void setGtProgress(IMetaTileEntity meta, int progress) {
        if (meta instanceof MTEBasicMachine machine) machine.mProgresstime = progress;
        else if (meta instanceof MTEMultiBlockBase machine) machine.mProgresstime = progress;
        else((MTEBrickedBlastFurnace) meta).mProgresstime = progress;
    }

    private static void sync(TargetKey key, Task task) {
        UniversalWandNetwork.syncAcceleration(
            null,
            key.dimension,
            key.x,
            key.y,
            key.z,
            task.rate,
            task.remaining,
            task.mode,
            task.throttled);
    }

    public static void cancel(UUID owner) {
        ensureSavedTasks();
        TASKS.entrySet()
            .removeIf(entry -> {
                Task task = entry.getValue();
                if (!task.owner.equals(owner)) return false;
                task.remaining = 0;
                if (saved != null) {
                    saved.tasks.remove(entry.getKey());
                    saved.markDirty();
                }
                sync(entry.getKey(), task);
                return true;
            });
    }

    public static void clearAll() {
        MANAGEMENT.clear();
        TASKS.clear();
        saved = null;
        COMPLETING_RECIPE.remove();
        UPDATING.remove();
    }

    private static final class Task {

        final UUID owner;
        String displayKey = "";
        String displayName = "";
        int rate = 2;
        int mode;
        int remaining = DURATION;
        int syncTicks;
        boolean throttled;
        boolean gtMode = true;
        ActivityProbe probe = new ActivityProbe();

        Task(UUID owner) {
            this.owner = owner;
        }
    }

    private static String encode(String text) {
        return java.util.Base64.getEncoder()
            .encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void captureName(TargetKey key, Task task) {
        World world = DimensionManager.getWorld(key.dimension);
        if (world == null || !loaded(world, key.x, key.z)) return;
        try {
            TileEntity tile = world.getTileEntity(key.x, key.y, key.z);
            ItemStack stack = tile instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() != null
                ? gt.getMetaTileEntity()
                    .getStackForm(1L)
                : new ItemStack(world.getBlock(key.x, key.y, key.z), 1, world.getBlockMetadata(key.x, key.y, key.z));
            if (stack.getItem() != null) {
                task.displayKey = stack.getUnlocalizedName() + ".name";
                task.displayName = stack.getDisplayName();
            } else task.displayName = world.getBlock(key.x, key.y, key.z)
                .getLocalizedName();
            if (saved != null && task.mode != 0) saved.markDirty();
        } catch (RuntimeException ignored) {
            // The coordinate remains manageable even when a third-party item cannot provide a name.
        }
    }

    private static void ensureSavedTasks() {
        World world = DimensionManager.getWorld(0);
        if (saved != null || world == null) return;
        saved = (SavedTasks) world.mapStorage.loadData(SavedTasks.class, SavedTasks.NAME);
        if (saved == null) {
            saved = new SavedTasks();
            world.mapStorage.setData(SavedTasks.NAME, saved);
        }
        TASKS.putAll(saved.tasks);
        if (!UniversalWandFeature.ACCELERATION.allowed()) discardDisabledTasks();
    }

    @SubscribeEvent
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) ensureSavedTasks();
    }

    private static void discardDisabledTasks() {
        MANAGEMENT.clear();
        TASKS.forEach(UniversalWandAccelerationHandler::remove);
        if (saved != null && !saved.tasks.isEmpty()) {
            saved.tasks.clear();
            saved.markDirty();
        }
    }

    private static void save(TargetKey key, Task task) {
        if (saved == null) return;
        if (task.mode == 0) saved.tasks.remove(key);
        else saved.tasks.put(key, task);
        saved.markDirty();
    }

    private static void remove(TargetKey key, Task task) {
        TASKS.remove(key, task);
        task.remaining = 0;
        if (saved != null) {
            saved.tasks.remove(key);
            saved.markDirty();
        }
        sync(key, task);
    }

    public static final class SavedTasks extends WorldSavedData {

        private static final String NAME = "tenkeitech_wand_acceleration";
        private final Map<TargetKey, Task> tasks = new java.util.HashMap<>();

        public SavedTasks() {
            super(NAME);
        }

        public SavedTasks(String name) {
            super(name);
        }

        @Override
        public void readFromNBT(NBTTagCompound root) {
            tasks.clear();
            if (root.getInteger("schema") != 1) return;
            NBTTagList list = root.getTagList("tasks", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int mode = tag.getInteger("mode"), rate = tag.getInteger("rate"), y = tag.getInteger("y");
                if (mode < 1 || mode > 2
                    || rate <= 0
                    || y < 0
                    || y > 255
                    || !tag.hasKey("ownerMost", 4)
                    || !tag.hasKey("ownerLeast", 4)
                    || !tag.hasKey("gtMode", 1)) continue;
                Task task = new Task(new UUID(tag.getLong("ownerMost"), tag.getLong("ownerLeast")));
                task.displayKey = tag.getString("displayKey");
                task.displayName = tag.getString("displayName");
                task.mode = mode;
                task.gtMode = tag.getBoolean("gtMode");
                task.rate = rate;
                task.remaining = -1;
                tasks.putIfAbsent(
                    new TargetKey(tag.getInteger("dimension"), tag.getInteger("x"), y, tag.getInteger("z")),
                    task);
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound root) {
            root.setInteger("schema", 1);
            NBTTagList list = new NBTTagList();
            for (Map.Entry<TargetKey, Task> entry : tasks.entrySet()) {
                TargetKey key = entry.getKey();
                Task task = entry.getValue();
                NBTTagCompound tag = new NBTTagCompound();
                tag.setLong("ownerMost", task.owner.getMostSignificantBits());
                tag.setLong("ownerLeast", task.owner.getLeastSignificantBits());
                tag.setString("displayKey", task.displayKey);
                tag.setString("displayName", task.displayName);
                tag.setInteger("dimension", key.dimension);
                tag.setInteger("x", key.x);
                tag.setInteger("y", key.y);
                tag.setInteger("z", key.z);
                tag.setInteger("mode", task.mode);
                tag.setBoolean("gtMode", task.gtMode);
                tag.setInteger("rate", task.rate);
                list.appendTag(tag);
            }
            root.setTag("tasks", list);
        }
    }

    private static final class ActivityProbe {

        private java.lang.ref.WeakReference<TileEntity> target = new java.lang.ref.WeakReference<>(null);
        private long fingerprint;
        private boolean sampled, observedWorking, known;
        private int idleTicks, sampleTicks;
        private int retryTicks;

        boolean isIdle(TileEntity tile) {
            if (tile != target.get()) {
                target = new java.lang.ref.WeakReference<>(tile);
                sampled = false;
                observedWorking = false;
                known = false;
                idleTicks = 0;
                sampleTicks = 0;
                retryTicks = 0;
            }
            if (retryTicks > 0) {
                retryTicks--;
                return false;
            }
            try {
                return sample(tile);
            } catch (Exception ignored) {
                retryTicks = 200;
                return false;
            }
        }

        private boolean sample(TileEntity tile) {
            if (tile instanceof IGregTechTileEntity gt) {
                known = true;
                if (gt.isActive()) {
                    observedWorking = true;
                    idleTicks = 0;
                } else idleTicks++;
            } else if (tile instanceof IInventory || tile instanceof IFluidHandler) {
                idleTicks++;
                if (++sampleTicks >= 5) {
                    sampleTicks = 0;
                    known = false;
                    long current = 1;
                    if (tile instanceof IInventory inventory) {
                        if (inventory.getSizeInventory() > 64) return false;
                        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                            ItemStack stack = inventory.getStackInSlot(slot);
                            current = current * 31 + (stack == null ? 0 : itemStackHash(stack));
                        }
                    }
                    if (tile instanceof IFluidHandler fluid) {
                        FluidTankInfo[] tanks = fluid.getTankInfo(ForgeDirection.UNKNOWN);
                        if (tanks == null || tanks.length > 16) return false;
                        for (FluidTankInfo tank : tanks)
                            current = current * 31 + (tank == null || tank.fluid == null ? 0
                                : tank.fluid.getFluidID() * 31L + tank.fluid.amount
                                    + (tank.fluid.tag == null ? 0 : tank.fluid.tag.hashCode()));
                    }
                    if (!sampled || current != fingerprint) {
                        if (sampled) observedWorking = true;
                        fingerprint = current;
                        sampled = true;
                        idleTicks = 0;
                    }
                    known = true;
                }
            } else return false;
            return known && observedWorking && idleTicks > 200;
        }

        private static long itemStackHash(ItemStack stack) {
            return ((long) net.minecraft.item.Item.getIdFromItem(stack.getItem()) * 31 + stack.getItemDamage()) * 31
                + stack.stackSize
                + (stack.getTagCompound() == null ? 0
                    : stack.getTagCompound()
                        .hashCode());
        }
    }

    private static final class TargetKey {

        final int dimension, x, y, z;

        TargetKey(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int hashCode() {
            return (((dimension * 31 + x) * 31 + y) * 31 + z);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TargetKey key)) return false;
            return dimension == key.dimension && x == key.x && y == key.y && z == key.z;
        }
    }
}
