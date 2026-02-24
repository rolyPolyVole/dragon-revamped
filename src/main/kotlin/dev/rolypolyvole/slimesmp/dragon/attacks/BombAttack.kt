package dev.rolypolyvole.slimesmp.dragon.attacks

import dev.rolypolyvole.slimesmp.dragon.util.DragonBomb
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import net.minecraft.world.phys.Vec3
import kotlin.reflect.KClass

class BombAttack(dragon: EnderDragon) : AbstractDragonAttack(dragon) {
    private val random: Double; get() = Math.random()

    private var lifetime = (200..240).random()
    private var nextBomb = (12..30).random()

    object BombAttackType : DragonAttackType {
        override fun invalidPhases(): List<KClass<out DragonPhaseInstance>> = listOf(
            AbstractDragonSittingPhase::class,
            DragonLandingApproachPhase::class,
            DragonLandingPhase::class,
            DragonDeathPhase::class,
        )

        override fun canStart(dragon: EnderDragon, lastAttack: AbstractDragonAttack?): Boolean {
            return super.canStart(dragon, lastAttack) && lastAttack !is BombAttack
        }

        override fun create(dragon: EnderDragon): AbstractDragonAttack {
            return BombAttack(dragon)
        }
    }

    override fun tick() {
        if (this.lifetime-- <= 0) return

        if (nextBomb-- == 0) {
            if (!level.getBlockState(dragon.blockPosition()).isAir) {
                this.nextBomb = 10
                return
            }

            DragonBomb(level, dragon).let {
                it.setVelocity(Vec3(random * 0.15 - 0.15, -random * 0.8, random * 0.15 - 0.15))

                level.addFreshEntity(it)
            }

            this.nextBomb = (12..30).random()
        }
    }

    override fun start(): Boolean {
        broadcastSound(SoundEvents.ENDER_DRAGON_GROWL)
        return true
    }

    override fun end() {
    }

    override fun getSpeedMultiplier(): Float = 1.0F
    override fun getTurnSpeedMultiplier(): Float = 1.0F

    override fun shouldEnd(): Boolean = lifetime <= 0
}
