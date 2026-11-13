package tw.handoff.viewer.bootstrap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FastCheckTest {
    @TempDir Path temp;
    private static void link(Path link,Path target)throws Exception{
        try{Files.createSymbolicLink(link,target);return;}catch(java.io.IOException|UnsupportedOperationException denied){/* try a junction next */}
        if(System.getProperty("os.name").startsWith("Windows")){
            Process junction=new ProcessBuilder("cmd","/c","mklink","/J",link.toString(),target.toString()).redirectErrorStream(true).start();
            junction.getInputStream().readAllBytes();
            if(junction.waitFor()==0)return;
        }
        Assumptions.abort("Neither symlink nor junction creation is available");
    }
    @Test void folderCachedBeforeItWasReplacedByALinkIsRefusedForWrites()throws Exception{
        Path root=Files.createDirectory(temp.resolve("viewer")),outside=Files.createDirectory(temp.resolve("outside"));
        Files.createDirectory(outside.resolve("b"));
        Path sub=Files.createDirectories(root.resolve("a/b"));
        OutputGuard guard=new OutputGuard(root);
        assertNull(guard.check(sub.resolve("f.txt")));
        Files.delete(sub);Files.delete(root.resolve("a"));link(root.resolve("a"),outside);
        // The cached answer is allowed to be stale for planning...
        guard.check(sub.resolve("f.txt"));
        // ...but a write is always re-resolved against the real path of its parent.
        var refused=assertThrows(java.io.IOException.class,()->guard.forWrite(sub.resolve("f.txt")));
        assertEquals("Linked viewer output refused",refused.getMessage());
        assertEquals("Linked viewer output refused",assertThrows(java.io.IOException.class,()->guard.forWrite(root.resolve("a/f.txt"))).getMessage());
        try(var entries=Files.list(outside.resolve("b"))){assertTrue(entries.findAny().isEmpty());}
    }
    @Test void copyCreatesNothingOutsideWhenACachedFolderBecameALink()throws Exception{
        Path root=Files.createDirectory(temp.resolve("viewer")),outside=Files.createDirectory(temp.resolve("outside"));
        Path from=Files.writeString(temp.resolve("source.txt"),"data");
        Files.createDirectories(root.resolve("a"));
        OutputGuard guard=new OutputGuard(root);
        guard.check(root.resolve("a/b/c/f.txt"));
        Files.delete(root.resolve("a"));link(root.resolve("a"),outside);
        var refused=assertThrows(java.io.IOException.class,()->ViewerSandbox.copy(guard,from,root.resolve("a/b/c/f.txt")));
        assertEquals("Linked viewer output refused",refused.getMessage());
        try(var entries=Files.list(outside)){assertTrue(entries.findAny().isEmpty(),"nothing may be created outside the viewer folder");}
    }
    @Test void pathsOutsideTheRootAreRefused()throws Exception{
        Path root=Files.createDirectory(temp.resolve("viewer"));OutputGuard guard=new OutputGuard(root);
        assertThrows(java.io.IOException.class,()->guard.check(root.resolve("../elsewhere/x.txt")));
        assertThrows(java.io.IOException.class,()->guard.forWrite(temp.resolve("x.txt")));
    }
    @Test void secondPrepareOfManyFilesIsFastAndReportsCheckingProgress()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install")),source=install.resolve("instances/Sword Art Online"),viewer=temp.resolve("viewer");
        Files.createDirectories(source.resolve("mods"));Files.writeString(source.resolve("mods/a.jar"),"code");
        for(int d=0;d<50;d++){Path dir=Files.createDirectories(source.resolve("resourcepacks/sao/d"+d));for(int i=0;i<100;i++)Files.writeString(dir.resolve("f"+i+".png"),"x"+i);}
        var first=new ByteArrayOutputStream();long t0=System.nanoTime();
        ViewerSandbox.prepare(install,source,viewer,false,true,new PrintStream(first,true,StandardCharsets.UTF_8),1000);
        long firstMillis=(System.nanoTime()-t0)/1_000_000;
        var second=new ByteArrayOutputStream();long t1=System.nanoTime();
        ViewerSandbox.prepare(install,source,viewer,false,true,new PrintStream(second,true,StandardCharsets.UTF_8),0);
        long secondMillis=(System.nanoTime()-t1)/1_000_000;
        System.out.println("fastcheck timing: first prepare "+firstMillis+" ms, second prepare "+secondMillis+" ms (5001 files)");
        String text=second.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Checking game files: "),text);
        assertTrue(text.contains("Game files up to date (5001 files)"),text);
        assertFalse(text.contains("Copying game files"));
        assertTrue(secondMillis<15_000,"second prepare took "+secondMillis+" ms");
    }
}
