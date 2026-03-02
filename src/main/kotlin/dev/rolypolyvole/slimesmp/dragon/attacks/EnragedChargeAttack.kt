package dev.rolypolyvole.slimesmp.dragon.attacks

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.*
import kotlin.reflect.KClass

class EnragedChargeAttack(dragon: EnderDragon) : AbstractDragonAttack(dragon) {

    object EnragedChargeAttackType : DragonAttackType {
        override fun invalidPhases(): List<KClass<out DragonPhaseInstance>> = listOf(
            DragonChargePlayerPhase::class,
            DragonLandingApproachPhase::class,
            DragonLandingPhase::class,
            DragonDeathPhase::class,
            AbstractDragonSittingPhase::class
        )

        override fun canStart(dragon: EnderDragon, lastAttack: AbstractDragonAttack?): Boolean {
            return super.canStart(dragon, lastAttack) && lastAttack !is EnragedChargeAttack
        }

        override fun create(dragon: EnderDragon): AbstractDragonAttack {
            return EnragedChargeAttack(dragon)
        }
    }

    private var roarTicks = 0

    private var chargesRemaining = 0
    private var currentCharge: ChargeAttack? = null
    private var shouldEnd = false

    override fun start(): Boolean {
        val playerCount = nearbyPlayers().size
        this.chargesRemaining = 4.coerceAtLeast(playerCount)

        return startNextCharge()
    }

    private fun startNextCharge(): Boolean {
        if (chargesRemaining <= 0) {
            this.shouldEnd = true
            return false
        }

        val charge = ChargeAttack(dragon)

        if (!charge.start()) {
            this.shouldEnd = true
            return false
        }

        charge.speedMultiplier = 1.3

        this.currentCharge = charge
        this.chargesRemaining--
        this.roarTicks = 0
        return true
    }

    override fun tick() {
        if (roarTicks++ <= 10) {
            broadcastSound(SoundEvents.ENDER_DRAGON_GROWL, volume = 2.0F, pitch = 0.5F + roarTicks * 0.2F)
        }

        val charge = currentCharge ?: run { this.shouldEnd = true; return }

        charge.tick()

        if (charge.shouldEnd()) {
            charge.end()

            if (chargesRemaining > 0) {
                startNextCharge()
            } else {
                this.shouldEnd = true
            }
        }
    }

    override fun beforeMove() {
        currentCharge?.beforeMove()
    }

    override fun end() {
        currentCharge?.end()
        restoreNormalPhase()
    }

    override fun getSpeedMultiplier(): Float = currentCharge?.getSpeedMultiplier() ?: 1.0f
    override fun getTurnSpeedMultiplier(): Float = currentCharge?.getTurnSpeedMultiplier() ?: 1.0f

    override fun shouldEnd(): Boolean = shouldEnd
}
