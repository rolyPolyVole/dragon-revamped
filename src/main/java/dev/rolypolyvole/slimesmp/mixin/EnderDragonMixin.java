package dev.rolypolyvole.slimesmp.mixin;

import dev.rolypolyvole.slimesmp.data.DragonDamageTypes;
import dev.rolypolyvole.slimesmp.dragon.DragonAbilityManager;
import dev.rolypolyvole.slimesmp.dragon.DragonAttackManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.phys.Vec3;

import java.util.List;

@Mixin(EnderDragon.class)
@SuppressWarnings("unused")
abstract class EnderDragonMixin extends Mob implements Enemy {
    @Final @Shadow
    private EnderDragonPhaseManager phaseManager;
    @Final @Shadow
    private EnderDragonPart body;

    @Shadow
    protected abstract void reallyHurt(ServerLevel serverLevel, DamageSource damageSource, float f);

    @Final @Shadow
    public EnderDragonPart head;
    @Unique
    private EnderDragonPart hitPart;
    @Unique
    private float damageReceived;

    @Unique
    private DragonAttackManager attackManager;
    @Unique
    private DragonAbilityManager abilityManager;

    protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<? extends EnderDragon> entityType, Level level, CallbackInfo ci) {
        this.setCustomName(Component.literal("Ender Dragon").withStyle(ChatFormatting.LIGHT_PURPLE));

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

    @Inject(method = "aiStep()V", at = @At("TAIL"))
    private void tick(CallbackInfo info) {
        if (this.level().isClientSide()) return;

        updateMaxHealth();

        if (tickCount > 10 * 20 && !isDeadOrDying()) {
            if (attackManager != null) attackManager.tick();
            if (abilityManager != null) abilityManager.tick();
        }
    }

    @Unique
    private void updateMaxHealth() {
        double playerCount = Math.max(nearbyPlayerCount(), 1);
        double newMax = 800.0 + 700 * Math.sqrt(playerCount - 1);
        double oldMax = this.getMaxHealth();

        if (newMax == oldMax) return;

        float healthPercent = this.getHealth() / (float) oldMax;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMax);
        this.setHealth(healthPercent * (float) newMax);
    }

    @Inject(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", shift = At.Shift.BEFORE))
    private void beforeMove(CallbackInfo info) {
        if (this.level().isClientSide()) return;

        if (attackManager != null && tickCount > 15 * 20 && !isDeadOrDying()) attackManager.onBeforeMove();
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

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At("HEAD"))
    private void storeDamageData(ServerLevel level, EnderDragonPart part, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.hitPart = part;
        this.damageReceived = amount;
    }

    @Redirect(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;reallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void customDamage(EnderDragon dragon, ServerLevel serverLevel, DamageSource source, float f) {
        float adjusted = phaseManager.getCurrentPhase().onHurt(source, damageReceived);

        float finalDamage = (hitPart == head)
            ? adjusted * 2.0F
            : adjusted;

        this.reallyHurt(serverLevel, source, finalDamage);
    }

    @ModifyConstant(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 0.25F))
    private float customTakeoffThreshold(float original) {
        EnderDragon dragon = self();

        float max = dragon.getMaxHealth();
        if (max <= 0.0F) return original;

        float threshold = Math.min(0.10F * max, 100.0F);
        return threshold / max;
    }

    @ModifyConstant(method = "checkCrystals()V", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 1.0F))
    private float customCrystalHeal(float original) {
        int ticksSinceLastHurt = tickCount - getLastHurtByMobTimestamp();
        double playerCount = Math.max(nearbyPlayerCount(), 1);
        int threshold = (int) (100 + 150 / Math.sqrt(playerCount));

        return ticksSinceLastHurt > threshold ? 8.0F : 2.0F;
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

    @ModifyConstant(method = "aiStep()V", constant = @org.spongepowered.asm.mixin.injection.Constant(floatValue = 0.06F))
    private float forwardMovement(float original) {
        return original * 1.7F * attackManager.getSpeedMultiplier();
    }
}
