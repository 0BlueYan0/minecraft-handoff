package tw.handoff.lab;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record LabPayload(byte[] data) implements CustomPacketPayload {
 public static final Type<LabPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("unfamiliar_test_mod","opaque_v9"));
 public static final StreamCodec<RegistryFriendlyByteBuf,LabPayload> CODEC=ByteBufCodecs.BYTE_ARRAY.map(LabPayload::new,LabPayload::data).cast();
 public Type<LabPayload> type(){return TYPE;}
}
