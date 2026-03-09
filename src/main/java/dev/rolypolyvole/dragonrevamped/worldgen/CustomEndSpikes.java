package dev.rolypolyvole.dragonrevamped.worldgen;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;


// heavily vibe coded
// needs desperate format code

public final class CustomEndSpikes {
    private CustomEndSpikes() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("dragon-revamped");

    private static final float MAX_LEAN_PER_Y = 0.35f;
    private static final float TAPER_EXPONENT = 0.97f;
    private static final int TIP_STRUCTURE_HEIGHT = 6;

    public static final Set<Vec3> CRYSTAL_LOCATIONS = new HashSet<>();

    /**
     * Returns cached crystal locations, computing from spike cache data if empty.
     * Crystal positions are deterministic from the cache — no world scanning needed.
     */
    public static Set<Vec3> getCrystalLocations(ServerLevel level) {
        if (!CRYSTAL_LOCATIONS.isEmpty()) {
            return CRYSTAL_LOCATIONS;
        }

        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) {
            CRYSTAL_LOCATIONS.add(new Vec3(
                    spike.getCenterX() + 0.5,
                    spike.getHeight(),
                    spike.getCenterZ() + 0.5
            ));
        }

        LOGGER.info("Crystal locations loaded: {}", CRYSTAL_LOCATIONS.size());

