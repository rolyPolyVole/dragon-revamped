package dev.rolypolyvole.slimesmp.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

fun BlockPos.highestBlockY(world: ServerLevel): BlockPos {
    val x = x
    val z = z
    val y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, BlockPos(Vec3i(x, 0, z)))
    return BlockPos(this.x, y, this.z)
}

fun BlockPos.toVec3(): Vec3 {
    return Vec3(x.toDouble(), y.toDouble(), z.toDouble())
}

fun Vec3.xz(): Vec3 {
    return Vec3(x, 0.0, z)
}

@Suppress("DEPRECATION")
fun Entity.standingBlockPos(): BlockPos {
    val pos = blockPosition()
    if (level().getBlockState(pos.below()).blocksMotion()) return pos

    return listOf(
        pos.north(), pos.south(), pos.east(), pos.west(),
        pos.north().east(), pos.north().west(),
        pos.south().east(), pos.south().west()
    ).firstOrNull { level().getBlockState(it.below()).blocksMotion() } ?: pos
}