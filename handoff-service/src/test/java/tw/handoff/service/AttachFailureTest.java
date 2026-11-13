package tw.handoff.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttachFailureTest {
    @Test void statusLineIsCarriedIntoTheMessage(){
        assertEquals("Original game attach failed: FAILED:BRIDGE:IllegalStateException",ServiceMain.attachFailure("FAILED:BRIDGE:IllegalStateException\n"));
        assertTrue(ServiceMain.attachFailure("FAILED:BRIDGE:IllegalStateException").endsWith("IllegalStateException"));
    }
    @Test void pendingMeansTheGameStillHoldsAnOlderAgent(){
        String message=ServiceMain.attachFailure("PENDING: original game remains running\n");
        assertTrue(message.startsWith("Original game attach failed: PENDING: original game remains running"));assertTrue(message.contains("Restart the game once"));
    }
    @Test void otherFailuresDoNotAskForARestart(){
        assertFalse(ServiceMain.attachFailure("FAILED:STOP:IllegalStateException").contains("Restart"));
        assertFalse(ServiceMain.attachFailure("FAILED:BRIDGE:IllegalStateException").contains("Restart"));
        assertFalse(ServiceMain.attachFailure("").contains("Restart"));
    }
    @Test void restartGameExplainsWhatToDo(){
        String message=ServiceMain.attachFailure("FAILED:AGENT:RestartGame\n");
        assertTrue(message.startsWith("Original game attach failed: FAILED:AGENT:RestartGame"));assertTrue(message.contains("Restart the game once"));
    }
    @Test void outputWithoutAStatusLineKeepsTheOldMessage(){
        assertEquals("Original game attach failed",ServiceMain.attachFailure(""));
        assertEquals("Original game attach failed",ServiceMain.attachFailure("some java banner\nUsage: --pid PID"));
    }
    @Test void aStatusLineAmongOtherOutputIsFound(){
        assertEquals("Original game attach failed: FAILED:STOP:IllegalStateException",ServiceMain.attachFailure("noise\nFAILED:STOP:IllegalStateException\nmore"));
    }
}
