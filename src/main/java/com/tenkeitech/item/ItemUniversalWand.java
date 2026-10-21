/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.google.common.collect.Multimap;
import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.gui.UniversalWandGui;
import com.tenkeitech.integration.backhand.UniversalWandBackhandIntegration;
import com.tenkeitech.mixin.client.PlayerControllerMPAccessor;
import com.tenkeitech.universalwand.UniversalWandActions;
import com.tenkeitech.universalwand.UniversalWandBuildingHandler;
import com.tenkeitech.universalwand.UniversalWandFeature;
import com.tenkeitech.universalwand.UniversalWandMiningHandler;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.universalwand.UniversalWandStorage;
import com.tenkeitech.universalwand.building.BuildingUndoHistory;

import appeng.api.implementations.items.IAEWrench;
import appeng.api.implementations.tiles.IWirelessAccessPoint;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.core.sync.GuiBridge;
import appeng.tile.AEBaseTile;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.util.Platform;
import buildcraft.api.tools.IToolWrench;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import crazypants.enderio.api.tool.ITool;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.GTItemStack;
import gregtech.api.util.GTUtility;

/** Phase-one universal wand core. Later capabilities are added behind this state boundary. */
@Optional.InterfaceList(
    value = { @Optional.Interface(iface = "appeng.api.implementations.items.IAEWrench", modid = "appliedenergistics2"),
        @Optional.Interface(iface = "buildcraft.api.tools.IToolWrench", modid = "BuildCraft|Core"),
        @Optional.Interface(iface = "crazypants.enderio.api.tool.ITool", modid = "EnderIO"),
        @Optional.Interface(iface = "crazypants.enderio.api.teleport.IItemOfTravel", modid = "EnderIO") })
