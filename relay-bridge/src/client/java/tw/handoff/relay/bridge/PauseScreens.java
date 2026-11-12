package tw.handoff.relay.bridge;
import java.util.Set;

/**
 * SAOUI 用自己的畫面取代原版暫停選單，兩個類別都直接繼承 Screen。
 * 不用 isPauseScreen() 認，因為 SAOUI 2.51.14 裡 47 個覆寫它的畫面（含這兩個暫停選單）全部回 false，認不出誰是暫停選單。
 * 用完整類別名認，relay-bridge 編譯時沒有 SAOUI，所以不能用 instanceof。SAOUI 改名時這裡會認不出來，
 * 結果是照舊回「Close the host screen」，不會放行錯的畫面。
 */
final class PauseScreens {
 static final Set<String> SAO=Set.of("fr.sao.triggersmod.screen.pausemenu.GameMenuScreen","fr.sao.backrooms.BackroomsPauseScreen");
 private PauseScreens(){}
 static boolean saoPause(String className){return className!=null&&SAO.contains(className);}
 /**
  * thin 模式 driver.begin() 會關掉失焦暫停並把視窗設為作用中，所以關掉畫面之後不會重開。
  * generic 模式沒有呼叫 begin()，失焦暫停還開著，關掉 SAO 暫停選單會在下一個畫格被原版重新開出來，再被 SAOUI 換成同一個畫面，
  * 關掉沒有意義，所以留著。
  */
 static boolean closeOnGrant(boolean saoPause,boolean generic){return !(saoPause&&generic);}
}
