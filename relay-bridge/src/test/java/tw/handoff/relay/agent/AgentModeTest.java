package tw.handoff.relay.agent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AgentModeTest {
    @BeforeEach @AfterEach void reset(){EntryAgent.resetForTest();}
    private static String enc(Path p){return Base64.getUrlEncoder().withoutPadding().encodeToString(p.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String base(Path dir)throws Exception{Path status=Files.createFile(dir.resolve("status"));return enc(status)+"."+enc(dir)+".0";}
    @Test void threeFieldsMeanOff(@TempDir Path dir)throws Exception{
        var o=EntryAgent.parse(base(dir));assertNotNull(o);assertFalse(o.generic());assertEquals(0,o.port());
    }
    @Test void fourthFieldSelectsMode(@TempDir Path dir)throws Exception{
        String b=base(dir);
        assertTrue(EntryAgent.parse(b+".g").generic());
        assertFalse(EntryAgent.parse(b+".-").generic());
    }
    @Test void unknownModeOrExtraFieldIsRefused(@TempDir Path dir)throws Exception{
        String b=base(dir);
        assertNull(EntryAgent.parse(b+".x"));assertNull(EntryAgent.parse(b+".G"));assertNull(EntryAgent.parse(b+".g.g"));assertNull(EntryAgent.parse(b+"."));assertNull(EntryAgent.parse(enc(dir)));
    }
    @Test void hostMainOptionsRoundTripThroughTheAgentParser(@TempDir Path dir)throws Exception{
        Path status=Files.createFile(dir.resolve("status"));
        assertTrue(EntryAgent.parse(HostMain.agentOptions(status,dir,true)).generic());
        var off=EntryAgent.parse(HostMain.agentOptions(status,dir,false));assertNotNull(off);assertFalse(off.generic());
        assertTrue(HostMain.agentOptions(status,dir,false).endsWith(".0.-"));assertTrue(HostMain.agentOptions(status,dir,true).endsWith(".0.g"));
    }
    @Test void hostMainArguments(){
        assertFalse(HostMain.parseGeneric(new String[]{"--pid","1","--game-dir","d"}));
        assertTrue(HostMain.parseGeneric(new String[]{"--pid","1","--game-dir","d","--generic"}));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseGeneric(new String[]{"--pid","1","--game-dir","d","--other"}));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseGeneric(new String[]{"--pid","1","--game-dir"}));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseGeneric(new String[]{"--pid","1","--game-dir","d","--generic","x"}));
    }
    @Test void repeatAttachWithSameModeKeepsStatus(){
        assertNull(EntryAgent.claim(true));assertEquals("",EntryAgent.claim(true));
    }
    @Test void repeatAttachWithDifferentModeFailsInsteadOfKeepingTheFirst(){
        assertNull(EntryAgent.claim(false));assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(true));assertEquals("",EntryAgent.claim(false));
        EntryAgent.resetForTest();
        assertNull(EntryAgent.claim(true));assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(false));
        assertTrue("FAILED:MODE:Mismatch".matches("FAILED:[A-Z_]+:[A-Za-z0-9_$]+"));
    }
}
