package carpet.forge.mixin;

import carpet.forge.CarpetSettings;
import carpet.forge.helper.TickSpeed;
import carpet.forge.interfaces.IWorld;
import carpet.forge.interfaces.IWorldServer;
import carpet.forge.logging.LoggerRegistry;
import carpet.forge.utils.CarpetProfiler;
import carpet.forge.utils.Messenger;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.village.VillageSiege;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Mixin(value = WorldServer.class, priority = 900)
public abstract class MixinWorldServer extends World implements IWorldServer {

    @Shadow public abstract boolean areAllPlayersAsleep();

    @Shadow protected abstract void wakeAllPlayers();

    @Shadow @Final private WorldEntitySpawner entitySpawner;

    @Shadow @Final public PlayerChunkMap playerChunkMap;

    @Shadow @Final protected VillageSiege villageSiege;

    @Shadow @Final private Teleporter worldTeleporter;

    @Shadow public List<Teleporter> customTeleporters;

    @Shadow protected abstract void sendQueuedBlockEvents();

    @Shadow protected abstract void playerCheckLight();

    @Shadow protected abstract BlockPos adjustPosToNearbyEntity(BlockPos pos);

    @Shadow @Final private TreeSet<NextTickListEntry> pendingTickListEntriesTreeSet;

    @Shadow @Final private Set<NextTickListEntry> pendingTickListEntriesHashSet;

    @Shadow @Final private List<NextTickListEntry> pendingTickListEntriesThisTick;

    protected MixinWorldServer(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
        super(saveHandlerIn, info, providerIn, profilerIn, client);
    }

    private boolean blockActionsProcessed;

    /**
     * @author DeadlyMC
     * @reason The change in code required extra indents.
     * Changes are highlighted with comments
     */
    @Overwrite
    public void tick() {
        super.tick();

        if (this.getWorldInfo().isHardcoreModeEnabled() && this.getDifficulty() != EnumDifficulty.HARD) {
            this.getWorldInfo().setDifficulty(EnumDifficulty.HARD);
        }

        this.provider.getBiomeProvider().cleanupCache();

        if (this.areAllPlayersAsleep()) {
            if (this.getGameRules().getBoolean("doDaylightCycle")) {
                long i = this.getWorldTime() + 24000L;
                this.setWorldTime(i - i % 24000L);
            }

            this.wakeAllPlayers();
        }

        String world_name = this.provider.getDimensionType().getName(); // [FCM] Code simplifier

        if (TickSpeed.process_entities) // [FCM] if statement around
        {
            this.profiler.startSection("mobSpawner");
            CarpetProfiler.start_section(world_name, "spawning");

            if (this.getGameRules().getBoolean("doMobSpawning") && this.worldInfo.getTerrainType() != WorldType.DEBUG_ALL_BLOCK_STATES) {
                this.entitySpawner.findChunksForSpawning((WorldServer) (Object) this, this.spawnHostileMobs, this.spawnPeacefulMobs, this.worldInfo.getWorldTotalTime() % 400L == 0L);
            }
            CarpetProfiler.end_current_section();
        }
        // [FCM] end

        this.profiler.endStartSection("chunkSource");
        this.chunkProvider.tick();
        int j = this.calculateSkylightSubtracted(1.0F);

        if (j != this.getSkylightSubtracted()) {
            this.setSkylightSubtracted(j);
        }

        if (TickSpeed.process_entities) // [FCM] if statement around
        {
            this.worldInfo.setWorldTotalTime(this.worldInfo.getWorldTotalTime() + 1L);

            if (this.getGameRules().getBoolean("doDaylightCycle")) {
                this.setWorldTime(this.getWorldTime() + 1L);
            }
            this.profiler.endStartSection("tickPending");

            CarpetProfiler.start_section(world_name, "blocks"); // [FCM] Carpet profiling start

            this.tickUpdates(false);

            CarpetProfiler.end_current_section(); // [FCM] Carpet profiling end
        } // [FCM] end

        CarpetProfiler.start_section(world_name, "blocks"); // [FCM] Carpet profiling start
        this.profiler.endStartSection("tickBlocks");
        this.updateBlocks();
        CarpetProfiler.end_current_section(); // [FCM] Carpet profiling end
        this.profiler.endStartSection("chunkMap");
        this.playerChunkMap.tick();

        if (TickSpeed.process_entities) // [FCM] if statement around
        {
            this.profiler.endStartSection("village");
            this.villageCollection.tick();
            this.villageSiege.tick();
            this.profiler.endStartSection("portalForcer");
            this.worldTeleporter.removeStalePortalLocations(this.getTotalWorldTime());
        } // [FCM] End

        for (Teleporter tele : customTeleporters) {
            tele.removeStalePortalLocations(getTotalWorldTime());
        }
        // [FCM] Newlight
        if (CarpetSettings.newLight) {
            this.profiler.endStartSection("lighting");
            ((IWorld) this).getLightingEngine().procLightUpdates();
        }
        // [FCM] End
        this.profiler.endSection();
        this.sendQueuedBlockEvents();
    }

