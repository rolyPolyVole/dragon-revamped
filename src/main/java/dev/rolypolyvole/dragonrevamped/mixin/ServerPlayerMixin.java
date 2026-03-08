package dev.rolypolyvole.dragonrevamped.mixin;

import com.mojang.authlib.GameProfile;
import dev.rolypolyvole.dragonrevamped.data.DragonDamageTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {
    public ServerPlayerMixin(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Shadow
    public abstract @NonNull ServerLevel level();

    @Unique
    private DamageSource deathSource;

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void saveDeathSource(DamageSource damageSource, CallbackInfo ci) {
        this.deathSource = damageSource;
    }

    @ModifyVariable(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At(value = "STORE", ordinal = 0), name = "component")
    private Component customDeathMessage(Component value) {
        if (deathSource == null || deathSource.getDirectEntity() == null) {
            return value;
        }

        var name = deathSource.getDirectEntity().getDisplayName();

        if (DragonDamageTypes.INSTANCE.dragonLightning(level()) == deathSource.typeHolder()) {
            return Component.empty()
                .append(getDisplayName())
                .append(" was struck down by ")
                .append(name);
        }

        if (DragonDamageTypes.INSTANCE.dragonBodyHit(level()) == deathSource.typeHolder()) {
            return Component.empty()
                .append(getDisplayName())
                .append(" got ran through by ")
                .append(name);
        }

        return value;
    }
}
