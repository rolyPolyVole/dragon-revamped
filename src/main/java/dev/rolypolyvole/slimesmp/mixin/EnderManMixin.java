package dev.rolypolyvole.slimesmp.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderMan.class)
@SuppressWarnings("unused")
abstract class EnderManMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void preventDragonTarget(LivingEntity target, CallbackInfo ci) {
        if (target instanceof EnderDragon) {
            ci.cancel();
        }
    }
}
