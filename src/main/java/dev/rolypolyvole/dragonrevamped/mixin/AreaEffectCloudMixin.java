package dev.rolypolyvole.dragonrevamped.mixin;

import dev.rolypolyvole.dragonrevamped.dragon.entities.CrystalProtector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin extends Entity {

    @Shadow
    public abstract LivingEntity getOwner();

    @Shadow
    public abstract float getRadius();

    protected AreaEffectCloudMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> filterAndBuffEndermen(Level level, Class<T> entityClass, AABB aabb) {
        List<T> entities = level.getEntitiesOfClass(entityClass, aabb);

        if (!(this.getOwner() instanceof EnderDragon)) {
            return entities;
        }

        float radius = this.getRadius();
        List<T> filtered = new ArrayList<>();

        for (T entity : entities) {
            if (entity instanceof CrystalProtector.CrystalProtectorHorse) continue;
            if (entity instanceof CrystalProtector) continue;

            if (entity instanceof EnderMan enderMan) {
                double dx = enderMan.getX() - this.getX();
                double dz = enderMan.getZ() - this.getZ();
                if (dx * dx + dz * dz <= radius * radius) {
                    enderMan.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
                    enderMan.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
                }
            } else {
                filtered.add(entity);
            }
        }

        return filtered;
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void corrodeItems(net.minecraft.server.level.ServerLevel serverLevel, CallbackInfo ci) {
        if (!(this.getOwner() instanceof EnderDragon)) return;

        float radius = this.getRadius();
        AABB aabb = this.getBoundingBox().inflate(radius, 2.0, radius);
        List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, aabb);

        for (ItemEntity item : items) {
            var stack = item.getItem();
            if (stack.is(Items.DRAGON_EGG)) continue;
            if (stack.has(DataComponents.DAMAGE_RESISTANT)) continue;

            double dx = item.getX() - this.getX();
            double dz = item.getZ() - this.getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                item.discard();
            }
        }
    }
}
