package tw.handoff.relay.bridge;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.cookie.CookiePacketTypes;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorRulesTest {
    private record RenamedPacket(PacketType type) implements Packet {
        public void handle(PacketListener listener){}
    }
    @Test void controlMessagesAreFilteredByStableWireIdentityNotMappedClassName(){
        assertFalse(MirrorRules.forward(new RenamedPacket(CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE)));
        assertFalse(MirrorRules.forward(new RenamedPacket(CommonPacketTypes.CLIENTBOUND_TRANSFER)));
        assertFalse(MirrorRules.forward(new RenamedPacket(CookiePacketTypes.CLIENTBOUND_COOKIE_REQUEST)));
    }
    @Test void ordinaryWorldStateIsForwardable(){assertTrue(MirrorRules.forward(new ClientboundSetHealthPacket(10,12,2)));}
}
