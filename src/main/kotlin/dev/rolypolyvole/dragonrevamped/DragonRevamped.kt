package dev.rolypolyvole.dragonrevamped

import dev.rolypolyvole.dragonrevamped.worldgen.CustomEndSpikes
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.status.ChunkStatus


class DragonRevamped : DedicatedServerModInitializer {

    companion object {
        const val MOD_ID = "dragon-revamped"
    }

    override fun onInitializeServer() {
        println("Dragons Revamped has been initialized!")

        ServerLifecycleEvents.SERVER_STARTED.register(this::createCustomEndSpikes)
    }

    private fun createCustomEndSpikes(server: MinecraftServer) {
        val end = server.getLevel(Level.END)!!

        val firstGeneration = end.dragonFight!!.saveData().needsStateScanning
        if (!firstGeneration) return

        for (x in -8..8) {
            for (z in -8..8) {
                end.getChunk(x, z, ChunkStatus.FULL, true)
            }
        }

        CustomEndSpikes.regenerateSpikes(end)
    }
}
