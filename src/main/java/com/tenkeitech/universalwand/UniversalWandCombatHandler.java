/*
 * SPDX-FileCopyrightText: 2026 TenkeiTech
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tenkeitech.universalwand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import com.tenkeitech.Config;
import com.tenkeitech.TenkeiTech;
import com.tenkeitech.item.ItemUniversalWand;
import com.tenkeitech.mixin.minecraft.UniversalWandLivingAccess;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** A synchronous attack owns its escalation and secondary targets; native code owns death and rewards. */
public final class UniversalWandCombatHandler {

    private static final ThreadLocal<AttackScope> ACTIVE = new ThreadLocal<>();
    private static final int MAX_DAMAGE_ATTEMPTS = 100;
    private static final Map<EntityLivingBase, Boolean> REWARDED = new java.util.WeakHashMap<>();

    public static void addConfiguredDrops(LivingDropsEvent event, EntityPlayerMP player) {
        EntityLivingBase victim = event.entityLiving;
        ItemStack wand = player.getHeldItem();
        if (event.isCanceled() || victim instanceof EntityPlayer
            || victim.getHealth() > 0
            || player.isDead
            || player.worldObj != victim.worldObj
            || !ItemUniversalWand.isHeldBy(player)
            || !ItemUniversalWand.isBeheadingEnabled(wand) && !ItemUniversalWand.isSpawnEggEnabled(wand)
            || REWARDED.put(victim, Boolean.TRUE) != null) return;
        if (ItemUniversalWand.isBeheadingEnabled(wand)) {
            int skull = -1;
            boolean special = isSpecialMob(victim);
            if (victim.getClass() == net.minecraft.entity.monster.EntitySkeleton.class
                || special && victim instanceof net.minecraft.entity.monster.EntitySkeleton)
                skull = ((net.minecraft.entity.monster.EntitySkeleton) victim).getSkeletonType() == 1 ? 1 : 0;
            else if (victim.getClass() == net.minecraft.entity.monster.EntityZombie.class
                || special && victim instanceof net.minecraft.entity.monster.EntityZombie
                    && !(victim instanceof net.minecraft.entity.monster.EntityPigZombie))
                skull = 2;
            else if (victim.getClass() == net.minecraft.entity.monster.EntityCreeper.class
                || special && victim instanceof net.minecraft.entity.monster.EntityCreeper) skull = 4;
            if (skull >= 0) event.drops.add(
                new EntityItem(
                    victim.worldObj,
                    victim.posX,
                    victim.posY,
                    victim.posZ,
                    new ItemStack(Items.skull, 1, skull)));
        }
        if (ItemUniversalWand.isSpawnEggEnabled(wand)) {
            int id = spawnEggId(victim);
            if (id > 0 && id <= Short.MAX_VALUE && EntityList.entityEggs.containsKey(id)) event.drops.add(
                new EntityItem(
                    victim.worldObj,
                    victim.posX,
                    victim.posY,
                    victim.posZ,
                    new ItemStack(Items.spawn_egg, 1, id)));
        }
    }

    private static boolean isSpecialMob(EntityLivingBase victim) {
        return victim.getClass()
            .getName()
            .startsWith("toast.specialMobs.entity.");
    }

