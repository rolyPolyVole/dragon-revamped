package dev.rolypolyvole.slimesmp.dragon

import dev.rolypolyvole.slimesmp.worldgen.CustomEndSpikes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3


class DragonAbilityManager(private val dragon: EnderDragon) {
    private val level = dragon.level() as ServerLevel

    private var ticks = 0
    private var ticksUntilCrystalRespawn = 0

    fun tick () {
        this.ticks++

        if (ticksUntilCrystalRespawn > 0) {
            this.ticksUntilCrystalRespawn--
        } else {
            respawnCrystal()
            this.ticksUntilCrystalRespawn = (200..300).random() + (getTotalCrystals() * 10)
        }
    }

    private fun respawnCrystal() {
        val pos = CustomEndSpikes.CRYSTAL_LOCATIONS
            .filterNot(::doesCrystalExist)
            .randomOrNull() ?: return

        val crystal = EntityType.END_CRYSTAL.create(level, EntitySpawnReason.EVENT) ?: return

        crystal.setPos(pos)
        crystal.setShowBottom(true)
        crystal.beamTarget = null

        level.addFreshEntity(crystal)

        dragon.level().players().forEach {
            it.displayClientMessage(
                net.minecraft.network.chat.Component.literal("An end crystal has respawned!"),
                false
            )
        }
    }

    private fun doesCrystalExist(vec: Vec3): Boolean {
        val aabb = AABB(vec.add(-0.5, -0.5, -0.5), vec.add(0.5, 0.5, 0.5)).inflate(1.5)

        return !level.getEntitiesOfClass(EndCrystal::class.java, aabb).isEmpty()
    }

    private fun getTotalCrystals(): Int {
        return level.allEntities.filterIsInstance<EndCrystal>().size
    }
}