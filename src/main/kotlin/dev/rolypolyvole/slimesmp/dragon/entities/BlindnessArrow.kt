package dev.rolypolyvole.slimesmp.dragon.entities

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class BlindnessArrow(
    level: Level,
    shooter: LivingEntity,
    pickupItem: ItemStack,
    weapon: ItemStack?
) : Arrow(level, shooter, pickupItem, weapon) {

    class PhantomMite(level: Level) : Endermite(EntityType.ENDERMITE, level) {

        init {
            getAttribute(Attributes.MOVEMENT_SPEED)!!.baseValue = 0.3
            getAttribute(Attributes.ATTACK_DAMAGE)!!.baseValue = 6.0
            getAttribute(Attributes.STEP_HEIGHT)!!.baseValue = 1.0
            getAttribute(Attributes.ATTACK_KNOCKBACK)!!.baseValue = 1.0
        }

        override fun canBeHitByProjectile(): Boolean = false

        override fun tick() {
            val vehicle = this.vehicle
            this.noPhysics = vehicle is Arrow && !vehicle.onGround()

            super.tick()
        }
    }

    init {
        if (Math.random() < 0.1) {
            PhantomMite(level()).startRiding(this)
        }
    }

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
            target.addEffect(MobEffectInstance(MobEffects.DARKNESS, 85, 0))
        }
    }
}
