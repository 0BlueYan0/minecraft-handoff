package tw.handoff.relay.bridge;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PauseScreensTest {
 @Test void recognizesBothSaoPauseScreensByFullName(){
  assertTrue(PauseScreens.saoPause("fr.sao.triggersmod.screen.pausemenu.GameMenuScreen"));
  assertTrue(PauseScreens.saoPause("fr.sao.backrooms.BackroomsPauseScreen"));
 }
 @Test void otherScreensAreNotPauseScreens(){
  assertFalse(PauseScreens.saoPause("fr.sao.mail.MailScreen"));
  assertFalse(PauseScreens.saoPause("fr.sao.backrooms.BackroomsMenuScreen"));
  assertFalse(PauseScreens.saoPause("net.minecraft.class_408"));
  assertFalse(PauseScreens.saoPause("GameMenuScreen"));
  assertFalse(PauseScreens.saoPause("fr.sao.triggersmod.screen.pausemenu.GameMenuScreen$Inner"));
  assertFalse(PauseScreens.saoPause(""));
  assertFalse(PauseScreens.saoPause(null));
 }
 @Test void saoPauseStaysOpenOnlyInGenericMode(){
  assertFalse(PauseScreens.closeOnGrant(true,true));
  assertTrue(PauseScreens.closeOnGrant(true,false));
 }
 @Test void otherScreensAreAlwaysClosedOnGrant(){
  assertTrue(PauseScreens.closeOnGrant(false,true));
  assertTrue(PauseScreens.closeOnGrant(false,false));
 }
}
