package tw.handoff.relay.bridge;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagNetworkSerialization;

final class RegistryTags {
    static ClientboundUpdateTagsPacket packet(RegistryAccess access){
        Map<ResourceKey<? extends Registry<?>>,TagNetworkSerialization.NetworkPayload> all=new LinkedHashMap<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries().forEach(entry->all.put(entry.key(),payload(entry.value())));
        access.registries().forEach(entry->all.put(entry.key(),payload(entry.value())));
        return new ClientboundUpdateTagsPacket(all);
    }
    private static <T> TagNetworkSerialization.NetworkPayload payload(Registry<T> registry){
        Map<ResourceLocation,IntList> tags=new LinkedHashMap<>();
        registry.getTags().forEach(tag->{IntList ids=new IntArrayList();for(var holder:tag)ids.add(registry.getId(holder.value()));tags.put(tag.key().location(),ids);});
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{buffer.writeMap(tags,FriendlyByteBuf::writeResourceLocation,FriendlyByteBuf::writeIntIdList);return TagNetworkSerialization.NetworkPayload.read(buffer);}
        finally{buffer.release();}
    }
}
