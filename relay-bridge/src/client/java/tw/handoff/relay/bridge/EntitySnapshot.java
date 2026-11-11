package tw.handoff.relay.bridge;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;

final class EntitySnapshot {
    static void players(Minecraft mc,NativePeer peer)throws ReflectiveOperationException{
        var listed=new HashSet<java.util.UUID>();
        for(PlayerInfo info:mc.getConnection().getListedOnlinePlayers())listed.add(info.getProfile().getId());
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries=new ArrayList<>();
        for(PlayerInfo info:mc.getConnection().getOnlinePlayers())entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
            info.getProfile().getId(),info.getProfile(),listed.contains(info.getProfile().getId()),info.getLatency(),info.getGameMode(),info.getTabListDisplayName(),
            info.showHat(),info.getTabListOrder(),info.getChatSession()==null?null:info.getChatSession().asData()));
        var packet=new ClientboundPlayerInfoUpdatePacket(EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class),List.of());
        boolean set=false;
        for(Field field:ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields())if(field.getType()==List.class){field.setAccessible(true);field.set(packet,List.copyOf(entries));set=true;break;}
        if(!set)throw new IllegalStateException("Player information packet contract changed");
        peer.send(packet);
    }
    static void entities(Minecraft mc,NativePeer peer){
        for(Entity entity:mc.level.entitiesForRendering()){
            if(entity==mc.player)continue;
            int data=entity instanceof HangingEntity hanging?hanging.getDirection().get3DDataValue():entity instanceof FallingBlockEntity falling?Block.getId(falling.getBlockState()):0;
            peer.send(new ClientboundAddEntityPacket(entity.getId(),entity.getUUID(),entity.getX(),entity.getY(),entity.getZ(),entity.getXRot(),entity.getYRot(),entity.getType(),data,entity.getDeltaMovement(),entity.getYHeadRot()));
            metadata(entity,peer);
            if(entity instanceof LivingEntity living){
                peer.send(new ClientboundUpdateAttributesPacket(entity.getId(),living.getAttributes().getSyncableAttributes()));
                List<Pair<EquipmentSlot,net.minecraft.world.item.ItemStack>> equipment=new ArrayList<>();
                for(EquipmentSlot slot:EquipmentSlot.values())if(!living.getItemBySlot(slot).isEmpty())equipment.add(Pair.of(slot,living.getItemBySlot(slot).copy()));
                if(!equipment.isEmpty())peer.send(new ClientboundSetEquipmentPacket(entity.getId(),equipment));
                for(var effect:living.getActiveEffects())peer.send(new ClientboundUpdateMobEffectPacket(entity.getId(),effect,false));
            }
        }
        metadata(mc.player,peer);
        peer.send(new ClientboundUpdateAttributesPacket(mc.player.getId(),mc.player.getAttributes().getSyncableAttributes()));
        for(var effect:mc.player.getActiveEffects())peer.send(new ClientboundUpdateMobEffectPacket(mc.player.getId(),effect,false));
        for(Entity entity:mc.level.entitiesForRendering())if(!entity.getPassengers().isEmpty())peer.send(new ClientboundSetPassengersPacket(entity));
        for(var team:mc.level.getScoreboard().getPlayerTeams())peer.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team,true));
    }
    private static void metadata(Entity entity,NativePeer peer){
        var data=entity.getEntityData().getNonDefaultValues();
        if(data!=null&&!data.isEmpty())peer.send(new ClientboundSetEntityDataPacket(entity.getId(),data));
    }
}
