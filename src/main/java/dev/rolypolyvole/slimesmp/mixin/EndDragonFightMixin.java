package dev.rolypolyvole.slimesmp.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndDragonFight.class)
public abstract class EndDragonFightMixin {
    @Shadow @Final
    private ServerBossEvent dragonEvent;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void removeFog(CallbackInfo ci) {
        this.dragonEvent.setCreateWorldFog(false);
    }
}
