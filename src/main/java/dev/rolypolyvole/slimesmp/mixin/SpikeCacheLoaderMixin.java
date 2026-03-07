package dev.rolypolyvole.slimesmp.mixin;

import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mixin(targets = "net.minecraft.world.level.levelgen.feature.SpikeFeature$SpikeCacheLoader")
public abstract class SpikeCacheLoaderMixin {

    @Unique
    private static final int CRYSTAL_Y = 110;
    @Unique
    private static final int POSITION_OFFSET = 50;

    @Inject(method = "load(Ljava/lang/Long;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void adjustSpikeConfigInputs(Long seedKey, CallbackInfoReturnable<List<SpikeFeature.EndSpike>> cir) {
        List<SpikeFeature.EndSpike> original = cir.getReturnValue();
        List<SpikeFeature.EndSpike> adjusted = new ArrayList<>(original.size());

        for (SpikeFeature.EndSpike s : original) {
            double centerX = s.getCenterX();
            double centerZ = s.getCenterZ();

            var direction = new Vector2d(centerX, centerZ).normalize();
            int newX = (int) Math.round(centerX + new Vector2d(direction).mul(POSITION_OFFSET).x);
            int newZ = (int) Math.round(centerZ + new Vector2d(direction).mul(POSITION_OFFSET).y);

            // Deterministic height from seed + spike position
            Random rng = new Random(seedKey ^ (newX * 341873128712L) ^ (newZ * 132897987541L));
            int height = CRYSTAL_Y + rng.nextInt(30);

            adjusted.add(new SpikeFeature.EndSpike(
                    newX,
                    newZ,
                    1,
                    height,
                    s.isGuarded()
            ));
        }

        cir.setReturnValue(adjusted);
    }
}
