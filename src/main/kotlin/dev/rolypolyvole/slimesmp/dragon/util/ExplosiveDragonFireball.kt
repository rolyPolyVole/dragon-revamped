package dev.rolypolyvole.slimesmp.dragon.util

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.AreaEffectCloud
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class ExplosiveDragonFireball(level: Level, owner: LivingEntity, direction: Vec3) : DragonFireball(level, owner, direction) {

    private val dragon = owner

    init {
        accelerationPower = 0.0
    }

    override fun getInertia(): Float = 1.0f

    override fun shouldBeSaved(): Boolean = false

    override fun onHit(hitResult: HitResult) {
        if (hitResult.type == HitResult.Type.ENTITY && ownedBy((hitResult as EntityHitResult).entity)) return
        if (level().isClientSide) return

        val serverLevel = level() as ServerLevel
        val pos = hitResult.location

        createExplosion(serverLevel, pos)
        spawnEffectCloud(serverLevel, pos)

        discard()
    }

    private fun createExplosion(level: ServerLevel, pos: Vec3) {
        level.explode(
            this,
            damageSources().explosion(this, dragon),
            null,
            pos.x, pos.y, pos.z,
            3.0f,
            false,
            Level.ExplosionInteraction.NONE
        )
    }

    private fun spawnEffectCloud(level: ServerLevel, pos: Vec3) {
        AreaEffectCloud(level, pos.x, pos.y, pos.z).let {
            it.owner = dragon
            it.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f))
            it.radius = 3.0f
            it.duration = 600
            it.radiusPerTick = (7.0f - it.radius) / it.duration
            it.setPotionDurationScale(0.25F)
            it.addEffect(MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
            level.addFreshEntity(it)
        }
    }
}
