package tw.handoff.viewer.bootstrap;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlatformTest {
    @Test void libraryRulesUseOsArchitectureAndOrderedOverrides(){
        var win=new Platform("windows","amd64","10.0");
        assertTrue(win.allows(null));
        assertTrue(win.allows(JsonParser.parseString("[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}]").getAsJsonArray()));
        assertFalse(win.allows(JsonParser.parseString("[{\"action\":\"allow\",\"os\":{\"name\":\"osx\"}}]").getAsJsonArray()));
        assertFalse(win.allows(JsonParser.parseString("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"arch\":\"amd64\"}}]").getAsJsonArray()));
        assertFalse(win.allows(JsonParser.parseString("[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}]").getAsJsonArray()));
    }
}
