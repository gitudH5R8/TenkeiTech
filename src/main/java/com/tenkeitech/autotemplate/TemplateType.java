/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.autotemplate;

import net.minecraft.item.ItemStack;

import com.tenkeitech.TenkeiTech;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTUtility;

/** Stable machine-template identifiers. IDs are persisted in machine-item NBT and must never be renumbered. */
public enum TemplateType {

    NONE(0, InputKind.NONE, null),

    EXTRUDER_ROD(1, InputKind.INGOT, ItemList.Shape_Extruder_Rod),
    EXTRUDER_PLATE(2, InputKind.INGOT, ItemList.Shape_Extruder_Plate),
    EXTRUDER_BOTTLE(3, InputKind.INGOT, ItemList.Shape_Extruder_Bottle),
    EXTRUDER_CELL(4, InputKind.INGOT, ItemList.Shape_Extruder_Cell),
    EXTRUDER_RING(5, InputKind.INGOT, ItemList.Shape_Extruder_Ring),
    EXTRUDER_BOLT(6, InputKind.INGOT, ItemList.Shape_Extruder_Bolt),
    EXTRUDER_INGOT(7, InputKind.INGOT, ItemList.Shape_Extruder_Ingot),
    EXTRUDER_WIRE(8, InputKind.INGOT, ItemList.Shape_Extruder_Wire),
    EXTRUDER_CASING(9, InputKind.INGOT, ItemList.Shape_Extruder_Casing),
    EXTRUDER_PIPE_TINY(10, InputKind.INGOT, ItemList.Shape_Extruder_Pipe_Tiny),
    EXTRUDER_PIPE_SMALL(11, InputKind.INGOT, ItemList.Shape_Extruder_Pipe_Small),
    EXTRUDER_PIPE_MEDIUM(12, InputKind.INGOT, ItemList.Shape_Extruder_Pipe_Medium),
    EXTRUDER_PIPE_LARGE(13, InputKind.INGOT, ItemList.Shape_Extruder_Pipe_Large),
    EXTRUDER_PIPE_HUGE(14, InputKind.INGOT, ItemList.Shape_Extruder_Pipe_Huge),
    EXTRUDER_BLOCK(15, InputKind.INGOT, ItemList.Shape_Extruder_Block),
    EXTRUDER_SWORD(16, InputKind.INGOT, ItemList.Shape_Extruder_Sword),
    EXTRUDER_PICKAXE(17, InputKind.INGOT, ItemList.Shape_Extruder_Pickaxe),
    EXTRUDER_SHOVEL(18, InputKind.INGOT, ItemList.Shape_Extruder_Shovel),
    EXTRUDER_AXE(19, InputKind.INGOT, ItemList.Shape_Extruder_Axe),
    EXTRUDER_HOE(20, InputKind.INGOT, ItemList.Shape_Extruder_Hoe),
    EXTRUDER_HAMMER(21, InputKind.INGOT, ItemList.Shape_Extruder_Hammer),
    EXTRUDER_FILE(22, InputKind.INGOT, ItemList.Shape_Extruder_File),
    EXTRUDER_SAW(23, InputKind.INGOT, ItemList.Shape_Extruder_Saw),
    EXTRUDER_GEAR(24, InputKind.INGOT, ItemList.Shape_Extruder_Gear),
    EXTRUDER_ROTOR(25, InputKind.INGOT, ItemList.Shape_Extruder_Rotor),
    EXTRUDER_TURBINE_BLADE(26, InputKind.INGOT, ItemList.Shape_Extruder_Turbine_Blade),
    EXTRUDER_SMALL_GEAR(27, InputKind.INGOT, ItemList.Shape_Extruder_Small_Gear),
    EXTRUDER_TOOL_HEAD_DRILL(28, InputKind.INGOT, ItemList.Shape_Extruder_ToolHeadDrill),