    /**
     * @author DeadlyMC
     * @reason The change in code required 'continue' statements.
     * Changes are highlighted with comments
     */
    @Overwrite
    protected void updateBlocks() {
        this.playerCheckLight();

        if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            Iterator<Chunk> iterator1 = this.playerChunkMap.getChunkIterator();

            while (iterator1.hasNext()) {
                ((Chunk) iterator1.next()).onTick(false);
            }
        } else {
            int i = this.getGameRules().getInt("randomTickSpeed");
            boolean flag = this.isRaining();
            boolean flag1 = this.isThundering();
            this.profiler.startSection("pollingChunks");

            for (Iterator<Chunk> iterator = getPersistentChunkIterable(this.playerChunkMap.getChunkIterator()); iterator.hasNext(); this.profiler.endSection()) {
                this.profiler.startSection("getChunk");
                Chunk chunk = iterator.next();
                int j = chunk.x * 16;
                int k = chunk.z * 16;
                this.profiler.endStartSection("checkNextLight");
                chunk.enqueueRelightChecks();
                this.profiler.endStartSection("tickChunk");
                chunk.onTick(false);
                if (!TickSpeed.process_entities) { // [FCM] Skipping the rest of the block processing
                    this.profiler.endSection();
                    continue;
                }
                this.profiler.endStartSection("thunder");

                if (this.provider.canDoLightning(chunk) && flag && flag1 && this.rand.nextInt(100000) == 0) {
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    int l = this.updateLCG >> 2;
                    BlockPos blockpos = this.adjustPosToNearbyEntity(new BlockPos(j + (l & 15), 0, k + (l >> 8 & 15)));

                    if (this.isRainingAt(blockpos)) {
                        DifficultyInstance difficultyinstance = this.getDifficultyForLocation(blockpos);

                        if (this.getGameRules().getBoolean("doMobSpawning") && this.rand.nextDouble() < (double) difficultyinstance.getAdditionalDifficulty() * 0.01D) {
                            EntitySkeletonHorse entityskeletonhorse = new EntitySkeletonHorse(this);
                            entityskeletonhorse.setTrap(true);
                            entityskeletonhorse.setGrowingAge(0);
                            entityskeletonhorse.setPosition((double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ());
                            this.spawnEntity(entityskeletonhorse);
                            this.addWeatherEffect(new EntityLightningBolt(this, (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), true));
                        } else {
                            this.addWeatherEffect(new EntityLightningBolt(this, (double) blockpos.getX(), (double) blockpos.getY(), (double) blockpos.getZ(), false));
                        }
                    }
                }

                this.profiler.endStartSection("iceandsnow");

                if (this.provider.canDoRainSnowIce(chunk) && this.rand.nextInt(16) == 0) {
                    this.updateLCG = this.updateLCG * 3 + 1013904223;
                    int j2 = this.updateLCG >> 2;
                    BlockPos blockpos1 = this.getPrecipitationHeight(new BlockPos(j + (j2 & 15), 0, k + (j2 >> 8 & 15)));
                    BlockPos blockpos2 = blockpos1.down();

                    if (this.isAreaLoaded(blockpos2, 1)) // Forge: check area to avoid loading neighbors in unloaded chunks
                        if (this.canBlockFreezeNoWater(blockpos2)) {
                            this.setBlockState(blockpos2, Blocks.ICE.getDefaultState());
                        }

                    if (flag && this.canSnowAt(blockpos1, true)) {
                        this.setBlockState(blockpos1, Blocks.SNOW_LAYER.getDefaultState());
                    }

                    if (flag && this.getBiome(blockpos2).canRain()) {
                        this.getBlockState(blockpos2).getBlock().fillWithRain(this, blockpos2);
                    }
                }

                this.profiler.endStartSection("tickBlocks");

                if (i > 0) {
                    for (ExtendedBlockStorage extendedblockstorage : chunk.getBlockStorageArray()) {
                        if (extendedblockstorage != Chunk.NULL_BLOCK_STORAGE && extendedblockstorage.needsRandomTick()) {
                            for (int i1 = 0; i1 < i; ++i1) {
                                this.updateLCG = this.updateLCG * 3 + 1013904223;
                                int j1 = this.updateLCG >> 2;
                                int k1 = j1 & 15;
                                int l1 = j1 >> 8 & 15;
                                int i2 = j1 >> 16 & 15;
                                IBlockState iblockstate = extendedblockstorage.get(k1, i2, l1);
                                Block block = iblockstate.getBlock();
                                this.profiler.startSection("randomTick");

                                if (block.getTickRandomly()) {
                                    block.randomTick(this, new BlockPos(k1 + j, i2 + extendedblockstorage.getYLocation(), l1 + k), iblockstate, this.rand);
                                }

                                this.profiler.endSection();
                            }
                        }
                    }
                }
            }

            this.profiler.endSection();
        }
    }

