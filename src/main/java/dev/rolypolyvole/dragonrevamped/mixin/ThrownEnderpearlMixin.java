package dev.rolypolyvole.dragonrevamped.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin extends Entity {

    private ThrownEnderpearlMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyConstant(method = "onHit", constant = @Constant(floatValue = 0.05F))
    private float increaseEndermiteSpawnChance(float original) {
        if (this.level() instanceof ServerLevel serverLevel && !serverLevel.getDragons().isEmpty()) {
            return 0.5F;
        }

        return original;
    }
}