    SOLIDIFIER_INGOT(32, InputKind.FLUID, ItemList.Shape_Mold_Ingot),
    SOLIDIFIER_BOTTLE(33, InputKind.FLUID, ItemList.Shape_Mold_Bottle),
    SOLIDIFIER_PLATE(34, InputKind.FLUID, ItemList.Shape_Mold_Plate),
    SOLIDIFIER_CASING(35, InputKind.FLUID, ItemList.Shape_Mold_Casing),
    SOLIDIFIER_GEAR(36, InputKind.FLUID, ItemList.Shape_Mold_Gear),
    SOLIDIFIER_SMALL_GEAR(37, InputKind.FLUID, ItemList.Shape_Mold_Gear_Small),
    SOLIDIFIER_CREDIT(38, InputKind.FLUID, ItemList.Shape_Mold_Credit),
    SOLIDIFIER_NUGGET(39, InputKind.FLUID, ItemList.Shape_Mold_Nugget),
    SOLIDIFIER_BLOCK(40, InputKind.FLUID, ItemList.Shape_Mold_Block),
    SOLIDIFIER_BALL(41, InputKind.FLUID, ItemList.Shape_Mold_Ball),
    SOLIDIFIER_BUN(42, InputKind.FLUID, ItemList.Shape_Mold_Bun),
    SOLIDIFIER_BREAD(43, InputKind.FLUID, ItemList.Shape_Mold_Bread),
    SOLIDIFIER_BAGUETTE(44, InputKind.FLUID, ItemList.Shape_Mold_Baguette),
    SOLIDIFIER_CYLINDER(45, InputKind.FLUID, ItemList.Shape_Mold_Cylinder),
    SOLIDIFIER_ANVIL(46, InputKind.FLUID, ItemList.Shape_Mold_Anvil),
    SOLIDIFIER_ARROW(47, InputKind.FLUID, ItemList.Shape_Mold_Arrow),
    SOLIDIFIER_NAME(48, InputKind.FLUID, ItemList.Shape_Mold_Name),
    SOLIDIFIER_ROD(49, InputKind.FLUID, ItemList.Shape_Mold_Rod),
    SOLIDIFIER_BOLT(50, InputKind.FLUID, ItemList.Shape_Mold_Bolt),
    SOLIDIFIER_ROUND(51, InputKind.FLUID, ItemList.Shape_Mold_Round),
    SOLIDIFIER_SCREW(52, InputKind.FLUID, ItemList.Shape_Mold_Screw),
    SOLIDIFIER_RING(53, InputKind.FLUID, ItemList.Shape_Mold_Ring),
    SOLIDIFIER_ROD_LONG(54, InputKind.FLUID, ItemList.Shape_Mold_Rod_Long),
    SOLIDIFIER_ROTOR(55, InputKind.FLUID, ItemList.Shape_Mold_Rotor),
    SOLIDIFIER_TURBINE_BLADE(56, InputKind.FLUID, ItemList.Shape_Mold_Turbine_Blade),
    SOLIDIFIER_PIPE_TINY(57, InputKind.FLUID, ItemList.Shape_Mold_Pipe_Tiny),
    SOLIDIFIER_PIPE_SMALL(58, InputKind.FLUID, ItemList.Shape_Mold_Pipe_Small),
    SOLIDIFIER_PIPE_MEDIUM(59, InputKind.FLUID, ItemList.Shape_Mold_Pipe_Medium),
    SOLIDIFIER_PIPE_LARGE(60, InputKind.FLUID, ItemList.Shape_Mold_Pipe_Large),
    SOLIDIFIER_PIPE_HUGE(61, InputKind.FLUID, ItemList.Shape_Mold_Pipe_Huge),
    SOLIDIFIER_TOOL_HEAD_DRILL(62, InputKind.FLUID, ItemList.Shape_Mold_ToolHeadDrill),

    WIREMILL_WIRE_1X(64, Family.WIREMILL, 1, OrePrefixes.wireGt01),
    WIREMILL_WIRE_2X(65, Family.WIREMILL, 2, OrePrefixes.wireGt02),
    WIREMILL_FINE_WIRE(66, Family.WIREMILL, 3, OrePrefixes.wireFine),
    WIREMILL_WIRE_4X(67, Family.WIREMILL, 4, OrePrefixes.wireGt04),
    WIREMILL_WIRE_8X(68, Family.WIREMILL, 8, OrePrefixes.wireGt08),
    WIREMILL_WIRE_12X(69, Family.WIREMILL, 12, OrePrefixes.wireGt12),
    WIREMILL_WIRE_16X(70, Family.WIREMILL, 16, OrePrefixes.wireGt16),

    BENDER_PLATE(80, Family.BENDER, 1, OrePrefixes.plate),
    BENDER_DOUBLE_PLATE(81, Family.BENDER, 2, OrePrefixes.plateDouble),
    BENDER_TRIPLE_PLATE(82, Family.BENDER, 3, OrePrefixes.plateTriple),
    BENDER_QUADRUPLE_PLATE(83, Family.BENDER, 4, OrePrefixes.plateQuadruple),
    BENDER_QUINTUPLE_PLATE(84, Family.BENDER, 5, OrePrefixes.plateQuintuple),
    BENDER_DENSE_PLATE(85, Family.BENDER, 9, OrePrefixes.plateDense),
    BENDER_FOIL(86, Family.BENDER, 10, OrePrefixes.foil);

    public enum Family {
        NONE,
        FIXED_TEMPLATE,
        WIREMILL,
        BENDER
    }

