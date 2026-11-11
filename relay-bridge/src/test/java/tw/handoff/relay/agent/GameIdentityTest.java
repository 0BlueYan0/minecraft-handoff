package tw.handoff.relay.agent;
import java.io.File;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class GameIdentityTest {
 @TempDir Path temp;
 public static final class ProductionClient {public File field_1697;ProductionClient(File path){field_1697=path;}}
 @Test void wrongThenCorrectAndWrongAfterSuccessAreIndependentlyValidated()throws Exception{
  Path game=Files.createDirectory(temp.resolve("game")),other=Files.createDirectory(temp.resolve("other"));Object client=new ProductionClient(game.toFile());
  assertThrows(IllegalArgumentException.class,()->GameIdentity.require(client,other));GameIdentity.require(client,game);
  assertThrows(IllegalArgumentException.class,()->GameIdentity.require(client,other));GameIdentity.require(client,game);
 }
}
