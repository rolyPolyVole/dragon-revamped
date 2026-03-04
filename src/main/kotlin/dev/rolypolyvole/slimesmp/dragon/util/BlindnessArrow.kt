package dev.rolypolyvole.slimesmp.dragon.util

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class BlindnessArrow(
    level: Level,
    shooter: LivingEntity,
    pickupItem: ItemStack,
    weapon: ItemStack?
) : Arrow(level, shooter, pickupItem, weapon) {

    override fun shouldBeSaved(): Boolean = false

    override fun tick() {
        super.tick()

        if (isInGround) return

        val level = level() as? ServerLevel ?: return

        level.sendParticles(
            PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
            x, y, z,
            1,
            0.0, 0.0, 0.0,
            0.0
        )
    }

    override fun doPostHurtEffects(target: LivingEntity) {
        super.doPostHurtEffects(target)

        target.addEffect(MobEffectInstance(MobEffects.WITHER, 85, 1))

        if (Math.random() < 0.1) {
            if (Math.random() < 0.5) {
                target.addEffect(MobEffectInstance(MobEffects.DARKNESS, 85, 0))
            } else {
                EntityType.ENDERMITE.create(target.level(), EntitySpawnReason.EVENT)?.let {
                    it.snapTo(target.eyePosition)
                    it.target = target
                }
            }
        }
    }
}
