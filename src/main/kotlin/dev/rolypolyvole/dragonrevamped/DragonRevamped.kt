package dev.rolypolyvole.dragonrevamped

import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.world.level.Level


class DragonRevamped : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        println("Dragon Revamped has been initialized!")

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val end = server.getLevel(Level.END) ?: return@register
            CustomEndSpikes.regenerateSpikes(end)
        }
    }

    companion object {
        const val MOD_ID = "dragon-revamped"
    }
}