    /**
     * @author DeadlyMC
     * @reason No injection point
     * FOR : TileTickLimit logger and rule
     */
    @Overwrite
    public boolean tickUpdates(boolean runAllPending) {
        if (this.worldInfo.getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES) {
            return false;
        } else {
            int i = this.pendingTickListEntriesTreeSet.size();

            if (i != this.pendingTickListEntriesHashSet.size()) {
                throw new IllegalStateException("TickNextTick list out of synch");
            } else {
                // [FCM] TileTickLimit - start
                int tileTickLimit = CarpetSettings.tileTickLimit;
                if (tileTickLimit >= 0 && i > tileTickLimit) {
                    if (LoggerRegistry.__tileTickLimit) {
                        final int fi = i;
                        LoggerRegistry.getLogger("tileTickLimit").log(() -> new ITextComponent[]{
                                        Messenger.s(null, String.format("Reached tile tick limit (%d > %d)", fi, tileTickLimit))
                                },
                                "NUMBER", i,
                                "LIMIT", tileTickLimit);
                    }
                    i = tileTickLimit;
                }
                // [FCM] TileTickLimit - End

                this.profiler.startSection("cleaning");

                for (int j = 0; j < i; ++j) {
                    NextTickListEntry nextticklistentry = this.pendingTickListEntriesTreeSet.first();

                    if (!runAllPending && nextticklistentry.scheduledTime > this.worldInfo.getWorldTotalTime()) {
                        break;
                    }

                    this.pendingTickListEntriesTreeSet.remove(nextticklistentry);
                    this.pendingTickListEntriesHashSet.remove(nextticklistentry);
                    this.pendingTickListEntriesThisTick.add(nextticklistentry);
                }

                this.profiler.endSection();
                this.profiler.startSection("ticking");
                Iterator<NextTickListEntry> iterator = this.pendingTickListEntriesThisTick.iterator();

                while (iterator.hasNext()) {
                    NextTickListEntry nextticklistentry1 = iterator.next();
                    iterator.remove();
                    //Keeping here as a note for future when it may be restored.
                    //boolean isForced = getPersistentChunks().containsKey(new ChunkPos(nextticklistentry.xCoord >> 4, nextticklistentry.zCoord >> 4));
                    //byte b0 = isForced ? 0 : 8;
                    int k = 0;

                    if (this.isAreaLoaded(nextticklistentry1.position.add(0, 0, 0), nextticklistentry1.position.add(0, 0, 0))) {
                        IBlockState iblockstate = this.getBlockState(nextticklistentry1.position);

                        if (iblockstate.getMaterial() != Material.AIR && Block.isEqualTo(iblockstate.getBlock(), nextticklistentry1.getBlock())) {
                            try {
                                iblockstate.getBlock().updateTick(this, nextticklistentry1.position, iblockstate, this.rand);
                            } catch (Throwable throwable) {
                                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception while ticking a block");
                                CrashReportCategory crashreportcategory = crashreport.makeCategory("Block being ticked");
                                CrashReportCategory.addBlockInfo(crashreportcategory, nextticklistentry1.position, iblockstate);
                                throw new ReportedException(crashreport);
                            }
                        }
                    } else {
                        this.scheduleUpdate(nextticklistentry1.position, nextticklistentry1.getBlock(), 0);
                    }
                }

                this.profiler.endSection();
                this.pendingTickListEntriesThisTick.clear();
                return !this.pendingTickListEntriesTreeSet.isEmpty();
            }
        }
    }

    // [FCM] Fix for pistonGhostBlocks breaking caterpillar engine - start
    @Inject(method = "tick", at = @At("HEAD"))
    private void resetBlockActionsProcessed(CallbackInfo ci) {
        this.blockActionsProcessed = false;
    }

    @Inject(method = "sendQueuedBlockEvents", at = @At("RETURN"))
    private void setBlockActionsProcessed(CallbackInfo ci) {
        this.blockActionsProcessed = true;
    }

    @Override
    public boolean haveBlockActionsProcessed() {
        return this.blockActionsProcessed;
    }
    // [FCM] Fix for pistonGhostBlocks breaking caterpillar engine - end
}
