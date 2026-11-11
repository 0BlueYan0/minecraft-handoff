package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OpaqueFramesTest {
 @Test void unknownChannelAndArbitraryBinaryRemainByteExact(){byte[] bytes=OpaqueFrames.payload(145,"unfamiliar_mod:skill_v9",new byte[]{0,-1,3,0,127});var v=OpaqueFrames.inspect(bytes);assertEquals(145,v.packetId());assertEquals("unfamiliar_mod:skill_v9",v.channel());assertArrayEquals(new byte[]{0,-1,3,0,127},java.util.Arrays.copyOfRange(bytes,v.dataOffset(),bytes.length));}
 @Test void malformedLengthOrIdentifierIsRejected(){assertThrows(IllegalArgumentException.class,()->OpaqueFrames.inspect(new byte[]{1,10,65}));assertThrows(IllegalArgumentException.class,()->OpaqueFrames.inspect(new byte[]{-128,-128,-128,-128,-128,1}));}
 @Test void connectionChannelsAreNeverGameplayHistory(){assertFalse(OpaqueFrames.gameplayChannel("launcher:validation"));assertFalse(OpaqueFrames.gameplayChannel("minecraft:brand"));assertFalse(OpaqueFrames.gameplayChannel("fabric:register"));assertTrue(OpaqueFrames.gameplayChannel("unknown_mod:any_action"));}
}
