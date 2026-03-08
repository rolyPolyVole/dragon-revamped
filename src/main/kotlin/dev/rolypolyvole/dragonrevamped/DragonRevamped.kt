package dev.rolypolyvole.dragonrevamped

import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents
import net.minecraft.world.level.Level


class DragonRevamped : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        println("Dragon Revamped has been initialized!")

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register { _, _, destination ->
            if (destination.dimension() == Level.END) {
                CustomEndSpikes.regenerateSpikes(destination)
            }
        }
    }

    companion object {
        const val MOD_ID = "dragon-revamped"
    }
}
