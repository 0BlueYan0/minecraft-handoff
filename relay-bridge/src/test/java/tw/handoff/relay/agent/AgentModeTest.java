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
    @Test void idleWalkFieldIsOptionalAndOldFormsMeanOff(@TempDir Path dir)throws Exception{
        String b=base(dir);
        assertFalse(EntryAgent.parse(b).idleWalk());assertFalse(EntryAgent.parse(b+".g").idleWalk());assertFalse(EntryAgent.parse(b+".-").idleWalk());
        var on=EntryAgent.parse(b+".-.w");assertTrue(on.idleWalk());assertFalse(on.generic());
        var both=EntryAgent.parse(b+".g.w");assertTrue(both.idleWalk());assertTrue(both.generic());
        var off=EntryAgent.parse(b+".g.-");assertFalse(off.idleWalk());assertTrue(off.generic());
        assertNull(EntryAgent.parse(b+".g.x"));assertNull(EntryAgent.parse(b+".g.W"));assertNull(EntryAgent.parse(b+".g.w.w"));assertNull(EntryAgent.parse(b+".g."));
    }
    @Test void hostMainWritesTheIdleWalkFieldOnlyWhenOn(@TempDir Path dir)throws Exception{
        Path status=Files.createFile(dir.resolve("status"));
        assertTrue(HostMain.agentOptions(status,dir,false,true).endsWith(".0.-.w"));assertTrue(HostMain.agentOptions(status,dir,true,true).endsWith(".0.g.w"));
        assertEquals(HostMain.agentOptions(status,dir,true),HostMain.agentOptions(status,dir,true,false));
        var o=EntryAgent.parse(HostMain.agentOptions(status,dir,true,true));assertTrue(o.generic());assertTrue(o.idleWalk());
        var only=EntryAgent.parse(HostMain.agentOptions(status,dir,false,true));assertFalse(only.generic());assertTrue(only.idleWalk());
    }
    @Test void hostMainFlagsInAnyOrder(){
        String[] base={"--pid","1","--game-dir","d"};
        assertEquals(new HostMain.Flags(false,false),HostMain.parseFlags(base));
        assertEquals(new HostMain.Flags(true,false),HostMain.parseFlags(concat(base,"--generic")));
        assertEquals(new HostMain.Flags(false,true),HostMain.parseFlags(concat(base,"--idle-walk")));
        assertEquals(new HostMain.Flags(true,true),HostMain.parseFlags(concat(base,"--generic","--idle-walk")));
        assertEquals(new HostMain.Flags(true,true),HostMain.parseFlags(concat(base,"--idle-walk","--generic")));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseFlags(concat(base,"--idle-walk","--idle-walk")));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseFlags(concat(base,"--generic","--generic")));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseFlags(concat(base,"--idle-walk","--other")));
        assertThrows(IllegalArgumentException.class,()->HostMain.parseFlags(concat(base,"--idle-walk","--generic","--generic")));
    }
    private static String[] concat(String[] a,String... b){var r=new String[a.length+b.length];System.arraycopy(a,0,r,0,a.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
    @Test void repeatAttachWithDifferentIdleWalkFailsInsteadOfKeepingTheFirst(){
        assertNull(EntryAgent.claim(true,true));assertEquals("",EntryAgent.claim(true,true));
        assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(true,false));assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(false,true));assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(false,false));
        EntryAgent.resetForTest();
        assertNull(EntryAgent.claim(true,false));assertEquals("FAILED:MODE:Mismatch",EntryAgent.claim(true,true));assertEquals("",EntryAgent.claim(true));
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
