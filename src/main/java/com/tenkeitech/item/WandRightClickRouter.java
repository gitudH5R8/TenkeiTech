/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.item;

import static com.tenkeitech.universalwand.interaction.WandActionResult.*;
import static com.tenkeitech.universalwand.interaction.WandRightClickContext.Phase.*;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand.BuildingCore;
import com.tenkeitech.item.ItemUniversalWand.GtToolMode;
import com.tenkeitech.universalwand.UniversalWandAccelerationHandler;
import com.tenkeitech.universalwand.UniversalWandBuildingHandler;
import com.tenkeitech.universalwand.UniversalWandNetwork;
import com.tenkeitech.universalwand.UniversalWandRightClick;
import com.tenkeitech.universalwand.UniversalWandStorage;
import com.tenkeitech.universalwand.interaction.WandActionResult;
import com.tenkeitech.universalwand.interaction.WandRightClickContext;
import com.tenkeitech.universalwand.interaction.WandRightClickContext.Phase;

import appeng.api.implementations.tiles.IWirelessAccessPoint;
import cpw.mods.fml.common.Loader;

/** Fixed candidates at native phases. Matching is pure; a selected backend is invoked only once. */
public final class WandRightClickRouter {

    public enum Action {

        CANCEL_ACCELERATION(BLOCK_FIRST),
        BIND(BLOCK_FIRST),
        GT_TOOL(BLOCK_FIRST),
        UNDO(BLOCK_FIRST),
        BUILD(BLOCK_FIRST),
        ACCELERATION(BLOCK_FIRST),
        HOE(BLOCK_ITEM),
        SHEARS(ENTITY),
        AIR_BUILD(AIR),
        TELEPORT(COMPLETE);

        final Phase phase;

        Action(Phase phase) {
            this.phase = phase;
        }
    }

    private static final Action[] ACTIONS = Action.values();

    private WandRightClickRouter() {}

    public static WandActionResult route(WandRightClickContext context) {
        return dispatch(context).result;
    }

    public static boolean useFirst(WandRightClickContext context) {
        Decision decision = dispatch(context);
        return decision.result.consumesClick()
            && (!context.world.isRemote || decision.action == Action.BUILD || decision.action == Action.UNDO);
    }

    private static final class Decision {

        final Action action;
        final WandActionResult result;

        Decision(Action action, WandActionResult result) {
            this.action = action;
            this.result = result;
        }
    }

    private static Decision dispatch(WandRightClickContext context) {
        if (context.stack == null || !(context.stack.getItem() instanceof ItemUniversalWand))
            return new Decision(null, PASS);
        if ((context.phase == BLOCK_FIRST || context.phase == BLOCK_ITEM) && !validBlockInput(context)) {
            if (!context.world.isRemote) UniversalWandRightClick.record(context.player, BLOCKED);
            return new Decision(null, BLOCKED);
        }
        if (!context.world.isRemote && context.phase != COMPLETE) {
            WandActionResult previous = UniversalWandRightClick.result(context.player);
            if (previous.consumesClick()) return new Decision(null, previous);
        }
        for (Action action : ACTIONS) {
            if (action.phase != context.phase || !matches(action, context)) continue;
            if (!context.world.isRemote && !UniversalWandRightClick.tryAction(context.player, action.ordinal()))
                continue;
            WandActionResult result;
            try {
                result = execute(action, context);
            } catch (RuntimeException failure) {
                TenkeiTech.LOG.error(
                    "Universal Wand right-click action {} failed for {}",
                    action,
                    context.player.getCommandSenderName(),
                    failure);
                result = BLOCKED;
            }
            if (!context.world.isRemote) {
                UniversalWandRightClick.record(context.player, result);
                result = result.merge(UniversalWandRightClick.result(context.player));
            }
            if (result.consumesClick()) return new Decision(action, result);
        }
        return new Decision(null, PASS);
    }

    private static boolean matches(Action action, WandRightClickContext c) {
        boolean building = c.building;
        boolean acceleration = c.acceleration;
        GtToolMode mode = c.gtToolMode;
        return switch (action) {
            case CANCEL_ACCELERATION -> c.modifier && c.sneaking && acceleration;
            case ACCELERATION -> c.sneaking && acceleration;
            case UNDO -> building && c.modifier;
            case BIND -> c.sneaking && c.world.blockExists(c.x, c.y, c.z)
                && c.world.getTileEntity(c.x, c.y, c.z) instanceof IWirelessAccessPoint;
            case BUILD -> !c.sneaking && building;
            case GT_TOOL -> mode != GtToolMode.OFF;
            case HOE -> ItemUniversalWand.isHoeEnabled(c.stack);
            case SHEARS -> ItemUniversalWand.isShearsEnabled(c.stack);
            case AIR_BUILD -> !c.sneaking && !c.modifier && building && c.buildingCore == BuildingCore.ANGEL;
            case TELEPORT -> ItemUniversalWand.isTeleportEnabled(c.stack) && Loader.isModLoaded("EnderIO");
        };
    }

