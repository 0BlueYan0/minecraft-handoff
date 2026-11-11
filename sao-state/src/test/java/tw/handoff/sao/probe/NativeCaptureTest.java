package tw.handoff.sao.probe;
import java.nio.file.*;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class NativeCaptureTest {
 @TempDir Path temp;
 public static final class Named {public File gameDirectory;public Object player,level;Named(File path){gameDirectory=path;}}
 public static final class Production {public File field_1697;public Object field_1724,field_1687;Production(File path){field_1697=path;}}
 @Test void menuAndWrongInstanceCannotCreateCaptureOutput()throws Exception{
  var game=Files.createDirectory(temp.resolve("game"));var wrong=Files.createDirectory(temp.resolve("wrong"));var mc=new Named(game.toFile());var status=Files.createFile(temp.resolve("status"));var out=temp.resolve("out.json");
  assertThrows(IllegalArgumentException.class,()->NativeCapture.captureOrThrow(mc,wrong,out,status));assertFalse(Files.exists(out));
  NativeCapture.capture(mc,wrong,out,status);assertFalse(Files.exists(out));
  assertEquals("CAPTURE_FAILED:IllegalArgumentException:capture:Wrong instance",Files.readString(status));
  assertThrows(IllegalStateException.class,()->NativeCapture.requireWorld(mc));mc.player=new Object();assertThrows(IllegalStateException.class,()->NativeCapture.requireWorld(mc));mc.level=new Object();NativeCapture.requireWorld(mc);
 }
 @Test void productionNamesAreCheckedWithoutReadingOtherFields()throws Exception{var mc=new Production(temp.toFile());assertThrows(IllegalStateException.class,()->NativeCapture.requireWorld(mc));mc.field_1724=new Object();mc.field_1687=new Object();NativeCapture.requireWorld(mc);}
}
