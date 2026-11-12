package tw.handoff.lab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LabPayload(byte[] data) implements CustomPacketPayload {
 public static final Type<LabPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("unfamiliar_test_mod","opaque_v9"));
 public static final StreamCodec<RegistryFriendlyByteBuf,LabPayload> CODEC=ByteBufCodecs.BYTE_ARRAY.map(LabPayload::new,LabPayload::data).cast();
 public Type<LabPayload> type(){return TYPE;}

 /** A payload whose wire form is the bytes themselves, with no length prefix, for channels the fixture imitates (SAO's mux, quest HUD, blessing). The bytes are invented. */
 public record Raw(Type<Raw> type,byte[] data) implements CustomPacketPayload {
  public static Type<Raw> channel(String namespace,String path){return new Type<>(ResourceLocation.fromNamespaceAndPath(namespace,path));}
  public static StreamCodec<RegistryFriendlyByteBuf,Raw> codec(Type<Raw> type){
   return StreamCodec.of((buffer,value)->buffer.writeBytes(value.data()),buffer->{byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);return new Raw(type,bytes);});
  }
 }
 /** SAOUI registers saocore:mux and capuodev:quest_hud itself, so only the private backend registers these two. saoblessing is not loaded in the fixtures, so every side registers it. */
 public static final Type<Raw> MUX=Raw.channel("saocore","mux"),QUEST=Raw.channel("capuodev","quest_hud"),BLESSING=Raw.channel("saoblessing","main");
}
