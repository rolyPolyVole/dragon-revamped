package dev.rolypolyvole.dragonrevamped.mixin;

import com.mojang.serialization.Codec;
import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpikeFeature.class)
public abstract class EndSpikeMixin extends Feature<SpikeConfiguration> {

    public EndSpikeMixin(Codec<SpikeConfiguration> codec) {
        super(codec);
    }

    @Inject(method = "placeSpike(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/SpikeConfiguration;Lnet/minecraft/world/level/levelgen/feature/SpikeFeature$EndSpike;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void buildCustomEndSpike(ServerLevelAccessor level, RandomSource random, SpikeConfiguration config, SpikeFeature.EndSpike spike, CallbackInfo ci) {
        CustomEndSpikes.place(level, random, config, spike);
        ci.cancel();
    }
}