    private static final TemplateType[] WIREMILL_MODES = { WIREMILL_WIRE_1X, WIREMILL_WIRE_2X, WIREMILL_FINE_WIRE,
        WIREMILL_WIRE_4X, WIREMILL_WIRE_8X, WIREMILL_WIRE_12X, WIREMILL_WIRE_16X };
    private static final TemplateType[] BENDER_MODES = { BENDER_PLATE, BENDER_DOUBLE_PLATE, BENDER_TRIPLE_PLATE,
        BENDER_QUADRUPLE_PLATE, BENDER_QUINTUPLE_PLATE, BENDER_DENSE_PLATE, BENDER_FOIL };

    private final int stableId;
    private final InputKind inputKind;
    private final ItemList templateItem;
    private final Family family;
    private final int circuitConfiguration;
    private final OrePrefixes expectedOutputPrefix;

    TemplateType(int stableId, InputKind inputKind, ItemList templateItem) {
        this.stableId = stableId;
        this.inputKind = inputKind;
        this.templateItem = templateItem;
        this.family = inputKind == InputKind.NONE ? Family.NONE : Family.FIXED_TEMPLATE;
        this.circuitConfiguration = 0;
        this.expectedOutputPrefix = null;
    }

    TemplateType(int stableId, Family family, int circuitConfiguration, OrePrefixes expectedOutputPrefix) {
        this.stableId = stableId;
        this.inputKind = InputKind.INGOT;
        this.templateItem = null;
        this.family = family;
        this.circuitConfiguration = circuitConfiguration;
        this.expectedOutputPrefix = expectedOutputPrefix;
    }

    static {
        for (TemplateType first : values()) {
            for (TemplateType second : values()) {
                if (first != second && first.stableId == second.stableId) {
                    throw new IllegalStateException("Duplicate auto-template stable ID " + first.stableId);
                }
            }
        }
    }

    public int getStableId() {
        return stableId;
    }

    public InputKind getInputKind() {
        return inputKind;
    }

    public Family getFamily() {
        return family;
    }

    public RecipeMap<?> getRecipeMap() {
        return switch (family) {
            case FIXED_TEMPLATE -> inputKind == InputKind.INGOT ? RecipeMaps.extruderRecipes
                : RecipeMaps.fluidSolidifierRecipes;
            case WIREMILL -> RecipeMaps.wiremillRecipes;
            case BENDER -> RecipeMaps.benderRecipes;
            default -> null;
        };
    }

    public ItemStack getTemplateStack() {
        if (isCircuitMode()) {
            ItemStack stack = GTUtility.getIntegratedCircuit(circuitConfiguration);
            if (stack == null) return null;
            stack = stack.copy();
            stack.stackSize = 0;
            return stack;
        }
        if (templateItem == null || !templateItem.hasBeenSet()) return null;
        ItemStack stack = templateItem.get(0L);
        if (stack == null) return null;
        stack = stack.copy();
        stack.stackSize = 0;
        return stack;
    }

    public boolean isConfigured() {
        return inputKind != InputKind.NONE && getRecipeMap() != null && getTemplateStack() != null;
    }

    public boolean isPhysicalTemplate() {
        return family == Family.FIXED_TEMPLATE;
    }

    public boolean isCircuitMode() {
        return family == Family.WIREMILL || family == Family.BENDER;
    }

    public int getCircuitConfiguration() {
        return circuitConfiguration;
    }

    public OrePrefixes getExpectedOutputPrefix() {
        return expectedOutputPrefix;
    }

    public TemplateType[] getSelectableModes() {
        return switch (family) {
            case WIREMILL -> WIREMILL_MODES.clone();
            case BENDER -> BENDER_MODES.clone();
            default -> new TemplateType[0];
        };
    }

    /** Matches a real held or crafting-grid template while ignoring only its stack size. */
    public boolean matchesTemplate(ItemStack candidate) {
        if (candidate == null || candidate.stackSize <= 0 || this == NONE) return false;
        ItemStack expected = getTemplateStack();
        if (expected == null) return false;
        ItemStack actual = candidate.copy();
        actual.stackSize = 0;
        return GTUtility.areStacksEqual(expected, actual);
    }

    public static TemplateType fromId(int stableId) {
        for (TemplateType type : values()) {
            if (type.stableId == stableId) return type;
        }
        return NONE;
    }

    public static TemplateType fromTemplateStack(ItemStack stack) {
        for (TemplateType type : values()) {
            if (!type.isPhysicalTemplate()) continue;
            try {
                if (type.matchesTemplate(stack)) return type;
            } catch (RuntimeException exception) {
                // An optional GT item can still be unset while a world is loading; it is not a match.
                TenkeiTech.LOG.debug("Skipping unavailable auto-template item {}", type, exception);
            }
        }
        return NONE;
    }
}
