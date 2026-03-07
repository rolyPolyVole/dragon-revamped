package dev.rolypolyvole.slimesmp.mixin;

import dev.rolypolyvole.slimesmp.dragon.entities.DragonSkeleton;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderMan.class)
public abstract class EnderManMixin extends Monster {

    protected EnderManMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void preventDragonTarget(LivingEntity target, CallbackInfo ci) {
        if (target instanceof EnderDragon) {
            ci.cancel();
        }

        if (((ServerLevel) level()).getDragons().isEmpty()) return;

        if (target instanceof Endermite || target instanceof DragonSkeleton) {
            ci.cancel();
        }
    }
}
