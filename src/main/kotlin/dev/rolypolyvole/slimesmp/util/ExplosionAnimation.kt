package dev.rolypolyvole.slimesmp.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object ExplosionAnimation {

    fun play(level: ServerLevel, blocks: List<Pair<BlockPos, BlockState>>, scalar: Double) {
        val solid = blocks.filter { (_, state) -> !state.isAir && state.isSolid }
        val selected = solid.shuffled().take(15)

        for ((pos, state) in selected) {
            val entity = FallingBlockEntity.fall(level, pos, state)

            val x = -1.0 + level.random.nextDouble() * 2.0
            val y = 0.5 + level.random.nextDouble() * 1.5
            val z = -1.0 + level.random.nextDouble() * 2.0
            val velocity = Vec3(x, y, z).normalize().scale(scalar)

            entity.deltaMovement = velocity
            entity.dropItem = false
            entity.time = 1
            entity.disableDrop()

            level.addFreshEntity(entity)
        }
    }
}
