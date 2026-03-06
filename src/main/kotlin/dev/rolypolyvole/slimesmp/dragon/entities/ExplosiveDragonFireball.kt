package dev.rolypolyvole.slimesmp.dragon.entities

import dev.rolypolyvole.slimesmp.util.ExplosionAnimation
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
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

    override fun tick() {
        super.tick()

        val serverLevel = level() as? ServerLevel ?: return
        serverLevel.sendParticles(
            PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
            true, true,
            x, y + 0.5, z,
            3, 0.1, 0.1, 0.1, 0.0
        )
    }

    override fun onHit(hitResult: HitResult) {
        if (hitResult.type == HitResult.Type.ENTITY && hitResult is EntityHitResult) {
            if (ownedBy(hitResult.entity)) return
            if (hitResult.entity.noPhysics) return
        }

        val serverLevel = level() as ServerLevel
        val pos = hitResult.location

        createExplosion(serverLevel, pos)
        spawnEffectCloud(serverLevel, pos)

        discard()
    }

    private fun createExplosion(level: ServerLevel, pos: Vec3) {
        val radius = 2.5F
        val blocks = captureBlocks(level, pos, radius)

        level.explode(
            this,
            damageSources().explosion(this, dragon),
            null,
            pos.x, pos.y, pos.z,
            radius,
            false,
            Level.ExplosionInteraction.MOB
        )

        ExplosionAnimation.play(level, blocks, 0.6)
    }

    private fun captureBlocks(level: ServerLevel, center: Vec3, radius: Float): List<Pair<BlockPos, net.minecraft.world.level.block.state.BlockState>> {
        val r = Mth.ceil(radius.toDouble())
        val blocks = mutableListOf<Pair<BlockPos, net.minecraft.world.level.block.state.BlockState>>()
        val cx = Mth.floor(center.x)
        val cy = Mth.floor(center.y)
        val cz = Mth.floor(center.z)

        for (x in -r..r) {
            for (y in -r..r) {
                for (z in -r..r) {
                    val bp = BlockPos(cx + x, cy + y, cz + z)
                    val state = level.getBlockState(bp)
                    if (!state.isAir) {
                        blocks.add(bp to state)
                    }
                }
            }
        }

        return blocks
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
