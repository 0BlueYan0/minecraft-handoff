package tw.handoff.relay.bridge;

import java.util.Set;
import java.util.Optional;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.GameType;

final class SnapshotPackets {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
    /** How far out the snapshot looks for chunks the host holds; the client chunk cache never holds more than 32 plus 3. */
    static final int SCAN=35;
    static int radius(int renderDistance){return Math.max(2,Math.min(32,renderDistance));}
    /** The radius the viewer is told: wide enough that its chunk cache keeps every chunk the host holds. */
    static int radius(int renderDistance,int farthestHeld){return radius(Math.max(renderDistance,farthestHeld));}
    /** Host-held chunk offsets within SCAN, nearest first, so a time-budget cut drops the farthest. */
    static java.util.List<int[]> held(java.util.function.BiPredicate<Integer,Integer> holds){
        var found=new java.util.ArrayList<int[]>();
        for(int x=-SCAN;x<=SCAN;x++)for(int z=-SCAN;z<=SCAN;z++)if(holds.test(x,z))found.add(new int[]{x,z});
        found.sort(java.util.Comparator.comparingInt(o->Math.max(Math.abs(o[0]),Math.abs(o[1]))));
        return found;
    }
    static void send(HostBridge host,NativePeer peer)throws ReflectiveOperationException{
        var mc=host.minecraft();var player=mc.player;var level=mc.level;
        if(player==null||level==null)throw new IllegalStateException("Host world changed");
        int px=player.chunkPosition().x,pz=player.chunkPosition().z;
        // The server tracks a wider square than the host's render distance (2026-11-13: 17 new chunks per step, so radius 8 against a render distance of 6)
        // and never sends those chunks again. Every chunk the host holds goes to the viewer, or that ring stays empty when the viewer walks into it.
        var held=held((x,z)->level.getChunkSource().getChunk(px+x,pz+z,ChunkStatus.FULL,false)!=null);
        int farthest=held.isEmpty()?0:Math.max(Math.abs(held.getLast()[0]),Math.abs(held.getLast()[1]));
        int radius=radius(mc.options.getEffectiveRenderDistance(),farthest);
        CommonPlayerSpawnInfo spawn=new CommonPlayerSpawnInfo(level.dimensionTypeRegistration(),level.dimension(),0,
            mc.gameMode.getPlayerMode(),mc.gameMode.getPreviousPlayerMode(),level.isDebug(),level.getLevelData().getHorizonHeight(level)==level.getMinY(),player.getLastDeathLocation(),player.getPortalCooldown(),level.getSeaLevel());
        peer.send(new ClientboundLoginPacket(player.getId(),level.getLevelData().isHardcore(),mc.getConnection().levels(),1,radius,radius,false,true,false,spawn,false));
        EntitySnapshot.players(mc,peer);
        peer.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START,0));
        peer.send(new ClientboundSetChunkCacheRadiusPacket(radius));
        peer.send(new ClientboundSetChunkCacheCenterPacket(player.chunkPosition().x,player.chunkPosition().z));
        peer.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        peer.send(new ClientboundSetHealthPacket(player.getHealth(),player.getFoodData().getFoodLevel(),player.getFoodData().getSaturationLevel()));
        peer.send(new ClientboundSetExperiencePacket(player.experienceProgress,player.totalExperience,player.experienceLevel));
        peer.send(new ClientboundSetHeldSlotPacket(player.getInventory().selected));
        peer.send(new ClientboundContainerSetContentPacket(0,player.inventoryMenu.getStateId(),player.inventoryMenu.getItems(),player.inventoryMenu.getCarried()));
        peer.send(ClientboundPlayerPositionPacket.of(1,PositionMoveRotation.of(player),Set.of()));
        peer.send(new ClientboundSetTimePacket(level.getGameTime(),level.getDayTime(),true));
        peer.send(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(),level.getSharedSpawnAngle()));
        var commands=CommandTreeFilter.copy(mc.getConnection().getCommands().getRoot(),CommandTreeFilter::serializable);
        if(!commands.dropped().isEmpty())LOG.info("Viewer command tree leaves out {} client-only commands: {}",commands.dropped().size(),commands.dropped());
        peer.send(new ClientboundCommandsPacket(commands.root()));
        peer.send(ClientboundChunkBatchStartPacket.INSTANCE);
        int count=0;long start=System.nanoTime();
        for(int[] offset:held){
            // Past the budget, send what is built instead of failing the join; the missing ones stay empty until the player moves.
            if(System.nanoTime()-start>3_000_000_000L){if(count>0)break;throw new IllegalStateException("Snapshot exceeded time budget");}
            var chunk=level.getChunkSource().getChunk(px+offset[0],pz+offset[1],ChunkStatus.FULL,false);
            if(chunk!=null){peer.send(new ClientboundLevelChunkWithLightPacket(chunk,level.getChunkSource().getLightEngine(),null,null));count++;}
        }
        LOG.info("Snapshot sent {} of {} host chunks, radius {}",count,held.size(),radius);
        peer.send(new ClientboundChunkBatchFinishedPacket(count));
        if(count==0)throw new IllegalStateException("Host has no visible chunks");
        EntitySnapshot.entities(mc,peer);
    }
}
