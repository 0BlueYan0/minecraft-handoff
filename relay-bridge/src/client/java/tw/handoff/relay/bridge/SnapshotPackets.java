package tw.handoff.relay.bridge;

import java.util.Set;
import java.util.Optional;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.GameType;

final class SnapshotPackets {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
    static void send(HostBridge host,NativePeer peer)throws ReflectiveOperationException{
        var mc=host.minecraft();var player=mc.player;var level=mc.level;
        if(player==null||level==null)throw new IllegalStateException("Host world changed");
        int radius=4;
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
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(System.nanoTime()-start>1_000_000_000L)throw new IllegalStateException("Snapshot exceeded time budget");
            var chunk=level.getChunkSource().getChunk(player.chunkPosition().x+x,player.chunkPosition().z+z,ChunkStatus.FULL,false);
            if(chunk!=null){peer.send(new ClientboundLevelChunkWithLightPacket(chunk,level.getChunkSource().getLightEngine(),null,null));count++;}
        }
        peer.send(new ClientboundChunkBatchFinishedPacket(count));
        if(count==0)throw new IllegalStateException("Host has no visible chunks");
        EntitySnapshot.entities(mc,peer);
    }
}
