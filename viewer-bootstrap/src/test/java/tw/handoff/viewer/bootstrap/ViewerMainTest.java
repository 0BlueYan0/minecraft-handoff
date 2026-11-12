package tw.handoff.viewer.bootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewerMainTest {
    private static final String[] REQUIRED={"--install","install","--viewer-dir","viewer","--guard","guard.jar","--port","25587"};
    private static String[] with(String... extra){
        String[] all=new String[REQUIRED.length+extra.length];
        System.arraycopy(REQUIRED,0,all,0,REQUIRED.length);System.arraycopy(extra,0,all,REQUIRED.length,extra.length);return all;
    }
    @Test void genericAndPacketLabAreMutuallyExclusive(){
        var failure=assertThrows(IllegalArgumentException.class,()->ViewerMain.run(with("--generic","--packet-lab")));
        assertTrue(failure.getMessage().contains("mutually exclusive"));
    }
    @Test void exclusionDoesNotDependOnArgumentOrder(){
        assertThrows(IllegalArgumentException.class,()->ViewerMain.run(with("--packet-lab","--host-fixture","--generic")));
    }
    @Test void heapOverrideIsRefusedOutsideFixtureModes(){
        var failure=assertThrows(IllegalArgumentException.class,()->ViewerMain.run(with("--fixture-xmx","2G")));
        assertTrue(failure.getMessage().contains("fixtures only"));
    }
    @Test void heapOverrideMustBeAPlainSize(){
        assertThrows(IllegalArgumentException.class,()->ViewerMain.run(with("--generic","--fixture-xmx","2G -Dx=1")));
    }
}
