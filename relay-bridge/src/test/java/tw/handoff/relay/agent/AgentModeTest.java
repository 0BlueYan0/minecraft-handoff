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
    @Test void jarFieldIsOptionalAndOldFormsHaveNone(@TempDir Path dir)throws Exception{
        String b=base(dir);Path jar=Files.createFile(dir.resolve("relay-agent.jar"));
        assertNull(EntryAgent.parse(b).jar());assertNull(EntryAgent.parse(b+".g").jar());assertNull(EntryAgent.parse(b+".g.w").jar());
        var o=EntryAgent.parse(b+".g.w."+enc(jar));assertEquals(jar.toRealPath(),o.jar());assertTrue(o.generic());assertTrue(o.idleWalk());
        var plain=EntryAgent.parse(b+".-.-."+enc(jar));assertEquals(jar.toRealPath(),plain.jar());assertFalse(plain.generic());assertFalse(plain.idleWalk());
        assertNull(EntryAgent.parse(b+".g.w."+enc(dir.resolve("missing.jar"))));assertNull(EntryAgent.parse(b+".g.w."+enc(dir)));
        assertNull(EntryAgent.parse(b+".g.w."+enc(jar)+".x"));assertNull(EntryAgent.parse(b+".g.w."));
    }
    @Test void hostMainWritesAllSixFieldsWithTheAttachedJar(@TempDir Path dir)throws Exception{
        Path status=Files.createFile(dir.resolve("status")),jar=Files.createFile(dir.resolve("relay-agent.jar"));
        for(boolean generic:new boolean[]{false,true})for(boolean walk:new boolean[]{false,true}){
            String options=HostMain.agentOptions(status,dir,generic,walk,jar);
            assertEquals(6,options.split("\\.",-1).length);
            var o=EntryAgent.parse(options);assertNotNull(o);assertEquals(generic,o.generic());assertEquals(walk,o.idleWalk());assertEquals(jar.toRealPath(),o.jar());
        }
    }
    @Test void firstAttachStartsAndSameJarAndSettingsKeep(){
        assertEquals(EntryAgent.Action.START,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,false));
        assertEquals(EntryAgent.Action.KEEP,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,false));
    }
    @Test void differentJarReloads(){
        EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,true);
        assertEquals(EntryAgent.Action.RELOAD,EntryAgent.claim("b",EntryAgent.AGENT_VERSION,true,true));
        assertEquals(EntryAgent.Action.KEEP,EntryAgent.claim("b",EntryAgent.AGENT_VERSION,true,true));
    }
    @Test void differentSettingsReloadInsteadOfFailing(){
        EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,true);
        assertEquals(EntryAgent.Action.RELOAD,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,false,true));
        assertEquals(EntryAgent.Action.RELOAD,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,false,false));
        assertEquals(EntryAgent.Action.RELOAD,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,false));
        assertEquals(EntryAgent.Action.KEEP,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,false));
    }
    @Test void otherAgentVersionAsksForRestartAndChangesNothing(){
        assertEquals(EntryAgent.Action.RESTART_GAME,EntryAgent.claim("a",EntryAgent.AGENT_VERSION+1,true,true));
        assertEquals(EntryAgent.Action.START,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,true));
        assertEquals(EntryAgent.Action.RESTART_GAME,EntryAgent.claim("b",EntryAgent.AGENT_VERSION+1,true,true));
        assertEquals(EntryAgent.Action.RESTART_GAME,EntryAgent.claim("a",-1,true,true));
        assertEquals(EntryAgent.Action.KEEP,EntryAgent.claim("a",EntryAgent.AGENT_VERSION,true,true));
    }
    private static EntryAgent.Starter starting(String result,java.util.List<String> log,EntryAgent.Stopper stopper){return ()->{log.add("start "+result);return new EntryAgent.Started(result,stopper);};}
    @Test void firstSwapStartsWithoutStopping()throws Exception{
        var log=new java.util.ArrayList<String>();
        assertEquals("one",EntryAgent.swap(starting("one",log,()->log.add("stop one"))));
        assertEquals(java.util.List.of("start one"),log);assertTrue(EntryAgent.hasRunningBridge());
    }
    @Test void secondSwapStopsTheOldBridgeBeforeStartingTheNewOne()throws Exception{
        var log=new java.util.ArrayList<String>();
        EntryAgent.swap(starting("one",log,()->log.add("stop one")));
        assertEquals("two",EntryAgent.swap(starting("two",log,()->log.add("stop two"))));
        assertEquals(java.util.List.of("start one","stop one","start two"),log);
    }
    @Test void stopFailureKeepsTheOldBridgeAndNeverStartsTheNewOne()throws Exception{
        var log=new java.util.ArrayList<String>();
        EntryAgent.swap(starting("one",log,()->{log.add("stop one");throw new IllegalStateException("stuck");}));
        var failure=assertThrows(EntryAgent.StopFailure.class,()->EntryAgent.swap(starting("two",log,()->log.add("stop two"))));
        assertEquals(IllegalStateException.class,failure.getCause().getClass());
        assertEquals(java.util.List.of("start one","stop one"),log);assertTrue(EntryAgent.hasRunningBridge());
        // The failed attach is unclaimed by agentmain, so the next attach is a START that tries the stop again.
        assertEquals(EntryAgent.Action.START,startAfterFailure("a","b"));
        assertThrows(EntryAgent.StopFailure.class,()->EntryAgent.swap(starting("two",log,()->log.add("stop two"))));
        assertEquals(java.util.List.of("start one","stop one","stop one"),log);
    }
    @Test void linkageErrorInStopCountsAsStopFailure()throws Exception{
        EntryAgent.swap(()->new EntryAgent.Started("one",()->{throw new NoClassDefFoundError("x");}));
        assertThrows(EntryAgent.StopFailure.class,()->EntryAgent.swap(()->new EntryAgent.Started("two",()->{})));
    }
    @Test void startFailureAfterStopLeavesNothingRunningAndTheNextSwapStartsAgain()throws Exception{
        var log=new java.util.ArrayList<String>();
        EntryAgent.swap(starting("one",log,()->log.add("stop one")));
        assertThrows(IllegalStateException.class,()->EntryAgent.swap(()->{log.add("start two");throw new IllegalStateException("bad jar");}));
        assertEquals(java.util.List.of("start one","stop one","start two"),log);assertFalse(EntryAgent.hasRunningBridge());
        assertEquals(EntryAgent.Action.START,startAfterFailure("a","b"));
        assertEquals("three",EntryAgent.swap(starting("three",log,()->log.add("stop three"))));
        assertEquals("start three",log.get(log.size()-1));assertFalse(log.subList(3,log.size()).contains("stop one"));
    }
    /** What agentmain does after a failed swap: unclaim, so the next claim does not see the failed jar as running. */
    private static EntryAgent.Action startAfterFailure(String shaBefore,String shaFailed){
        EntryAgent.claim(shaBefore,EntryAgent.AGENT_VERSION,true,true);EntryAgent.claim(shaFailed,EntryAgent.AGENT_VERSION,true,true);
        EntryAgent.unclaim();return EntryAgent.claim(shaFailed,EntryAgent.AGENT_VERSION,true,true);
    }
    @Test void restartStatusMatchesTheHelperStatusPattern(){
        assertEquals("FAILED:AGENT:RestartGame",EntryAgent.RESTART_GAME_STATUS);
        assertTrue(EntryAgent.RESTART_GAME_STATUS.matches("FAILED:[A-Z_]+:[A-Za-z0-9_$]+"));
    }
    @Test void shaIsStableAndFollowsTheBytes(@TempDir Path dir)throws Exception{
        Path a=dir.resolve("a.jar"),b=dir.resolve("b.jar");Files.writeString(a,"one");Files.writeString(b,"one");
        assertEquals(EntryAgent.sha256(a),EntryAgent.sha256(b));assertEquals(64,EntryAgent.sha256(a).length());
        Files.writeString(b,"two");assertNotEquals(EntryAgent.sha256(a),EntryAgent.sha256(b));
    }
    @Test void builtJarCarriesTheAgentVersionTheCodeHas(){
        assertEquals(EntryAgent.AGENT_VERSION,EntryAgent.jarAgentVersion(Path.of(System.getProperty("handoff.bridgeDevJar"))));
    }
    @Test void jarWithoutTheAttributeCountsAsOlder(@TempDir Path dir)throws Exception{
        Path plain=dir.resolve("plain.jar");
        try(var out=new java.util.jar.JarOutputStream(Files.newOutputStream(plain))){out.putNextEntry(new java.util.zip.ZipEntry("x.txt"));out.closeEntry();}
        assertEquals(-1,EntryAgent.jarAgentVersion(plain));assertEquals(-1,EntryAgent.jarAgentVersion(dir.resolve("missing.jar")));
    }
}
