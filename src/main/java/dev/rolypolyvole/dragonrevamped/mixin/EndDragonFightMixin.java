package dev.rolypolyvole.dragonrevamped.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
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
    private void customizeBossBar(CallbackInfo ci) {
        this.dragonEvent.setCreateWorldFog(false);
        this.dragonEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
    }
}