    private static int spawnEggId(EntityLivingBase victim) {
        int id = EntityList.getEntityID(victim);
        if (isRegisteredEgg(id, victim.getClass())) return id;
        // Special Mobs registers variant eggs only in the forward map, without classToIDMapping.
        int found = Integer.MAX_VALUE;
        for (Object key : EntityList.entityEggs.keySet()) {
            if (key instanceof Integer candidate && isRegisteredEgg(candidate, victim.getClass()))
                found = Math.min(found, candidate);
        }
        if (found != Integer.MAX_VALUE) return found;
        Class<?> base = null;
        for (Class<?> type = victim.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName()
                .startsWith("net.minecraft.entity.monster.")) {
                base = type;
                break;
            }
        }
        if (base == null) return -1;
        // Replaced vanilla mobs (for example Ender Expansion's Enderman) retain the nearest vanilla family egg.
        for (Object key : EntityList.entityEggs.keySet()) {
            if (!(key instanceof Integer candidate) || candidate <= 0 || candidate > Short.MAX_VALUE) continue;
            Class<?> registered = EntityList.IDtoClassMapping.get(candidate);
            if (registered != null && (registered == base || base.isAssignableFrom(registered) && base.getSimpleName()
                .equals("Entity" + EntityList.getStringFromID(candidate)))) found = Math.min(found, candidate);
        }
        return found == Integer.MAX_VALUE ? -1 : found;
    }

    private static boolean isRegisteredEgg(int id, Class<?> type) {
        return id > 0 && id <= Short.MAX_VALUE
            && EntityList.entityEggs.containsKey(id)
            && EntityList.IDtoClassMapping.get(id) == type;
    }

    public static AttackScope begin(EntityPlayer player, Entity target) {
        if (ACTIVE.get() != null || !(player instanceof EntityPlayerMP mp)
            || player.worldObj.isRemote
            || !ItemUniversalWand.isHeldBy(player)) return null;
        ItemStack wand = player.getHeldItem();
        boolean force = ItemUniversalWand.isForceKillEnabled(wand);
        boolean aoe = ItemUniversalWand.isAoeDamageEnabled(wand);
        EntityLivingBase victim = resolve(target);
        if ((!force && !aoe) || !eligible(player, victim)) return null;
        AttackScope scope = new AttackScope(mp, wand, target, victim, force, aoe);
        ACTIVE.set(scope);
        return scope;
    }

    public static void observeAttack(EntityPlayer player, Entity target, boolean accepted) {
        AttackScope scope = ACTIVE.get();
        if (scope != null && scope.player == player && scope.original == target) scope.accepted = accepted;
    }

    public static boolean beforeAttack(EntityPlayer player, Entity target) {
        AttackScope scope = ACTIVE.get();
        return scope != null && scope.player == player && scope.original == target && scope.beforeAttack();
    }

    private static EntityLivingBase resolve(Entity entity) {
        if (entity instanceof EntityDragonPart part && part.entityDragonObj instanceof EntityLivingBase parent)
            return parent;
        return entity instanceof EntityLivingBase living ? living : null;
    }

    private static boolean eligible(EntityPlayer player, EntityLivingBase target) {
        if (target == null || target instanceof EntityPlayer
            || target.isDead
            || target.getHealth() <= 0
            || target.worldObj != player.worldObj
            || player.isOnSameTeam(target)) return false;
        if (target instanceof EntityTameable pet && pet.isTamed()) {
            EntityLivingBase owner = pet.getOwner();
            if (owner == null || owner == player || player.isOnSameTeam(owner)) return false;
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDeath(LivingDeathEvent event) {
        AttackScope scope = ACTIVE.get();
        if (scope != null) scope.deaths.put(event.entityLiving, event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDrops(LivingDropsEvent event) {
        AttackScope scope = ACTIVE.get();
        if (scope != null && scope.kill != null && scope.kill.target == event.entityLiving) {
            scope.kill.dropsAttempted = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttack(LivingAttackEvent event) {
        AttackScope scope = ACTIVE.get();
        if (scope != null) scope.attacks.put(event.entityLiving, event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onHurt(LivingHurtEvent event) {
        AttackScope scope = ACTIVE.get();
        if (scope != null) scope.hurts.put(event.entityLiving, event);
    }

    public static final class AttackScope implements AutoCloseable {

        private final EntityPlayerMP player;
        private final ItemStack wand;
        private final ItemStack snapshot;
        private final World world;
        private final Entity original;
        private final EntityLivingBase primary;
        private final boolean force;
        private final boolean aoe;
        private final float damage;
        private final AxisAlignedBB bounds;
        private final double x, y, z;
        private final Map<EntityLivingBase, LivingDeathEvent> deaths = new IdentityHashMap<>();
        private final Map<EntityLivingBase, LivingAttackEvent> attacks = new IdentityHashMap<>();
        private final Map<EntityLivingBase, LivingHurtEvent> hurts = new IdentityHashMap<>();
        private boolean accepted;
        private ForceKillContext kill;
        private boolean finished;
        private boolean strongPrimary;
        private boolean strongPrimaryAttempted;

        private AttackScope(EntityPlayerMP player, ItemStack wand, Entity original, EntityLivingBase primary,
            boolean force, boolean aoe) {
            this.player = player;
            this.wand = wand;
            this.snapshot = wand.copy();
            this.world = player.worldObj;
            this.original = original;
            this.primary = primary;
            this.force = force;
            this.aoe = aoe;
            double attribute = player.getEntityAttribute(SharedMonsterAttributes.attackDamage)
                .getAttributeValue();
            damage = Double.isNaN(attribute) || attribute <= 0 ? 0 : (float) Math.min(Float.MAX_VALUE, attribute);
            x = original.posX;
            y = original.posY;
            z = original.posZ;
            double radius = (ItemUniversalWand.getAoeSize(wand) - 1) / 2D;
            bounds = AxisAlignedBB.getBoundingBox(x - radius, y - 3, z - radius, x + radius, y + 3, z + radius);
        }

        private boolean valid() {
            return !player.isDead && player.worldObj == world
                && primary.worldObj == world
                && player.getHeldItem() == wand
                && ItemStack.areItemStacksEqual(wand, snapshot);
        }

        public void complete() {
            if (finished) return;
            finished = true;
            if (!accepted || !valid() || !strongPrimary && canceled(primary)) return;
            if (force && !strongPrimaryAttempted) forceKill(primary);
            if (!aoe || !valid() || damage <= 0) return;
            List<EntityLivingBase> targets = new ArrayList<>();
            for (Object entity : player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bounds)) {
                EntityLivingBase target = (EntityLivingBase) entity;
                if (target != primary && eligible(player, target)) targets.add(target);
            }
            targets.sort(
                Comparator.comparingDouble((EntityLivingBase target) -> target.getDistanceSq(x, y, z))
                    .thenComparingInt(Entity::getEntityId));
            int count = Math.min(Config.universalWandAoeMaxTargets, targets.size());
            for (int index = 0; index < count && valid(); index++) {
                EntityLivingBase target = targets.get(index);
                try {
                    if (!eligible(player, target)
                        || MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target))) continue;
                    if (!valid() || !eligible(player, target)) continue;
                    if (force && forceKill(target)) continue;
                    boolean hit = target.attackEntityFrom(DamageSource.causePlayerDamage(player), damage);
                } catch (RuntimeException failure) {
                    TenkeiTech.LOG
                        .error("Universal wand secondary attack failed for entity {}", target.getEntityId(), failure);
                }
            }
        }

        private boolean canceled(EntityLivingBase target) {
            LivingAttackEvent attack = attacks.get(target);
            LivingHurtEvent hurt = hurts.get(target);
            LivingDeathEvent death = deaths.get(target);
            return attack != null && attack.isCanceled() || hurt != null && (hurt.isCanceled() || hurt.ammount <= 0)
                || death != null && death.isCanceled();
        }

        public boolean beforeAttack() {
            if (!force || !valid()) return false;
            strongPrimaryAttempted = true;
            if (!forceKill(primary)) return false;
            accepted = true;
            strongPrimary = true;
            // The strong path already settled the primary. Only run the area operation here.
            complete();
            return true;
        }

        private boolean forceKill(EntityLivingBase target) {
            if (!valid() || !eligible(player, target) || kill != null) return false;
            ForceKillContext context = new ForceKillContext(target);
            kill = context;
            try {
                DamageSource source = DamageSource.causePlayerDamage(player)
                    .setDamageBypassesArmor()
                    .setDamageIsAbsolute();
                if (target instanceof EntityDragon dragon) {
                    clearInvulnerability(target);
                    dragon.attackEntityFromPart(dragon.dragonPartHead, source, killDamage(target));
                }
                for (int attempt = 0; attempt < MAX_DAMAGE_ATTEMPTS && valid()
                    && !committed(target)
                    && target.getHealth() > 0
                    && !deaths.containsKey(target); attempt++) {
                    context.rememberHealth();
                    clearInvulnerability(target);
                    target.attackEntityFrom(source, killDamage(target));
                }
                if (committed(target)) return true;
                if (!valid()) return false;
                if (!deaths.containsKey(target) && !context.dropsAttempted) {
                    context.rememberHealth();
                    target.setHealth(0);
                    target.onDeath(source);
                }
                if (committed(target)) return true;
                if (!valid()) return false;
                context.restoreHealth();
                target.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                    .setBaseValue(0);
                UniversalWandLivingAccess access = (UniversalWandLivingAccess) target;
                access.tenkeitech$setAttackingPlayer(player);
                access.tenkeitech$setRecentlyHit(100);
                target.setHealth(0);
                if (!context.dropsAttempted) target.onDeath(source);
                if (committed(target)) return true;
                if (!valid()) return false;
                fallbackDeath(context, source);
                return committed(target);
            } catch (RuntimeException failure) {
                TenkeiTech.LOG.error("Universal wand force kill failed for entity {}", target.getEntityId(), failure);
                return committed(target);
            } finally {
                try {
                    if (!committed(target)) {
                        target.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                            .setBaseValue(context.maxHealthBase);
                        context.restoreHealth();
                        target.hurtResistantTime = context.resistance;
                        if (target instanceof EntityWither wither) wither.func_82215_s(context.witherInvulnerability);
                        if (target instanceof UniversalWandGaiaAccess gaia)
                            gaia.tenkeitech$setGaiaInvulnerability(context.gaiaInvulnerability);
                    }
                } finally {
                    kill = null;
                }
            }
        }

        private void fallbackDeath(ForceKillContext context, DamageSource source) {
            EntityLivingBase target = context.target;
            UniversalWandLivingAccess access = (UniversalWandLivingAccess) target;
            if (committed(target)) return;
            target.setHealth(0);
            access.tenkeitech$setDeathCommitted(true);
            access.tenkeitech$setAttackingPlayer(player);
            access.tenkeitech$setRecentlyHit(100);
            target.setRevengeTarget(player);
            player.onKillEntity(target);
            if (!context.dropsAttempted && world.getGameRules()
                .getGameRuleBooleanValue("doMobLoot")) {
                context.dropsAttempted = true;
                boolean captureBefore = target.captureDrops;
                ArrayList<EntityItem> previousDrops = new ArrayList<>(target.capturedDrops);
                target.captureDrops = true;
                target.capturedDrops.clear();
                try {
                    int looting = EnchantmentHelper.getLootingModifier(player);
                    int rareRoll = 200;
                    if (access.tenkeitech$canDropLoot()) {
                        access.tenkeitech$dropFewItems(true, looting);
                        access.tenkeitech$dropEquipment(true, looting);
                        rareRoll = world.rand.nextInt(200) - looting;
                        if (rareRoll < 5) access.tenkeitech$dropRareDrop(rareRoll <= 0 ? 1 : 0);
                    }
                    ArrayList<EntityItem> drops = new ArrayList<>(target.capturedDrops);
                    target.captureDrops = captureBefore;
                    if (!ForgeHooks.onLivingDrops(target, source, drops, looting, true, rareRoll)) {
                        for (EntityItem drop : drops) world.spawnEntityInWorld(drop);
                    }
                } finally {
                    target.captureDrops = captureBefore;
                    target.capturedDrops.clear();
                    target.capturedDrops.addAll(previousDrops);
                }
            }
            world.setEntityState(target, (byte) 3);
            // Vanilla death updates own XP and delayed removal, including boss death animations.
            // Targets that refuse a zero health value cannot enter that path, so use world cleanup.
            if (target.getHealth() > 0) world.removeEntity(target);
        }

        private static void clearInvulnerability(EntityLivingBase target) {
            target.hurtResistantTime = 0;
            if (target instanceof EntityWither wither) wither.func_82215_s(0);
            // Gaia's spawn timer both rejects native hits (and attacker registration) and heals after death.
            if (target instanceof UniversalWandGaiaAccess gaia) gaia.tenkeitech$setGaiaInvulnerability(0);
        }

        private static float killDamage(EntityLivingBase target) {
            double amount = (double) target.getHealth() + target.getAbsorptionAmount() + target.getMaxHealth() + 1;
            return Double.isNaN(amount) || Double.isInfinite(amount) ? 1024F
                : (float) Math.min(Float.MAX_VALUE, Math.max(1024D, amount));
        }

        private static boolean committed(EntityLivingBase target) {
            return target.isDead || ((UniversalWandLivingAccess) target).tenkeitech$isDeathCommitted();
        }

        private static final class ForceKillContext {

            final EntityLivingBase target;
            final double maxHealthBase;
            final int resistance;
            final int witherInvulnerability;
            final int gaiaInvulnerability;
            float lastHealth;
            boolean dropsAttempted;

            ForceKillContext(EntityLivingBase target) {
                this.target = target;
                maxHealthBase = target.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                    .getBaseValue();
                resistance = target.hurtResistantTime;
                witherInvulnerability = target instanceof EntityWither wither ? wither.func_82212_n() : 0;
                gaiaInvulnerability = target instanceof UniversalWandGaiaAccess gaia
                    ? gaia.tenkeitech$getGaiaInvulnerability()
                    : 0;
                rememberHealth();
            }

            void rememberHealth() {
                float health = target.getHealth();
                if (!Float.isNaN(health) && !Float.isInfinite(health) && health > 0) lastHealth = health;
            }

            void restoreHealth() {
                if (!committed(target) && target.getHealth() <= 0 && lastHealth > 0)
                    target.setHealth(Math.min(lastHealth, target.getMaxHealth()));
            }
        }

        @Override
        public void close() {
            if (ACTIVE.get() == this) ACTIVE.remove();
        }
    }
}
