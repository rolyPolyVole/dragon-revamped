package dev.rolypolyvole.slimesmp.dragon

import dev.rolypolyvole.slimesmp.dragon.attacks.*
import net.minecraft.world.entity.boss.enderdragon.EnderDragon

class DragonAttackManager(private val dragon: EnderDragon) {
    private val attacks = listOf(
        List(12) { ChargeAttack.ChargeAttackType },
        List(5) { FireballAttack.FireballAttackType },
        List(4) { LightningAttack.LightningAttackType },
        List(2) { EnragedChargeAttack.EnragedChargeAttackType }
    ).flatten()

    private var currentAttack: AbstractDragonAttack? = null
    private var lastAttack: AbstractDragonAttack? = null

    private var ticksUntilNextAttack = 200

    val speedMultiplier: Float
        get() = currentAttack?.getSpeedMultiplier() ?: 1.0F
    val turnSpeedMultiplier: Float
        get() = currentAttack?.getTurnSpeedMultiplier() ?: 1.0F

    fun tick() {
        currentAttack?.let {
            it.tick()

            if (it.shouldEnd()) {
                it.end()

                this.lastAttack = it
                this.currentAttack = null
                this.ticksUntilNextAttack = (80..200).random()
            }

            return
        }

        if (ticksUntilNextAttack > 0) {
            this.ticksUntilNextAttack--
            return
        }

        val picked = attacks
            .filter { it.canStart(dragon, lastAttack) }
            .randomOrNull()?.create(dragon)

        if (picked != null && picked.start()) {
            this.currentAttack = picked
        } else {
            this.ticksUntilNextAttack = 20
        }
    }

    fun onBeforeMove() {
        currentAttack?.beforeMove()
    }
}