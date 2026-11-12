package tw.handoff.relay.bridge;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The attaching agent asks for generic mode without setting handoff.generic in the game JVM. */
class AgentGenericModeTest {
    @BeforeEach @AfterEach void clear(){System.clearProperty("handoff.generic");System.clearProperty("handoff.packetLab");}
    private static void marker(Path dir)throws Exception{Files.writeString(dir.resolve(".handoff-viewer"),"MinecraftHandoffViewer:1");}
    @Test void agentOffWithoutJvmFlagIsOff(@TempDir Path dir)throws Exception{assertNull(PacketLab.mode(dir,false));}
    @Test void agentGenericAloneIsProductionWithoutMarker(@TempDir Path dir)throws Exception{assertEquals(PacketLab.Mode.PRODUCTION,PacketLab.mode(dir,true));}
    @Test void agentDoesNotChangeTheJvmProperties(@TempDir Path dir)throws Exception{
        PacketLab.mode(dir,true);
        assertNull(System.getProperty("handoff.generic"));assertNull(System.getProperty("handoff.packetLab"));
    }
    @Test void jvmGenericStillWorksWhateverTheAgentAsks(@TempDir Path dir)throws Exception{
        System.setProperty("handoff.generic","true");
        assertEquals(PacketLab.Mode.PRODUCTION,PacketLab.mode(dir,false));assertEquals(PacketLab.Mode.PRODUCTION,PacketLab.mode(dir,true));
    }
    @Test void jvmFixtureWithAgentOffKeepsFixture(@TempDir Path dir)throws Exception{
        System.setProperty("handoff.packetLab","true");marker(dir);
        assertEquals(PacketLab.Mode.FIXTURE,PacketLab.mode(dir,false));
    }
    @Test void agentGenericWithJvmFixtureIsRefused(@TempDir Path dir)throws Exception{
        System.setProperty("handoff.packetLab","true");marker(dir);
        assertThrows(IllegalStateException.class,()->PacketLab.mode(dir,true));
    }
    @Test void oldSignatureKeepsItsMeaning(@TempDir Path dir)throws Exception{
        assertNull(PacketLab.mode(dir));
        System.setProperty("handoff.generic","true");assertEquals(PacketLab.Mode.PRODUCTION,PacketLab.mode(dir));
    }
}