public final class ItemUniversalWand extends Item
    implements IGuiHolder<GuiData>, IAEWrench, IToolWrench, ITool, crazypants.enderio.api.teleport.IItemOfTravel {

    public enum EnchantMode {
        SILK,
        FORTUNE
    }

    private static final String NBT_SETTINGS = "tenkeitechUniversalWand";
    private static final String NBT_SCHEMA = "schemaVersion";
    private static final String NBT_MODE = "enchantMode";
    private static final String NBT_BREAK_COOLDOWN = "breakCooldown";
    private static final String NBT_PROTECTION = "protectionMode";
    private static final String NBT_FORCE_BEDROCK = "forceBedrock";
    private static final String NBT_ENHANCED_CHAIN = "enhancedChain";
    private static final String NBT_GT_SMALL_CHAIN = "gtSmallChain";
    private static final String NBT_ENHANCED_CHAIN_RADIUS = "enhancedChainRadius";
    private static final String NBT_MAX_MINING_COUNT = "maxMiningCount";
    private static final String NBT_AE_BINDING = "aeBinding";
    private static final String NBT_AE_PRIORITY = "aeStoragePriority";
    private static final String NBT_INVENTORY_COLLECTION = "collectToInventory";
    private static final String NBT_SHEARS = "shearsEnabled";
    private static final String NBT_HOE = "hoeEnabled";
    private static final String NBT_BUILDING = "buildingEnabled";
    private static final String NBT_BUILDING_CORE = "buildingCore";
    private static final String NBT_BUILDING_AE = "buildingAeEnabled";
    private static final String NBT_FORCE_KILL = "forceKill";
    private static final String NBT_AOE_SIZE = "aoeSize";
    private static final String NBT_BEHEADING = "beheading";
    private static final String NBT_SPAWN_EGG = "spawnEgg";
    private static final String NBT_TELEPORT = "teleportEnabled";
    private static final String NBT_PLAYER_PROTECTION = "playerProtection";
    private static final String NBT_NO_AGGRO = "noAggro";
    private static final String NBT_TIME_ACCELERATION = "timeAcceleration";
    private static final String NBT_ACCELERATION_RATE = "accelerationRate";
    private static final String NBT_ACCELERATION_MODE = "accelerationMode";
    private static final String NBT_ACCELERATION_GT_MODE = "accelerationGtMode";
    public static final int MAX_AOE_SIZE = 27;
    private static final int DEFAULT_ENHANCED_CHAIN_RADIUS = 16;
    private static final int DEFAULT_MAX_MINING_COUNT = 256;
    public static final int MAX_ENHANCED_CHAIN_RADIUS = 128;
    private static final String NBT_TOOL_MODE = "gtToolMode";

    public enum GtToolMode {
        OFF,
        WRENCH,
        WIRE_CUTTER
    }

    public enum BuildingCore {
        NORMAL,
        ANGEL,
        DESTRUCTION
    }

    private static final int SETTINGS_SCHEMA = 1;
    private static final double ATTACK_DAMAGE = 114514D;
    private static final java.util.UUID ATTACK_MODIFIER_ID = java.util.UUID
        .fromString("a84cf497-f821-4f11-85e8-baa799190f31");
    private static final String ATTACK_MODIFIER_NAME = "TenkeiTech Universal Wand damage";
    public static final double REACH_BONUS = 8D;

    public static boolean isHeldBy(EntityPlayer player) {
        return player != null && player.getHeldItem() != null
            && player.getHeldItem()
                .getItem() instanceof ItemUniversalWand;
    }

    public static boolean hasReachBonus(EntityPlayer player) {
        if (player == null) return false;
        ItemStack mainhand = Loader.isModLoaded("backhand") ? UniversalWandBackhandIntegration.getMainhandItem(player)
            : player.getHeldItem();
        return mainhand != null && mainhand.getItem() instanceof ItemUniversalWand;
    }

    public ItemUniversalWand() {
        setUnlocalizedName(TenkeiTech.MOD_ID + ".universal_wand");
        setTextureName(TenkeiTech.MOD_ID + ":universal_wand");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
        setMaxDamage(0);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, EntityPlayer player) {
        return false;
    }

    @Override
    public int getEntityLifespan(ItemStack stack, World world) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    @Override
    public Entity createEntity(World world, Entity original, ItemStack stack) {
        com.tenkeitech.universalwand.EntityUselessWandItem dropped = new com.tenkeitech.universalwand.EntityUselessWandItem(
            world,
            original.posX,
            original.posY,
            original.posZ,
            stack);
        dropped.motionX = original.motionX;
        dropped.motionY = original.motionY;
        dropped.motionZ = original.motionZ;
        if (original instanceof net.minecraft.entity.item.EntityItem item) {
            dropped.delayBeforeCanPickup = item.delayBeforeCanPickup;
            dropped.age = item.age;
            dropped.hoverStart = item.hoverStart;
            dropped.func_145797_a(item.func_145798_i());
            dropped.func_145799_b(item.func_145800_j());
        }
        return dropped;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!player.isSneaking() && isBuildingEnabled(stack)) {
            boolean undo = world.isRemote ? TenkeiTech.proxy.isWandUndoKeyHeld() : BuildingUndoHistory.isActive(player);
            if (!undo && getBuildingCore(stack) == BuildingCore.ANGEL) {
                if (!world.isRemote && player instanceof EntityPlayerMP mp) UniversalWandBuildingHandler.requestAir(mp);
            }
        }
        if (world.isRemote) TenkeiTech.proxy.useWandTravelIfImmediate(stack, player);
        return stack;
    }

    /** Opens the settings screen after the dedicated key packet has been validated server-side. */
    public static void openSettingsGui(EntityPlayerMP player) {
        if (player == null) return;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)) return;
        ensureEnchantMode(stack);
        player.sendContainerToPlayer(player.inventoryContainer);
        GuiFactories.playerInventory()
            .openFromMainHand(player);
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (player.isSneaking() && isTimeAccelerationEnabled(stack)) {
            if (world.isRemote) return false;
            if (player instanceof EntityPlayerMP mp)
                com.tenkeitech.universalwand.UniversalWandAccelerationHandler.use(mp, x, y, z);
            return true;
        }
        boolean undo = isBuildingEnabled(stack)
            && (world.isRemote ? TenkeiTech.proxy.isWandUndoKeyHeld() : BuildingUndoHistory.isActive(player));
        if (undo) {
            if (world.isRemote) {
                UniversalWandNetwork.requestUndoKey(true);
                UniversalWandNetwork
                    .requestBuildStart(x, y, z, side, hitX, hitY, hitZ, getBuildingCore(stack).ordinal(), true);
            } else if (player instanceof EntityPlayerMP mp) UniversalWandBuildingHandler
                .requestStart(mp, x, y, z, side, hitX, hitY, hitZ, getBuildingCore(stack).ordinal(), true);
            return true;
        }
        if (player.isSneaking() && world.getTileEntity(x, y, z) instanceof IWirelessAccessPoint) {
            // A true client result prevents 1.7.10 from sending the block-use packet.
            if (world.isRemote) return false;
            if (player instanceof EntityPlayerMP mp) UniversalWandStorage.toggleBinding(mp, x, y, z);
            return true;
        }
        if (!player.isSneaking() && isBuildingEnabled(stack)) {
            if (world.isRemote) UniversalWandNetwork
                .requestBuildStart(x, y, z, side, hitX, hitY, hitZ, getBuildingCore(stack).ordinal());
            else if (player instanceof EntityPlayerMP mp) UniversalWandBuildingHandler
                .requestStart(mp, x, y, z, side, hitX, hitY, hitZ, getBuildingCore(stack).ordinal());
            return true;
        }
        GtToolMode mode = getGtToolMode(stack);
        if (mode == GtToolMode.OFF) {
            return false;
        }
        if (world.isRemote) return false;
        ForgeDirection clickedSide = ForgeDirection.getOrientation(side);
        if (mode == GtToolMode.WIRE_CUTTER) {
            int[] cable = findGtCableOnViewRay(player, world);
            if (cable != null && !(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity)) {
                x = cable[0];
                y = cable[1];
                z = cable[2];
                clickedSide = ForgeDirection.getOrientation(cable[3]);
                hitX = cable[4] / 1000F;
                hitY = cable[5] / 1000F;
                hitZ = cable[6] / 1000F;
            }
            return com.tenkeitech.universalwand.UniversalWandRightClick.handled(
                player,
                GtToolBackend.handleWireCutter(stack, player, world, x, y, z, clickedSide, hitX, hitY, hitZ));
        }
        return com.tenkeitech.universalwand.UniversalWandRightClick
            .handled(player, GtToolBackend.handleWrench(stack, player, world, x, y, z, clickedSide, hitX, hitY, hitZ));
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return com.tenkeitech.universalwand.UniversalWandRightClick
            .handled(player, VanillaToolBackend.useHoe(stack, player, world, x, y, z, side, hitX, hitY, hitZ));
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target) {
        // Creative interaction supplies a copy; settings and restoration belong to the actual held wand.
        if (player == null || !isHeldBy(player)
            || player.getHeldItem()
                .getItem() != this)
            return false;
        return com.tenkeitech.universalwand.UniversalWandRightClick
            .handled(player, VanillaToolBackend.shearEntity(player.getHeldItem(), player, target));
    }

    /** Finds a GT cable when its thin collision boxes leave a gap in the vanilla ray hit. */
    public static int[] findGtCableOnViewRay(EntityPlayer player, World world) {
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        net.minecraft.util.Vec3 look = player.getLookVec();
        if (look == null) return null;
        for (double distance = 0.05D; distance <= 5.0D; distance += 0.05D) {
            int x = (int) Math.floor(eye.xCoord + look.xCoord * distance);
            int y = (int) Math.floor(eye.yCoord + look.yCoord * distance);
            int z = (int) Math.floor(eye.zCoord + look.zCoord * distance);
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
            if (!(tile instanceof IGregTechTileEntity gt)) continue;
            IMetaTileEntity meta = gt.getMetaTileEntity();
            if (meta == null || !meta.getClass()
                .getName()
                .toLowerCase()
                .contains("cable")) continue;
            ForgeDirection face = Math.abs(look.xCoord) >= Math.abs(look.yCoord)
                && Math.abs(look.xCoord) >= Math.abs(look.zCoord)
                    ? (look.xCoord > 0 ? ForgeDirection.WEST : ForgeDirection.EAST)
                    : Math.abs(look.yCoord) >= Math.abs(look.zCoord)
                        ? (look.yCoord > 0 ? ForgeDirection.DOWN : ForgeDirection.UP)
                        : (look.zCoord > 0 ? ForgeDirection.NORTH : ForgeDirection.SOUTH);
            double px = eye.xCoord + look.xCoord * distance;
            double py = eye.yCoord + look.yCoord * distance;
            double pz = eye.zCoord + look.zCoord * distance;
            return new int[] { x, y, z, face.ordinal(), (int) ((px - x) * 1000), (int) ((py - y) * 1000),
                (int) ((pz - z) * 1000) };
        }
        return null;
    }

    static boolean handleGtWrench(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection clickedSide, float hitX, float hitY, float hitZ) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        ItemUniversalWand wand = stack.getItem() instanceof ItemUniversalWand ? (ItemUniversalWand) stack.getItem()
            : null;
        if (wand != null && isEnderIOTarget(world, x, y, z)
            && player.isSneaking()
            && wand.canUse(stack, player, x, y, z)) {
            wand.used(stack, player, x, y, z);
            return true;
        }
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            if (meta != null && meta.onWrenchRightClick(
                clickedSide,
                GTUtility.determineWrenchingSide(clickedSide, hitX, hitY, hitZ),
                player,
                hitX,
                hitY,
                hitZ,
                stack)) return true;
        }
        if (player.isSneaking()) return false;
        Block block = world.getBlock(x, y, z);
        return block != null && block.rotateBlock(world, x, y, z, clickedSide);
    }

    static boolean isEnderIOTarget(World world, int x, int y, int z) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        Block block = world.getBlock(x, y, z);
        String tileName = tile == null ? ""
            : tile.getClass()
                .getName()
                .toLowerCase();
        String blockName = block == null ? ""
            : block.getClass()
                .getName()
                .toLowerCase();
        return tileName.contains("enderio") || tileName.contains("crazypants")
            || blockName.contains("enderio")
            || blockName.contains("crazypants");
    }

    static boolean handleGtWireCutter(ItemStack stack, EntityPlayer player, World world, int x, int y, int z,
        ForgeDirection clickedSide, float hitX, float hitY, float hitZ) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        ItemUniversalWand wand = stack.getItem() instanceof ItemUniversalWand ? (ItemUniversalWand) stack.getItem()
            : null;
        if (wand != null && isEnderIOTarget(world, x, y, z) && wand.canUse(stack, player, x, y, z)) {
            // Let Ender IO receive the original activation. Its conduit code
            // performs the component/connection-point selection and calls ITool
            // itself; invoking our no-op callback here would swallow the click.
            return false;
        }
        if (tile instanceof IGregTechTileEntity gtTile) {
            IMetaTileEntity meta = gtTile.getMetaTileEntity();
            ForgeDirection cutterSide = GTUtility.determineWrenchingSide(clickedSide, hitX, hitY, hitZ);
            if (meta != null && meta.onWireCutterRightClick(clickedSide, cutterSide, player, hitX, hitY, hitZ, stack))
                return true;
        }
        if (!(tile instanceof IPartHost) && !(tile instanceof AEBaseTile)) return false;
        if ((tile instanceof TileDrive || tile instanceof TileChest) && !player.isSneaking()) return false;
        ForgeDirection renameSide = clickedSide;
        if (tile instanceof IPartHost partHost) {
            SelectedPart part = partHost.selectPart(net.minecraft.util.Vec3.createVectorHelper(hitX, hitY, hitZ));
            if (part == null) return false;
            renameSide = part.side;
        }
        Platform.openGUI(player, tile, renameSide, GuiBridge.GUI_RENAMER);
        return true;
    }

    /** AE2 invokes this before activating a block or part. */
    @Override
    public boolean canWrench(ItemStack wrench, EntityPlayer player, int x, int y, int z) {
        return wrench != null && wrench.getItem() == this
            && getGtToolMode(wrench) == GtToolMode.WRENCH
            && player != null;
    }

    /** BuildCraft invokes this before pipe or machine wrenching. */
    @Override
    public boolean canWrench(EntityPlayer player, int x, int y, int z) {
        return player != null && canWrench(player.getHeldItem(), player, x, y, z);
    }

    @Override
    public void wrenchUsed(EntityPlayer player, int x, int y, int z) {
        com.tenkeitech.universalwand.UniversalWandRightClick.handled(player, true);
    }

    /** Ender IO conduit and machine integration. */
    @Override
    public boolean canUse(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        return stack != null && player != null
            && stack == player.getHeldItem()
            && stack.getItem() == this
            && getGtToolMode(stack) != GtToolMode.OFF
            && getGtToolMode(player.getHeldItem()) != GtToolMode.OFF;
    }

    @Override
    public void used(ItemStack stack, EntityPlayer player, int x, int y, int z) {
        com.tenkeitech.universalwand.UniversalWandRightClick.handled(player, true);
    }

    @Override
    public boolean shouldHideFacades(ItemStack stack, EntityPlayer player) {
        return stack != null && stack.getItem() == this && getGtToolMode(stack) != GtToolMode.OFF;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && sanitizeDisabledFeatures(stack) && entity instanceof EntityPlayer player)
            player.inventory.markDirty();
        if (!world.isRemote && held && entity instanceof EntityPlayerMP player)
            UniversalWandStorage.validateHeldBinding(player, stack);
        if (world.isRemote && held) {
            GtToolMode mode = getGtToolMode(stack);
            if ((mode == GtToolMode.WRENCH && GregTechAPI.sWrenchList != null
                && !GTUtility.isStackInList(stack, GregTechAPI.sWrenchList))
                || (mode == GtToolMode.WIRE_CUTTER && GregTechAPI.sWireCutterList != null
                    && !GTUtility.isStackInList(stack, GregTechAPI.sWireCutterList))) {
                updateGtRightClickRegistration(stack, mode);
            }
        }
        if (!world.isRemote && (!hasEnchantment(stack, Enchantment.looting.effectId) || !hasEnchantment(
            stack,
            getEnchantMode(stack) == EnchantMode.FORTUNE ? Enchantment.fortune.effectId
                : Enchantment.silkTouch.effectId))) {
            applyEnchantMode(stack, getEnchantMode(stack));
        }
    }

    @Override
    public boolean isItemTool(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return true;
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass) {
        return isProtectionMode(stack) ? 0 : Integer.MAX_VALUE;
    }

    @Override
    public float getDigSpeed(ItemStack stack, Block block, int metadata) {
        return isProtectionMode(stack) ? 0F : Integer.MAX_VALUE;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z,
        EntityLivingBase entityLiving) {
        if (world.isRemote && getBreakCooldown(stack) > 0) {
            ((PlayerControllerMPAccessor) Minecraft.getMinecraft().playerController)
                .tenkeitech$setBlockHitDelay(getBreakCooldown(stack));
        }
        return true;
    }

    @Override
    public Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
        ItemStack stack) {
        Multimap<String, net.minecraft.entity.ai.attributes.AttributeModifier> modifiers = super.getAttributeModifiers(
            stack);
        modifiers.put(
            SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
            new net.minecraft.entity.ai.attributes.AttributeModifier(
                ATTACK_MODIFIER_ID,
                ATTACK_MODIFIER_NAME,
                ATTACK_DAMAGE,
                0).setSaved(false));
        return modifiers;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tenkeitech.item.universal_wand.lore_1"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("tenkeitech.item.universal_wand.lore_2"));
        tooltip.add("");
        tooltip.add(EnumChatFormatting.DARK_GRAY + "---- Tools ----");
        addStateTooltip(tooltip, "protection", isProtectionMode(stack), EnumChatFormatting.BLUE);
        tooltip.add(EnumChatFormatting.DARK_GRAY + "---- Mining ----");
        addStateTooltip(tooltip, "enhanced_chain", isEnhancedChain(stack), EnumChatFormatting.GREEN);
        addStateTooltip(tooltip, "gt_small_chain", isGtSmallChainEnabled(stack), EnumChatFormatting.GREEN);
        addStateTooltip(tooltip, "crop_harvest", isCropHarvestEnabled(stack), EnumChatFormatting.GREEN);
        addStateTooltip(tooltip, "crop_sticks", isCropSticksEnabled(stack), EnumChatFormatting.GREEN);
        addStateTooltip(tooltip, "force_bedrock", isForceBedrock(stack), EnumChatFormatting.RED);
        addStateTooltip(tooltip, "collect_items", isInventoryCollection(stack), EnumChatFormatting.AQUA);
        addStateTooltip(tooltip, "building", isBuildingEnabled(stack), EnumChatFormatting.YELLOW);
        tooltip.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(
                "tenkeitech.item.universal_wand.gt_mode",
                StatCollector
                    .translateToLocal("tenkeitech.item.universal_wand.gt_mode." + getGtToolMode(stack).name())));
        if (!UniversalWandFeature.GT_TOOLS.allowed()) tooltip.add(disabledFeatureTooltip());
        tooltip.add(EnumChatFormatting.DARK_GRAY + "---- Combat ----");
        addStateTooltip(tooltip, "force_kill", isForceKillEnabled(stack), EnumChatFormatting.RED);
        addStateTooltip(tooltip, "beheading", isBeheadingEnabled(stack), EnumChatFormatting.RED);
        addStateTooltip(tooltip, "spawn_egg", isSpawnEggEnabled(stack), EnumChatFormatting.GREEN);
        addStateTooltip(tooltip, "aoe", isAoeDamageEnabled(stack), EnumChatFormatting.RED);
        tooltip.add(EnumChatFormatting.DARK_GRAY + "---- Auxiliary ----");
        addStateTooltip(
            tooltip,
            "player_protection",
            isPlayerProtectionEnabled(stack),
            EnumChatFormatting.LIGHT_PURPLE);
        addStateTooltip(tooltip, "no_aggro", isNoAggroEnabled(stack), EnumChatFormatting.LIGHT_PURPLE);
        addStateTooltip(tooltip, "teleport", isTeleportEnabled(stack), EnumChatFormatting.LIGHT_PURPLE);
        addStateTooltip(tooltip, "time_acceleration", isTimeAccelerationEnabled(stack), EnumChatFormatting.AQUA);
        tooltip.add(EnumChatFormatting.DARK_GRAY + "---- Vanilla ----");
        addStateTooltip(tooltip, "shears", isShearsEnabled(stack), EnumChatFormatting.YELLOW);
        addStateTooltip(tooltip, "hoe", isHoeEnabled(stack), EnumChatFormatting.YELLOW);
    }

    private static void addStateTooltip(List<String> tooltip, String key, boolean enabled, EnumChatFormatting color) {
        tooltip.add(
            color + StatCollector.translateToLocal("tenkeitech.gui.universal_wand." + key)
                + EnumChatFormatting.GRAY
                + ": "
                + (enabled ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                + StatCollector
                    .translateToLocal("tenkeitech.item.universal_wand." + (enabled ? "enabled" : "disabled")));
        UniversalWandFeature feature = UniversalWandFeature.forControl(key);
        if (feature != null && !feature.allowed()) tooltip.add(disabledFeatureTooltip());
    }

    public static String disabledFeatureTooltip() {
        return EnumChatFormatting.RED + StatCollector.translateToLocal("tenkeitech.gui.universal_wand.config_disabled");
    }

    @Override
    public ModularScreen createScreen(GuiData data, ModularPanel panel) {
        return new ModularScreen(TenkeiTech.MOD_ID, panel);
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        ItemStack expected = data instanceof com.cleanroommc.modularui.factory.PlayerInventoryGuiData inventoryData
            ? inventoryData.getUsedItemStack()
            : data.getPlayer()
                .getHeldItem();
        settings.canInteractWith(
            player -> expected != null && player.getHeldItem() == expected
                && expected.getItem() instanceof ItemUniversalWand);
        settings.getRecipeViewerSettings()
            .disable();
        return UniversalWandGui.build(data, syncManager);
    }

    public static EnchantMode getEnchantMode(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        if (settings.getInteger(NBT_MODE) == EnchantMode.FORTUNE.ordinal()) return EnchantMode.FORTUNE;
        return EnchantMode.SILK;
    }

    public static boolean setEnchantMode(EntityPlayer player, ItemStack expectedStack, EnchantMode mode) {
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(NBT_MODE, mode.ordinal());
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        applyEnchantMode(expectedStack, mode);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static int getBreakCooldown(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        return Math.max(0, Math.min(20, settings.getInteger(NBT_BREAK_COOLDOWN)));
    }

    public static boolean forceBedrock(EntityPlayerMP player, int targetX, int targetY, int targetZ) {
        if (player == null || player.isDead || player.worldObj.isRemote) return false;
        ItemStack stack = player.getHeldItem();
        if (!(stack != null && stack.getItem() instanceof ItemUniversalWand) || !isForceBedrock(stack)
            || isProtectionMode(stack)) return false;
        int x = targetX;
        int y = targetY;
        int z = targetZ;
        if (!player.worldObj.blockExists(x, y, z) || !UniversalWandActions.isTarget(player, x, y, z)) return false;
        if (player.worldObj.getBlock(x, y, z) != Blocks.bedrock || !player.canPlayerEdit(x, y, z, 1, stack))
            return false;
        if (net.minecraft.server.MinecraftServer.getServer()
            .isBlockProtected((net.minecraft.world.WorldServer) player.worldObj, x, y, z, player)) return false;
        BlockEvent.BreakEvent event;
        try (UniversalWandMiningHandler.ForcedBreakScope ignored = UniversalWandMiningHandler.beginForcedBreak()) {
            event = ForgeHooks
                .onBlockBreakEvent(player.worldObj, player.theItemInWorldManager.getGameType(), player, x, y, z);
        }
        if (event.isCanceled() || player.getHeldItem() != stack
            || isProtectionMode(stack)
            || !isForceBedrock(stack)
            || player.worldObj.getBlock(x, y, z) != Blocks.bedrock) return false;
        if (!player.worldObj.setBlockToAir(x, y, z) || player.worldObj.getBlock(x, y, z) == Blocks.bedrock)
            return false;
        if (!player.capabilities.isCreativeMode) {
            UniversalWandMiningHandler.distributeBedrock(player, stack, x, y, z);
        }
        applyMiningExhaustion(player);
        return true;
    }

    public static boolean isProtectionMode(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_PROTECTION);
    }

    public static boolean isForceKillEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_FORCE_KILL);
    }

    public static boolean isBeheadingEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_BEHEADING);
    }

    public static boolean isSpawnEggEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_SPAWN_EGG);
    }

    public static boolean isTeleportEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_TELEPORT);
    }

    public static boolean isTimeAccelerationEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_TIME_ACCELERATION);
    }

    public static int getAccelerationRate(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        int rate = settings.hasKey(NBT_ACCELERATION_RATE) ? settings.getInteger(NBT_ACCELERATION_RATE) : 2;
        if (rate == 0) return 0;
        for (int allowed : Config.universalWandAccelerationRates) if (allowed == rate) return rate;
        return Config.universalWandAccelerationRates[0];
    }

    public static int getAccelerationMode(ItemStack stack) {
        return Math.max(0, Math.min(2, getSettings(stack).getInteger(NBT_ACCELERATION_MODE)));
    }

    public static boolean isAccelerationGtMode(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        return !settings.hasKey(NBT_ACCELERATION_GT_MODE) || settings.getBoolean(NBT_ACCELERATION_GT_MODE);
    }

    public static boolean setAccelerationGtMode(EntityPlayer player, ItemStack expected, boolean gtMode) {
        return setBooleanSetting(player, expected, NBT_ACCELERATION_GT_MODE, gtMode);
    }

    public static boolean setAccelerationRate(EntityPlayer player, ItemStack expected, int rate) {
        if (rate != 0 && java.util.Arrays.stream(Config.universalWandAccelerationRates)
            .noneMatch(value -> value == rate)) return false;
        return setAccelerationSetting(player, expected, NBT_ACCELERATION_RATE, rate);
    }

    public static boolean setAccelerationMode(EntityPlayer player, ItemStack expected, int mode) {
        return mode >= 0 && mode <= 2 && setAccelerationSetting(player, expected, NBT_ACCELERATION_MODE, mode);
    }

    private static boolean setAccelerationSetting(EntityPlayer player, ItemStack expected, String key, int value) {
        if (!UniversalWandFeature.ACCELERATION.allowed()) return false;
        if (player == null || player.worldObj.isRemote
            || expected == null
            || player.getHeldItem() != expected
            || !(expected.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expected.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expected);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(key, value);
        root.setTag(NBT_SETTINGS, settings);
        expected.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP mp) mp.sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setTimeAccelerationEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        if (!setBooleanSetting(player, expected, NBT_TIME_ACCELERATION, enabled)) return false;
        if (!enabled && !player.worldObj.isRemote)
            com.tenkeitech.universalwand.UniversalWandAccelerationHandler.cancel(player.getUniqueID());
        return true;
    }

    public static boolean isPlayerProtectionEnabled(ItemStack stack) {
        if (!UniversalWandFeature.PLAYER_PROTECTION.allowed()) return false;
        NBTTagCompound settings = readProtectionSettings(stack);
        return !settings.hasKey(NBT_PLAYER_PROTECTION) || settings.getBoolean(NBT_PLAYER_PROTECTION);
    }

    public static boolean isNoAggroEnabled(ItemStack stack) {
        return UniversalWandFeature.NO_AGGRO.allowed() && readProtectionSettings(stack).getBoolean(NBT_NO_AGGRO);
    }

    private static NBTTagCompound readProtectionSettings(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) return new NBTTagCompound();
        NBTTagCompound settings = stack.getTagCompound()
            .getCompoundTag(NBT_SETTINGS);
        return settings.getInteger(NBT_SCHEMA) == SETTINGS_SCHEMA ? settings : new NBTTagCompound();
    }

    public static boolean setPlayerProtectionEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_PLAYER_PROTECTION, enabled);
    }

    public static boolean setNoAggroEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_NO_AGGRO, enabled);
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public boolean isActive(EntityPlayer player, ItemStack stack) {
        return player != null && stack != null && stack.getItem() == this && isTeleportEnabled(stack);
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public int canExtractInternal(ItemStack stack, int power) {
        return Math.max(0, power);
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public void extractInternal(ItemStack stack, int power) {}

    @Override
    @Optional.Method(modid = "EnderIO")
    public int receiveEnergy(ItemStack stack, int amount, boolean simulate) {
        return 0;
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public int extractEnergy(ItemStack stack, int amount, boolean simulate) {
        return 0;
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public int getEnergyStored(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    @Optional.Method(modid = "EnderIO")
    public int getMaxEnergyStored(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    public static boolean setTeleportEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setCombatSetting(player, expected, NBT_TELEPORT, enabled);
    }

    public static boolean setBeheadingEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setCombatSetting(player, expected, NBT_BEHEADING, enabled);
    }

    public static boolean setSpawnEggEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setCombatSetting(player, expected, NBT_SPAWN_EGG, enabled);
    }

    public static boolean isAoeDamageEnabled(ItemStack stack) {
        return getAoeSize(stack) > 1;
    }

    public static int getAoeSize(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        return settings.hasKey(NBT_AOE_SIZE, Constants.NBT.TAG_INT)
            ? normalizeAoeSize(settings.getInteger(NBT_AOE_SIZE))
            : 1;
    }

    private static int normalizeAoeSize(int size) {
        int bounded = Math.max(1, Math.min(MAX_AOE_SIZE, size));
        return bounded % 2 == 0 ? bounded - 1 : bounded;
    }

    public static boolean setAoeSize(EntityPlayer player, ItemStack expected, int size) {
        if (!UniversalWandFeature.AREA_ATTACK.allowed()) return false;
        if (player == null || player.worldObj.isRemote
            || expected == null
            || player.getHeldItem() != expected
            || !(expected.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expected.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expected);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(NBT_AOE_SIZE, normalizeAoeSize(size));
        root.setTag(NBT_SETTINGS, settings);
        expected.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP mp) mp.sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setForceKillEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setCombatSetting(player, expected, NBT_FORCE_KILL, enabled);
    }

    private static boolean setCombatSetting(EntityPlayer player, ItemStack expected, String key, boolean enabled) {
        if (!UniversalWandFeature.canChange(key)) return false;
        if (player == null || player.worldObj.isRemote
            || player.getHeldItem() != expected
            || expected == null
            || !(expected.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expected.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expected);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setBoolean(key, enabled);
        root.setTag(NBT_SETTINGS, settings);
        expected.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP mp) mp.sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean isForceBedrock(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_FORCE_BEDROCK);
    }

    public static boolean isEnhancedChain(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_ENHANCED_CHAIN);
    }

    public static boolean isGtSmallChainEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_GT_SMALL_CHAIN);
    }

    public static boolean isAePriority(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_AE_PRIORITY);
    }

    public static boolean isInventoryCollection(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_INVENTORY_COLLECTION);
    }

    public static boolean isShearsEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_SHEARS);
    }

    public static boolean isHoeEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_HOE);
    }

    public static boolean isCropHarvestEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean("cropHarvest");
    }

    public static boolean setCropHarvestEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "cropHarvest", enabled);
    }

    public static boolean isCropSticksEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean("cropSticks");
    }

    public static boolean setCropSticksEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "cropSticks", enabled);
    }

    public static boolean isBuildingEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_BUILDING);
    }

    public static boolean setBuildingEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_BUILDING, enabled);
    }

    public static boolean isBuildingAeEnabled(ItemStack stack) {
        return getSettings(stack).getBoolean(NBT_BUILDING_AE);
    }

    public static boolean setBuildingAeEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_BUILDING_AE, enabled);
    }

    public static BuildingCore getBuildingCore(ItemStack stack) {
        int core = getSettings(stack).getInteger(NBT_BUILDING_CORE);
        return core >= 0 && core < BuildingCore.values().length ? BuildingCore.values()[core] : BuildingCore.NORMAL;
    }

    public static boolean setBuildingCore(EntityPlayer player, ItemStack expected, BuildingCore core) {
        if (!UniversalWandFeature.BUILDING.allowed()) return false;
        if (player == null || player.worldObj.isRemote
            || expected == null
            || core == null
            || player.getHeldItem() != expected
            || !(expected.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expected.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expected);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(NBT_BUILDING_CORE, core.ordinal());
        root.setTag(NBT_SETTINGS, settings);
        expected.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP mp) mp.sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setShearsEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_SHEARS, enabled);
    }

    public static boolean setHoeEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, NBT_HOE, enabled);
    }

    public static boolean setAePriority(EntityPlayer player, ItemStack expected, boolean enabled) {
        if (!setBooleanSetting(player, expected, NBT_AE_PRIORITY, enabled)) return false;
        if (enabled) setBooleanSetting(player, expected, NBT_INVENTORY_COLLECTION, true);
        return true;
    }

    public static boolean setInventoryCollection(EntityPlayer player, ItemStack expected, boolean enabled) {
        if (!enabled && isAePriority(expected)) setBooleanSetting(player, expected, NBT_AE_PRIORITY, false);
        return setBooleanSetting(player, expected, NBT_INVENTORY_COLLECTION, enabled);
    }

    public static NBTTagCompound getAeBinding(ItemStack stack) {
        return (NBTTagCompound) getSettings(stack).getCompoundTag(NBT_AE_BINDING)
            .copy();
    }

    public static boolean setAeBinding(EntityPlayer player, ItemStack expected, NBTTagCompound binding) {
        if (player == null || player.worldObj.isRemote
            || player.getHeldItem() != expected
            || expected == null
            || !(expected.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expected.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expected);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        if (binding == null) settings.removeTag(NBT_AE_BINDING);
        else settings.setTag(NBT_AE_BINDING, binding.copy());
        root.setTag(NBT_SETTINGS, settings);
        expected.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP mp) mp.sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setGtSmallChainEnabled(EntityPlayer player, ItemStack expectedStack, boolean enabled) {
        return setBooleanSetting(player, expectedStack, NBT_GT_SMALL_CHAIN, enabled);
    }

    public static int getEnhancedChainRadius(ItemStack stack) {
        int value = getSettings(stack).getInteger(NBT_ENHANCED_CHAIN_RADIUS);
        return value <= 0 ? DEFAULT_ENHANCED_CHAIN_RADIUS : Math.max(1, Math.min(MAX_ENHANCED_CHAIN_RADIUS, value));
    }

    public static int getMaxMiningCount(ItemStack stack) {
        int value = getSettings(stack).getInteger(NBT_MAX_MINING_COUNT);
        return value <= 0 ? DEFAULT_MAX_MINING_COUNT : value;
    }

    public static void applyMiningExhaustion(EntityPlayer player) {
        if (!player.worldObj.isRemote && !player.capabilities.isCreativeMode) {
            player.addExhaustion((float) Config.universalWandMiningExhaustion);
        }
    }

    public static boolean setEnhancedChainRadius(EntityPlayer player, ItemStack expectedStack, int radius) {
        return setIntegerSetting(
            player,
            expectedStack,
            NBT_ENHANCED_CHAIN_RADIUS,
            Math.max(1, Math.min(MAX_ENHANCED_CHAIN_RADIUS, radius)));
    }

    public static boolean setMaxMiningCount(EntityPlayer player, ItemStack expectedStack, int count) {
        return setIntegerSetting(
            player,
            expectedStack,
            NBT_MAX_MINING_COUNT,
            Math.max(1, Math.min(Integer.MAX_VALUE, count)));
    }

    private static boolean setIntegerSetting(EntityPlayer player, ItemStack expectedStack, String key, int value) {
        if (!UniversalWandFeature.canChange(key)) return false;
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(key, value);
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static GtToolMode getGtToolMode(ItemStack stack) {
        NBTTagCompound settings = getSettings(stack);
        int value = settings.getInteger(NBT_TOOL_MODE);
        if (value < 0 || value >= GtToolMode.values().length) {
            return GtToolMode.OFF;
        }
        return GtToolMode.values()[value];
    }

    public static boolean isWireCutterMode(ItemStack stack) {
        return getGtToolMode(stack) == GtToolMode.WIRE_CUTTER;
    }

    public static boolean setGtToolMode(EntityPlayer player, ItemStack expectedStack, GtToolMode mode) {
        if (!UniversalWandFeature.GT_TOOLS.allowed()) return false;
        if (player == null || expectedStack == null
            || mode == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(NBT_TOOL_MODE, mode.ordinal());
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        updateGtRightClickRegistration(expectedStack, mode);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    private static void updateGtRightClickRegistration(ItemStack stack, GtToolMode mode) {
        removeGtRegistrations(GregTechAPI.sWrenchList, stack.getItem());
        removeGtRegistrations(GregTechAPI.sWireCutterList, stack.getItem());
        ItemStack registration = stack.copy();
        if (mode == GtToolMode.WRENCH) GregTechAPI.registerWrench(registration);
        if (mode == GtToolMode.WIRE_CUTTER) GregTechAPI.registerWireCutter(registration);
    }

    private static void removeGtRegistrations(java.util.Collection<GTItemStack> list, Item item) {
        if (list == null) return;
        list.removeIf(entry -> entry != null && entry.mItem == item);
    }

    public static boolean setProtectionMode(EntityPlayer player, ItemStack expectedStack, boolean enabled) {
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setBoolean(NBT_PROTECTION, enabled);
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setForceBedrock(EntityPlayer player, ItemStack expectedStack, boolean enabled) {
        if (!UniversalWandFeature.BEDROCK.allowed()) return false;
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setBoolean(NBT_FORCE_BEDROCK, enabled);
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean isCircuitPreserverEnabled(ItemStack stack) {
        return readTenkeiSetting(stack, "preserveProgrammedCircuits");
    }

    public static boolean isCatalystRecorderEnabled(ItemStack stack) {
        return readTenkeiSetting(stack, "recordCatalysts");
    }

    public static boolean isCondensateReplacementEnabled(ItemStack stack) {
        return readTenkeiSetting(stack, "replaceCondensateWithSource");
    }

    public static boolean isCutterFluidFilterEnabled(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemUniversalWand
            && !ItemPatternCatalystMarker.isMarker(stack)
            && getSettings(stack).getBoolean("filterCutterFluids");
    }

    public static boolean setCutterFluidFilterEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "filterCutterFluids", enabled);
    }

    public static boolean setCircuitPreserverEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "preserveProgrammedCircuits", enabled);
    }

    public static boolean setCatalystRecorderEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "recordCatalysts", enabled);
    }

    public static boolean setCondensateReplacementEnabled(EntityPlayer player, ItemStack expected, boolean enabled) {
        return setBooleanSetting(player, expected, "replaceCondensateWithSource", enabled);
    }

    private static boolean readTenkeiSetting(ItemStack stack, String key) {
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)
            || ItemPatternCatalystMarker.isMarker(stack)) return false;
        NBTTagCompound settings = getSettings(stack);
        if (settings.hasKey(key)) return settings.getBoolean(key);
        return true;
    }

    public static boolean setEnhancedChain(EntityPlayer player, ItemStack expectedStack, boolean enabled) {
        return setBooleanSetting(player, expectedStack, NBT_ENHANCED_CHAIN, enabled);
    }

    private static boolean setBooleanSetting(EntityPlayer player, ItemStack expectedStack, String key,
        boolean enabled) {
        if (!UniversalWandFeature.canChange(key)) return false;
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setBoolean(key, enabled);
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    public static boolean setBreakCooldown(EntityPlayer player, ItemStack expectedStack, int cooldown) {
        if (player == null || expectedStack == null
            || player.getHeldItem() != expectedStack
            || !(expectedStack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound root = expectedStack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        NBTTagCompound settings = getSettings(expectedStack);
        settings.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        settings.setInteger(NBT_BREAK_COOLDOWN, Math.max(0, Math.min(20, cooldown)));
        root.setTag(NBT_SETTINGS, settings);
        expectedStack.setTagCompound(root);
        player.inventory.markDirty();
        if (player instanceof EntityPlayerMP)
            ((EntityPlayerMP) player).sendContainerToPlayer(player.inventoryContainer);
        return true;
    }

    private static void ensureEnchantMode(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)) return;
        applyEnchantMode(stack, getEnchantMode(stack));
    }

    private static void applyEnchantMode(ItemStack stack, EnchantMode mode) {
        NBTTagList enchantments = stack.getEnchantmentTagList();
        if (enchantments != null) {
            for (int index = enchantments.tagCount() - 1; index >= 0; index--) {
                int id = enchantments.getCompoundTagAt(index)
                    .getShort("id");
                if (id == Enchantment.silkTouch.effectId || id == Enchantment.fortune.effectId)
                    enchantments.removeTag(index);
            }
        }
        stack.addEnchantment(mode == EnchantMode.FORTUNE ? Enchantment.fortune : Enchantment.silkTouch, 10);
        if (!hasEnchantment(stack, Enchantment.looting.effectId)) stack.addEnchantment(Enchantment.looting, 10);
    }

    private static boolean hasEnchantment(ItemStack stack, int id) {
        NBTTagList enchantments = stack.getEnchantmentTagList();
        if (enchantments == null) return false;
        for (int index = 0; index < enchantments.tagCount(); index++) {
            if (enchantments.getCompoundTagAt(index)
                .getShort("id") == id) return true;
        }
        return false;
    }

    private static NBTTagCompound getSettings(ItemStack stack) {
        NBTTagCompound settings = readProtectionSettings(stack);
        settings = (NBTTagCompound) settings.copy();
        UniversalWandFeature.restrict(settings);
        return settings;
    }

    /** Persist current server restrictions when tools enter a player's inventory or are loaded. */
    public static boolean sanitizeDisabledFeatures(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemUniversalWand)) return false;
        NBTTagCompound previous = readProtectionSettings(stack);
        NBTTagCompound restricted = getSettings(stack);
        if (previous.equals(restricted)) return false;
        restricted.setInteger(NBT_SCHEMA, SETTINGS_SCHEMA);
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) root = new NBTTagCompound();
        root.setTag(NBT_SETTINGS, restricted);
        stack.setTagCompound(root);
        if (!UniversalWandFeature.GT_TOOLS.allowed()) updateGtRightClickRegistration(stack, GtToolMode.OFF);
        return true;
    }
}
