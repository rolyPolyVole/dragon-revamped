package dev.rolypolyvole.dragonrevamped.dragon.entities

import dev.rolypolyvole.dragonrevamped.util.ExplosionAnimation
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.PowerParticleOption
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.AreaEffectCloud
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
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
            10, 0.1, 0.1, 0.1, 0.0
        )
    }

    override fun onHit(hitResult: HitResult) {
        if (hitResult.type == HitResult.Type.ENTITY && hitResult is EntityHitResult) {
            if (hitResult.entity is EnderDragon) return
            if (hitResult.entity.noPhysics) return
        }

        val serverLevel = level() as ServerLevel
        val pos = hitResult.location

        createExplosion(serverLevel, pos)
        spawnEffectCloud(serverLevel, pos)

        discard()
    }

    private fun createExplosion(level: ServerLevel, pos: Vec3) {
        val blocks = captureBlocks(level, pos)

        level.explode(
            this,
            damageSources().explosion(this, dragon),
            null,
            pos.x, pos.y, pos.z,
            2.5F,
            false,
            Level.ExplosionInteraction.MOB
        )

        ExplosionAnimation.play(level, blocks, 0.6)
    }

    private fun captureBlocks(level: ServerLevel, center: Vec3): List<Pair<BlockPos, BlockState>> {
        val blocks = mutableListOf<Pair<BlockPos, BlockState>>()
        val radius = 2
        val centerX = Mth.floor(center.x)
        val centerY = Mth.floor(center.y)
        val centerZ = Mth.floor(center.z)

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = BlockPos(centerX + x, centerY + y, centerZ + z)
                    val state = level.getBlockState(pos)

                    if (!state.isAir && (!state.`is`(BlockTags.DRAGON_IMMUNE))) {
                        blocks.add(pos to state)
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