        return CRYSTAL_LOCATIONS;
    }

    // ── Spike placement ─────────────────────────────────────────────────

    public static void place(ServerLevelAccessor level, RandomSource ignored, SpikeConfiguration cfg, SpikeFeature.EndSpike spike) {
        if (level instanceof WorldGenRegion) {
            return;
        }

        placeSpike(level, spike, true);
    }

    /**
     * Places all spikes if initial generation flagged them for deferred placement.
     * Called on the first End world tick after chunk generation completes.
     */
    public static void regenerateSpikes(ServerLevel level) {
        for (SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(level)) {
            placeSpike(level, spike, true);
        }

        LOGGER.info("Regenerated all End spikes");
    }

    private static void placeSpike(ServerLevelAccessor level, SpikeFeature.EndSpike spike, boolean crystals) {
        RandomSource sr = spikeRandom(level, spike);

        int tipX = spike.getCenterX();
        int tipZ = spike.getCenterZ();
        int crystalBlockY = spike.getHeight();
        int tipBaseY = crystalBlockY - TIP_STRUCTURE_HEIGHT;

        float[] lean = outwardLean(tipX, tipZ, MAX_LEAN_PER_Y, sr);
        float leanX = lean[0];
        float leanZ = lean[1];

        // Deterministic startY — no terrain scanning, so spikes are identical on respawn
        int startY = Math.max(level.getMinY(), crystalBlockY - TIP_STRUCTURE_HEIGHT - 80);

        int bodyHeight = Math.max(1, tipBaseY - startY);
        int baseRadius = Mth.clamp(bodyHeight / 10 + 2, 4, 9);

        // Base center at startY (shifted from tip by lean)
        float baseCxf = tipX - (tipBaseY - startY) * leanX;
        float baseCzf = tipZ - (tipBaseY - startY) * leanZ;

        placeIsland(level, baseCxf, baseCzf, startY, baseRadius + 2);
        placeSpire(level, sr, tipX, tipZ, startY, tipBaseY, baseRadius, leanX, leanZ);
        placeTipStructure(level, tipX, tipBaseY, tipZ);

        if (spike.isGuarded()) {
            placeCage(level, tipX, crystalBlockY, tipZ, 4);
        }

        if (crystals) {
            spawnCrystal(level, sr, tipX, crystalBlockY, tipZ);
        }
    }

    // ── Base island ────────────────────────────────────────────────────

    private static void placeIsland(ServerLevelAccessor level, float cx, float cz, int topY, int topRadius) {
        MutableBlockPos pos = new MutableBlockPos();
        int depth = topRadius;

        for (int dy = 0; dy <= depth; dy++) {
            int y = topY - dy;
            float t = dy / (float) depth;
            // Hemisphere profile: radius shrinks as we go down
            float r = topRadius * (float) Math.sqrt(1.0f - t * t);
            if (r < 0.5f) break;

            float r2 = r * r;
            int minX = (int) Math.floor(cx - r - 1);
            int maxX = (int) Math.ceil(cx + r + 1);
            int minZ = (int) Math.floor(cz - r - 1);
            int maxZ = (int) Math.ceil(cz + r + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    float dx = (x + 0.5f) - cx;
                    float dz = (z + 0.5f) - cz;
                    if (dx * dx + dz * dz <= r2) {
                        pos.set(x, y, z);
                        level.setBlock(pos, Blocks.END_STONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // ── Spike body ──────────────────────────────────────────────────────

    private static void placeSpire(
            ServerLevelAccessor level,
            RandomSource sr,
            int tipX,
            int tipZ,
            int startY,
            int tipBaseY,
            int baseRadius,
            float leanX,
            float leanZ
    ) {
        MutableBlockPos pos = new MutableBlockPos();
        int bodyHeight = Math.max(1, tipBaseY - startY);

        for (int y = startY; y <= tipBaseY; y++) {
            float t = (y - startY) / (float) bodyHeight;

            // Lean: tip stays at (tipX, tipZ), base shifts inward toward origin
            float cxf = tipX - (tipBaseY - y) * leanX;
            float czf = tipZ - (tipBaseY - y) * leanZ;

            int spineX = Mth.floor(cxf);
            int spineZ = Mth.floor(czf);

            // Taper: gradually narrows from baseRadius at bottom to single-block at top
            float r = 0.5f + (baseRadius - 0.5f) * (float) Math.pow(1.0f - t, TAPER_EXPONENT);

            // Always place the spine (center) block
            pos.set(spineX, y, spineZ);
            level.setBlock(pos, pickSpikeBlock(sr, y, tipBaseY), 2);

            // Place cylindrical shell when wider than 1 block
            if (r > 1.0f) {
                int minX = (int) Math.floor(cxf - r - 1);
                int maxX = (int) Math.ceil(cxf + r + 1);
                int minZ = (int) Math.floor(czf - r - 1);
                int maxZ = (int) Math.ceil(czf + r + 1);

                float r2 = r * r;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        float dx = (x + 0.5f) - cxf;
                        float dz = (z + 0.5f) - czf;
                        if (dx * dx + dz * dz <= r2) {
                            pos.set(x, y, z);
                            level.setBlock(pos, pickSpikeBlock(sr, y, tipBaseY), 2);
                        }
                    }
                }
            }
        }
    }

    // ── Tip structure ───────────────────────────────────────────────────

    private static void placeTipStructure(ServerLevelAccessor level, int x, int tipBaseY, int z) {
        MutableBlockPos pos = new MutableBlockPos();

        // Center column
        setBlock(level, pos, x, tipBaseY + 1, z, Blocks.OBSIDIAN.defaultBlockState());
        setBlock(level, pos, x, tipBaseY + 2, z, Blocks.OBSIDIAN.defaultBlockState());
        setBlock(level, pos, x, tipBaseY + 3, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        setBlock(level, pos, x, tipBaseY + 4, z, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
        setBlock(level, pos, x, tipBaseY + 5, z, Blocks.BEDROCK.defaultBlockState());
        setBlock(level, pos, x, tipBaseY + 6, z, Blocks.SOUL_FIRE.defaultBlockState());

        // Cardinal sides of reinforced deepslate: upside-down stairs + candles above
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            int sx = x + dir.getStepX();
            int sz = z + dir.getStepZ();

            BlockState stair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, dir.getOpposite())
                    .setValue(BlockStateProperties.HALF, Half.TOP);
            setBlock(level, pos, sx, tipBaseY + 4, sz, stair);

            BlockState candle = Blocks.BLACK_CANDLE.defaultBlockState()
                    .setValue(BlockStateProperties.LIT, false)
                    .setValue(BlockStateProperties.CANDLES, 4);
            setBlock(level, pos, sx, tipBaseY + 5, sz, candle);
        }

        // Diagonal corners: upper slab + lower slab above
        int[][] diagonals = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : diagonals) {
            int dx = x + d[0];
            int dz = z + d[1];

            BlockState upperSlab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
            setBlock(level, pos, dx, tipBaseY + 4, dz, upperSlab);

            BlockState lowerSlab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
            setBlock(level, pos, dx, tipBaseY + 5, dz, lowerSlab);
        }
    }

    // ── Iron bar cage ────────────────────────────────────────────────────

    private static void placeCage(ServerLevelAccessor level, int cx, int cy, int cz, int radius) {
        MutableBlockPos pos = new MutableBlockPos();
        float innerR2 = (radius - 0.5f) * (radius - 0.5f);
        float outerR2 = (radius + 0.5f) * (radius + 0.5f);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    float dist2 = dx * dx + dy * dy + dz * dz;
                    if (dist2 >= innerR2 && dist2 <= outerR2) {
                        pos.set(cx + dx, cy + dy, cz + dz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    // ── Crystal spawning ────────────────────────────────────────────────

    private static void spawnCrystal(ServerLevelAccessor level, RandomSource sr, int x, int crystalBlockY, int z) {
        EndCrystal crystal = EntityType.END_CRYSTAL.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (crystal == null) return;

        crystal.setBeamTarget(null);
        crystal.setInvulnerable(false);
        crystal.setShowBottom(false);
        crystal.snapTo(x + 0.5, crystalBlockY, z + 0.5, sr.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(crystal);

        CRYSTAL_LOCATIONS.add(crystal.position());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static RandomSource spikeRandom(ServerLevelAccessor level, SpikeFeature.EndSpike spike) {
        long worldSeed = level.getLevel().getSeed();
        long x = spike.getCenterX();
        long z = spike.getCenterZ();
        long h = worldSeed
                ^ (x * 341873128712L)
                ^ (z * 132897987541L)
                ^ (spike.isGuarded() ? 0x9E3779B97F4A7C15L : 0L);
        return RandomSource.create(h);
    }

    private static float[] outwardLean(int cx, int cz, float maxLeanPerY, RandomSource sr) {
        float len = Mth.sqrt((float)(cx * cx + cz * cz));
        float nx = len == 0 ? 0 : cx / len;
        float nz = len == 0 ? 0 : cz / len;

        float leanX = nx * maxLeanPerY;
        float leanZ = nz * maxLeanPerY;

        float wobble = (sr.nextFloat() - 0.5f) * (maxLeanPerY * 0.25f);
        leanX += -nz * wobble;
        leanZ +=  nx * wobble;

        return new float[]{leanX, leanZ};
    }

    private static BlockState pickSpikeBlock(RandomSource sr, int y, int topY) {
        if (sr.nextFloat() < 0.2f * y / topY) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return Blocks.OBSIDIAN.defaultBlockState();
    }

    private static void setBlock(ServerLevelAccessor level, MutableBlockPos pos, int x, int y, int z, BlockState state) {
        pos.set(x, y, z);
        level.setBlock(pos, state, 2);
    }
}
