package tw.handoff.service;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceMainArgsTest {
    private static final Pairing PAIR=new Pairing(2,"127.0.0.1",1,"0".repeat(64),"a".repeat(43),"Name","00000000-0000-0000-0000-000000000000");
    @Test void genericIsAFlagWithoutValueForHostAndViewer(){
        var host=ServiceMain.parse(new String[]{"host","--pid","7","--generic","--state","s"});
        assertTrue(host.generic());assertFalse(host.vanilla());assertEquals(Map.of("--pid","7","--state","s"),host.options());
        var viewer=ServiceMain.parse(new String[]{"viewer","--pair","p","--vanilla","--generic"});
        assertTrue(viewer.generic());assertTrue(viewer.vanilla());assertEquals(Map.of("--pair","p"),viewer.options());
    }
    @Test void genericIsOffByDefault(){assertFalse(ServiceMain.parse(new String[]{"host","--pid","7"}).generic());}
    @Test void optionWithoutValueIsStillRefused(){assertThrows(IllegalArgumentException.class,()->ServiceMain.parse(new String[]{"host","--pid"}));}
    @Test void hostAttachCommandForwardsTheFlag(){
        var on=ServiceMain.attachCommand("java",Path.of("relay.jar"),42,Path.of("game"),true);
        var off=ServiceMain.attachCommand("java",Path.of("relay.jar"),42,Path.of("game"),false);
        assertEquals("--generic",on.getLast());assertFalse(off.contains("--generic"));
        assertEquals(off,on.subList(0,on.size()-1));assertTrue(off.containsAll(List.of("--pid","42","--game-dir")));
    }
    @Test void idleWalkIsAFlagWithoutValueAndOffByDefault(){
        assertFalse(ServiceMain.parse(new String[]{"host","--pid","7"}).idleWalk());
        var both=ServiceMain.parse(new String[]{"host","--pid","7","--idle-walk","--generic","--state","s"});
        assertTrue(both.idleWalk());assertTrue(both.generic());assertEquals(Map.of("--pid","7","--state","s"),both.options());
        var swapped=ServiceMain.parse(new String[]{"host","--generic","--pid","7","--idle-walk"});
        assertTrue(swapped.idleWalk());assertTrue(swapped.generic());assertEquals(Map.of("--pid","7"),swapped.options());
        var only=ServiceMain.parse(new String[]{"host","--pid","7","--idle-walk"});assertTrue(only.idleWalk());assertFalse(only.generic());
        var genericOnly=ServiceMain.parse(new String[]{"host","--pid","7","--generic"});assertFalse(genericOnly.idleWalk());
    }
    @Test void hostAttachCommandForwardsIdleWalk(){
        var game=Path.of("game");var relay=Path.of("relay.jar");
        var both=ServiceMain.attachCommand("java",relay,42,game,true,true);
        var only=ServiceMain.attachCommand("java",relay,42,game,false,true);
        var neither=ServiceMain.attachCommand("java",relay,42,game,false,false);
        assertEquals(List.of("--generic","--idle-walk"),both.subList(both.size()-2,both.size()));
        assertEquals("--idle-walk",only.getLast());assertFalse(only.contains("--generic"));
        assertEquals(neither,only.subList(0,only.size()-1));
        assertEquals(ServiceMain.attachCommand("java",relay,42,game,true),ServiceMain.attachCommand("java",relay,42,game,true,false));
    }
    @Test void viewerRefusesIdleWalk(){
        assertThrows(IllegalArgumentException.class,()->ServiceMain.run(new String[]{"viewer","--pair","p","--idle-walk"}));
    }
    @Test void viewerArgumentsCarryTheFlagToViewerMain(){
        var o=Map.of("--install","i","--viewer-dir","v","--guard","g");
        assertTrue(List.of(ServiceMain.viewerArguments(o,"9",PAIR,false,true)).contains("--generic"));
        assertFalse(List.of(ServiceMain.viewerArguments(o,"9",PAIR,false,false)).contains("--generic"));
        assertTrue(List.of(ServiceMain.viewerArguments(o,"9",PAIR,true,true)).containsAll(List.of("--vanilla","--generic")));
    }
    @Test void viewerMainAcceptsTheGenericCommandLine(){
        var o=Map.of("--install","no-such-install-dir","--viewer-dir","v","--guard","g");
        // Parsing passes and the run stops at the missing install directory. A refused flag would throw IllegalArgumentException instead.
        assertThrows(java.nio.file.NoSuchFileException.class,()->tw.handoff.viewer.bootstrap.ViewerMain.run(ServiceMain.viewerArguments(o,"9",PAIR,false,true)));
    }
}
