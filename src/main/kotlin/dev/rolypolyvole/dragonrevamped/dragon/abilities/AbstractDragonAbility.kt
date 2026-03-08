package dev.rolypolyvole.dragonrevamped.dragon.abilities

import dev.rolypolyvole.dragonrevamped.dragon.DragonAbilityManager
import net.minecraft.world.entity.boss.enderdragon.EnderDragon

abstract class AbstractDragonAbility(
    protected val dragon: EnderDragon,
    protected val manager: DragonAbilityManager
) {
    abstract fun tick()

    open fun isActive(): Boolean = false

    open fun onBeforeMove() {}
}