    private static WandActionResult execute(Action action, WandRightClickContext c) {
        if (action == Action.TELEPORT) {
            if (c.world.isRemote) TenkeiTech.proxy.executeWandClientAction(action.ordinal(), c.stack, c.player);
            else if (c.player instanceof EntityPlayerMP mp)
                UniversalWandNetwork.continueRightClick(mp, c.interactionId, action.ordinal());
            else return BLOCKED;
            return HANDLED;
        }
        if (action == Action.HOE)
            return VanillaToolBackend.useHoe(c.stack, c.player, c.world, c.x, c.y, c.z, c.side, c.hitX, c.hitY, c.hitZ);
        if (action == Action.SHEARS) return nativeResult(VanillaToolBackend.shearEntity(c.stack, c.player, c.entity));
        if (c.world.isRemote) {
            if (action == Action.BUILD || action == Action.UNDO) {
                UniversalWandNetwork.requestBuildStart(
                    c.x,
                    c.y,
                    c.z,
                    c.side,
                    c.hitX,
                    c.hitY,
                    c.hitZ,
                    c.buildingCore.ordinal(),
                    action == Action.UNDO);
                return PENDING;
            }
            // false in client onItemUseFirst preserves the native block-use packet. It is not a server PASS.
            return PENDING;
        }
        if (!(c.player instanceof EntityPlayerMP player)) return BLOCKED;
        return switch (action) {
            case CANCEL_ACCELERATION -> UniversalWandAccelerationHandler.cancel(player, c.x, c.y, c.z);
            case ACCELERATION -> UniversalWandAccelerationHandler.use(player, c.x, c.y, c.z);
            case GT_TOOL -> ItemUniversalWand
                .useGtTool(c.stack, player, c.world, c.x, c.y, c.z, c.side, c.hitX, c.hitY, c.hitZ, c.gtToolMode);
            case UNDO, BUILD -> UniversalWandBuildingHandler.requestStart(
                player,
                c.x,
                c.y,
                c.z,
                c.side,
                c.hitX,
                c.hitY,
                c.hitZ,
                c.buildingCore.ordinal(),
                action == Action.UNDO);
            case BIND -> UniversalWandStorage.toggleBinding(player, c.x, c.y, c.z);
            case AIR_BUILD -> UniversalWandBuildingHandler.requestAir(player);
            default -> PASS;
        };
    }

    /** A server continuation resumes the same checked candidate; there is no separate travel fallback. */
    public static void resume(int actionId, WandRightClickContext context) {
        if (actionId < 0 || actionId >= ACTIONS.length) return;
        Action action = ACTIONS[actionId];
        if (action.phase == COMPLETE && context.phase == COMPLETE && matches(action, context)) execute(action, context);
    }

    /** The custom building packet enters the same policy, never an independent feature shortcut. */
    public static void requestBuilding(WandRightClickContext context, int core, boolean undo) {
        if (context.stack == null || !(context.stack.getItem() instanceof ItemUniversalWand)) return;
        if (!validBlockInput(context)) {
            UniversalWandRightClick.record(context.player, BLOCKED);
            return;
        }
        Action selected = null;
        for (Action action : ACTIONS) {
            if (action.phase == BLOCK_FIRST && matches(action, context)) {
                selected = action;
                break;
            }
        }
        if ((selected != Action.BUILD && selected != Action.UNDO) || undo != (selected == Action.UNDO)
            || core != context.buildingCore.ordinal()) {
            UniversalWandRightClick.record(context.player, BLOCKED);
            return;
        }
        route(context);
    }

    private static boolean validBlockInput(WandRightClickContext c) {
        return c.side >= 0 && c.side < 6
            && Float.isFinite(c.hitX)
            && Float.isFinite(c.hitY)
            && Float.isFinite(c.hitZ)
            && c.hitX >= 0
            && c.hitX <= 1
            && c.hitY >= 0
            && c.hitY <= 1
            && c.hitZ >= 0
            && c.hitZ <= 1
            && c.world.blockExists(c.x, c.y, c.z);
    }
}
