/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.asm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

/** Read-only, fixed bytecode contracts. Does not load optional classes or generate bridge methods. */
public final class QuantumCompat {

    private static final String AE = "appeng/";
    private static final String CRAFT = AE + "api/networking/crafting/";
    private static final String CPU = AE + "me/cluster/implementations/CraftingCPUCluster";
    private static final String PUSH = "(L" + CRAFT
        + "ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z";
    private static final String SUBMIT = "(L" + AE
        + "api/networking/IGrid;L"
        + CRAFT
        + "ICraftingJob;L"
        + AE
        + "api/networking/security/BaseActionSource;L"
        + CRAFT
        + "ICraftingRequester;)L"
        + CRAFT
        + "ICraftingLink;";

    private QuantumCompat() {}

    public static boolean supportsECO() {
        String root = "cn/dancingsnow/neoecoae/";
        String logic = root + "crafting/execution/ECOCraftingCPULogic";
        String executor = root + "crafting/execution/ECOVanillaCraftingExecutor";
        String nativeCpu = root + "platform/ae2/crafting/nativecpu/NativeECOCpu";
        return inspect("ECO", contract -> {
            contract.extendsClass(nativeCpu, CPU);
            contract.method(nativeCpu, "submitJob", SUBMIT, true);
            contract.method(nativeCpu, "getLastCraftingLink", "()L" + CRAFT + "ICraftingLink;", true);
            contract
                .method(nativeCpu, "trackRequester", "(L" + AE + "api/networking/security/BaseActionSource;)V", true);
            contract.method(nativeCpu, "writeTracking", "(Lnet/minecraft/nbt/NBTTagCompound;)V", true);
            contract.method(nativeCpu, "readTracking", "(Lnet/minecraft/nbt/NBTTagCompound;)V", true);
            contract
                .method(nativeCpu, "getActionSource", "()L" + AE + "api/networking/security/BaseActionSource;", true);
            contract.method(logic, "nativeCPU", "()L" + CRAFT + "ICraftingCPU;", true);
            contract.method(
                logic,
                "tickCraftingLogic",
                "(L" + AE + "api/networking/energy/IEnergyGrid;L" + root + "platform/ae2/crafting/ICraftingService;)V",
                true);
            contract.method(
                executor,
                "isFinalPattern",
                "(L" + root + "crafting/execution/ExecutingCraftingJob;L" + CRAFT + "ICraftingPatternDetails;)Z",
                false);
            contract.staticMethod(
                root + "crafting/execution/ECOCraftingDispatchAccounting",
                "isFakeCraftingDispatch",
                "(L" + root
                    + "crafting/execution/ECOCraftingDispatchRequest;L"
                    + root
                    + "platform/ae2/crafting/ICraftingProvider;)Z");
            contract.pushSite(executor, "executeCrafting");
            contract.pushSite(root + "platform/ae2/crafting/NativeCraftingProvider", "pushPattern");
        });
    }

    public static boolean supportsGTNL() {
        String owner = "com/science/gtnl/common/machine/multiblock/QuantumComputer";
        String cpu = "com/science/gtnl/utils/ECraftingCPUCluster";
        return inspect("GTNL", contract -> {
            contract.extendsClass(cpu, CPU);
            contract.method(owner, "forEachCPU", "(Ljava/util/function/Consumer;)V", true);
            contract.method(cpu, "setVirtualCPUOwner", "(L" + owner + ";)V", true);
            contract.method(cpu, "submitJob", SUBMIT, true);
        });
    }

    private static boolean inspect(String name, java.util.function.Consumer<Contract> checks) {
        try {
            checks.accept(new Contract());
            return true;
        } catch (RuntimeException failure) {
            LogManager.getLogger("TenkeiTech")
                .warn("Skipping optional {} quantum CPU support: {}", name, failure.getMessage());
            return false;
        }
    }

    private static final class Contract {

        private final Map<String, ClassNode> classes = new HashMap<>();

        ClassNode node(String owner) {
            return classes.computeIfAbsent(owner, name -> {
                try {
                    byte[] bytes = Launch.classLoader.getClassBytes(name.replace('/', '.'));
                    if (bytes == null) throw new IllegalStateException("missing " + name);
                    ClassNode node = new ClassNode();
                    new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    return node;
                } catch (IOException failure) {
                    throw new IllegalStateException("cannot inspect " + name, failure);
                }
            });
        }

        void extendsClass(String owner, String parent) {
            if (!parent.equals(node(owner).superName)) throw new IllegalStateException("changed superclass: " + owner);
        }

        void method(String owner, String name, String descriptor, boolean publicAccess) {
            for (MethodNode method : node(owner).methods) if (name.equals(method.name) && descriptor.equals(method.desc)
                && (method.access & Opcodes.ACC_STATIC) == 0
                && (!publicAccess || (method.access & Opcodes.ACC_PUBLIC) != 0)) return;
            throw new IllegalStateException("missing method: " + owner + "." + name + descriptor);
        }

        void staticMethod(String owner, String name, String descriptor) {
            for (MethodNode method : node(owner).methods) if (name.equals(method.name) && descriptor.equals(method.desc)
                && (method.access & Opcodes.ACC_STATIC) != 0) return;
            throw new IllegalStateException("missing static method: " + owner + "." + name + descriptor);
        }

        void pushSite(String owner, String caller) {
            int sites = 0;
            for (MethodNode method : node(owner).methods) if (caller.equals(method.name)) {
                for (var instruction : method.instructions.toArray())
                    if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && (CRAFT + "ICraftingMedium").equals(call.owner)
                        && "pushPattern".equals(call.name)
                        && PUSH.equals(call.desc)) sites++;
            }
            if (sites != 1) throw new IllegalStateException("changed dispatch site: " + owner + "." + caller);
        }
    }
}
