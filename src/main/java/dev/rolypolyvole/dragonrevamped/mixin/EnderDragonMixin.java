package dev.rolypolyvole.dragonrevamped.mixin;

import dev.rolypolyvole.dragonrevamped.data.DragonDamageTypes;
import dev.rolypolyvole.dragonrevamped.dragon.DragonAbilityManager;
import dev.rolypolyvole.dragonrevamped.dragon.DragonAttackManager;
import dev.rolypolyvole.dragonrevamped.dragon.entities.CrystalProtector;
import dev.rolypolyvole.dragonrevamped.util.ExplosionAnimation;
import kotlin.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob implements Enemy {
    @Final @Shadow
    private EnderDragonPhaseManager phaseManager;

    @Shadow
    protected abstract void reallyHurt(ServerLevel serverLevel, DamageSource damageSource, float f);

    @Final @Shadow
    private EnderDragonPart body;
    @Final @Shadow
    public EnderDragonPart head;
    @Final @Shadow
    private EnderDragonPart neck;

    @Shadow
    public int dragonDeathTime;

    @Unique
    private EnderDragonPart hitPart;
    @Unique
    private float damageReceived;

    @Unique
    private DragonAttackManager attackManager;
    @Unique
    private DragonAbilityManager abilityManager;
    @Unique
    private boolean firstTick = true;

    @Unique
    private long lastPlayerCount = -1;

    protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<? extends EnderDragon> entityType, Level level, CallbackInfo ci) {
        this.setCustomName(Component.literal("Ender Dragon").withStyle(ChatFormatting.LIGHT_PURPLE));

        var maxHealth = ((RangedAttributeAccessor) getAttribute(Attributes.MAX_HEALTH).getAttribute().value());
        maxHealth.setMaxValue(4096.0);

        getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1.0);

        this.attackManager = new DragonAttackManager(self());
        this.abilityManager = new DragonAbilityManager(self());
    }

    @Unique
    private EnderDragon self() {
        return (EnderDragon)(Object) this;
    }

    @Unique
    private long nearbyPlayerCount() {
        Vec3 center = self().getFightOrigin().getCenter();
        return level().players().stream()
            .filter(p -> p.position().distanceToSqr(center) < 40000.0)
            .count();
    }

    @Inject(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;updateDragon(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;)V", shift = At.Shift.BEFORE))
    private void beforeUpdateDragon(CallbackInfo info) {
        if (getHealth() <= 1.0F) return;
        
        updateMaxHealth();
    }

    @Inject(method = "aiStep()V", at = @At("TAIL"))
    private void tick(CallbackInfo info) {
        if (getHealth() <= 1.0F) return;

        if (firstTick) {
            this.firstTick = false;
            phaseManager.setPhase(EnderDragonPhase.HOLDING_PATTERN);
        }

        if (abilityManager != null) abilityManager.tick();
        if (attackManager != null && !abilityManager.getRageAbility().isActive()) attackManager.tick();
    }

    @Inject(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.BEFORE))
    private void beforeMove(CallbackInfo info) {
        if (abilityManager != null && abilityManager.getRageAbility().isActive()) {
            abilityManager.getRageAbility().onBeforeMove();
            return;
        }

        if (attackManager != null && getHealth() > 1.0F) attackManager.onBeforeMove();
    }

    @Unique
    private void updateMaxHealth() {
        long playerCount = Math.max(nearbyPlayerCount(), 1);
        if (playerCount == lastPlayerCount) return;
        this.lastPlayerCount = playerCount;

        double newMax = 800.0 + 700 * Math.sqrt(playerCount - 1);
        float oldMax = this.getMaxHealth();
        float healthPercent = this.getHealth() / oldMax;

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMax);
        this.setHealth(healthPercent * (float) newMax);
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void headAttack(ServerLevel serverLevel, List<Entity> list, CallbackInfo info) {
        float damage = 14.0F;

        for (Entity entity : list) {
            if (entity instanceof LivingEntity) {
                DamageSource damageSource = DragonDamageTypes.INSTANCE.dragonBodyHit(serverLevel, self());
                entity.hurtServer(serverLevel, damageSource, damage);
                EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damageSource);
            }
        }

        info.cancel();
    }

    @Inject(method = "knockBack", at = @At("HEAD"), cancellable = true)
    private void wingAttack(ServerLevel serverLevel, List<Entity> list, CallbackInfo info) {
        float damage = 8.0F;

        double centerX = (this.body.getBoundingBox().minX + this.body.getBoundingBox().maxX) / 2.0;
        double centerZ = (this.body.getBoundingBox().minZ + this.body.getBoundingBox().maxZ) / 2.0;

        for (Entity entity : list) {
            if (entity instanceof LivingEntity livingEntity) {
                double dx = entity.getX() - centerX;
                double dz = entity.getZ() - centerZ;
                double distanceSqr = Math.max(dx * dx + dz * dz, 0.1);
                double yVelocity = 0.2F;

                entity.push(dx / distanceSqr * 4.0, yVelocity, dz / distanceSqr * 4.0);

                boolean isSitting = this.phaseManager.getCurrentPhase().isSitting();
                boolean onCooldown = livingEntity.getLastHurtByMobTimestamp() >= entity.tickCount - 2;

                if (!isSitting && !onCooldown) {
                    DamageSource damageSource = DragonDamageTypes.INSTANCE.dragonBodyHit(serverLevel, self());
                    entity.hurtServer(serverLevel, damageSource, damage);
                    EnchantmentHelper.doPostAttackEffects(serverLevel, entity, damageSource);
                }
            }
        }

        info.cancel();
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
    private void storeDamageData(ServerLevel level, EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (abilityManager != null && abilityManager.getRageAbility().isActive()) {
            cir.setReturnValue(false);
            return;
        }

        this.hitPart = part;
        this.damageReceived = amount;
    }

    @Redirect(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;reallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void customDamage(EnderDragon dragon, ServerLevel serverLevel, DamageSource source, float f) {
        float adjusted = phaseManager.getCurrentPhase().onHurt(source, damageReceived);

        float finalDamage = (hitPart == head || hitPart == neck)
            ? adjusted * 2.0F
            : adjusted;

        if (source.is(DamageTypeTags.IS_EXPLOSION) && phaseManager.getCurrentPhase().getPhase() == EnderDragonPhase.LANDING) {
            finalDamage *= 0.2F;
        }

        this.reallyHurt(serverLevel, source, finalDamage);

        if (source.getEntity() instanceof ServerPlayer player) {
            int health = this.getHealth() <= 1.0F ? 0 : Math.round(this.getHealth());
            Component message = Component.literal("❤ Dragon Health: " + health).withStyle(ChatFormatting.RED);

            player.sendSystemMessage(message, true);
        }
    }

    @ModifyConstant(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", constant = @Constant(floatValue = 0.25F))
    private float customTakeoffThreshold(float original) {
        EnderDragon dragon = self();

        float max = dragon.getMaxHealth();
        if (max <= 0.0F) return original;

        float threshold = 0.15F * max;
        return threshold / max;
    }

    @ModifyConstant(method = "checkCrystals()V", constant = @Constant(floatValue = 1.0F))
    private float customCrystalHeal(float original) {
        int ticksSinceLastHurt = tickCount - getLastHurtByMobTimestamp();
        double playerCount = Math.max(nearbyPlayerCount(), 1);
        int threshold = (int) (300 + 900 / Math.sqrt(playerCount));

        return ticksSinceLastHurt > threshold ? getMaxHealth() * 0.01F : 1.0F + ((float) playerCount) * 0.1F;
    }

    @Redirect(method = "aiStep()V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;hurtTime:I", opcode = Opcodes.GETFIELD), require = 1)
    private int ignoreHurtTime(EnderDragon instance) {
        return 0;
    }

    @Redirect(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getFlySpeed()F"))
    private float getFlySpeed(DragonPhaseInstance instance) {
        float original = Math.max(1.0F, instance.getFlySpeed());

        if (instance.getPhase() == EnderDragonPhase.CHARGING_PLAYER) {
            return original * 2.4F;
        } else {
            return original * 1.7F;
        }
    }

    @Redirect(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonPhaseInstance;getTurnSpeed()F"))
    private float getTurnSpeed(DragonPhaseInstance instance) {
        float original = instance.getTurnSpeed();

        return original * 1.7F * attackManager.getTurnSpeedMultiplier();
    }

    @ModifyConstant(method = "aiStep()V", constant = @Constant(floatValue = 0.06F))
    private float forwardMovement(float original) {
        return original * 1.7F * attackManager.getSpeedMultiplier();
    }

    @Redirect(method = "checkWalls", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean breakBlockWithAnimation(ServerLevel level, BlockPos pos, boolean flags) {
        BlockState state = level.getBlockState(pos);
        boolean result = level.removeBlock(pos, flags);

        if (result) {
            ExplosionAnimation.INSTANCE.play(level, List.of(new Pair<>(pos.immutable(), state)), 0.4);
        }

        return result;
    }

    @ModifyConstant(method = "checkCrystals()V", constant = @Constant(doubleValue = 32.0))
    private double increaseCrystalRange(double original) {
        return 128.0;
    }

    @ModifyConstant(method = "onCrystalDestroyed", constant = @Constant(floatValue = 10.0F))
    private float increaseCrystalDamage(float original) {
        return 25.0F;
    }

    @Inject(method = "tickDeath", at = @At("HEAD"))
    private void onDeath(CallbackInfo ci) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        if (dragonDeathTime != 0) return;

        Vec3 center = self().getFightOrigin().getCenter();
        AABB area = AABB.ofSize(center, 400.0, 400.0, 400.0);

        for (CrystalProtector protector : serverLevel.getEntitiesOfClass(CrystalProtector.class, area)) {
            protector.remove(RemovalReason.DISCARDED);
        }

        for (Endermite endermite : serverLevel.getEntitiesOfClass(Endermite.class, area)) {
            endermite.discard();
        }
    }

    @Inject(method = "onCrystalDestroyed", at = @At("TAIL"))
    private void onCrystalDestroyed(ServerLevel serverLevel, EndCrystal endCrystal, BlockPos blockPos, DamageSource damageSource, CallbackInfo ci) {
        if (abilityManager != null) {
            abilityManager.getCrystalRespawnAbility().onCrystalDestroyed(blockPos);
            abilityManager.getCrystalRespawnAbility().sendCrystalCount();
        }
    }
}
