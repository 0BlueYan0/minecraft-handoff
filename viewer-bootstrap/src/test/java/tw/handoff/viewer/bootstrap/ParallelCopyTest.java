package tw.handoff.viewer.bootstrap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ParallelCopyTest {
    @TempDir Path temp;
    private static final Pattern LINE=Pattern.compile("Copying game files: (\\d+)/(\\d+) \\(([0-9.]+)/([0-9.]+) MB\\)");

    @Test void threadCountFollowsCoresBetweenTwoAndEight(){
        assertEquals(2,ViewerSandbox.copyThreads(1));
        assertEquals(2,ViewerSandbox.copyThreads(2));
        assertEquals(6,ViewerSandbox.copyThreads(6));
        assertEquals(8,ViewerSandbox.copyThreads(12));
        assertEquals(8,ViewerSandbox.copyThreads(64));
    }

    private Path[] layout()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install")),source=install.resolve("instances/Sword Art Online");
        Files.createDirectories(source.resolve("mods"));Files.writeString(source.resolve("mods/a.jar"),"code");
        return new Path[]{install,source,temp.resolve("viewer")};
    }

    @Test void fiveThousandSmallFilesAreCopiedWithMatchingContentAndCountedProgress()throws Exception{
        Path[] p=layout();
        for(int d=0;d<50;d++){Path dir=Files.createDirectories(p[1].resolve("resourcepacks/sao/d"+d));for(int i=0;i<100;i++)Files.writeString(dir.resolve("f"+i+".png"),"content-"+d+"-"+i);}
        var out=new ByteArrayOutputStream();long t0=System.nanoTime();
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(out,true,StandardCharsets.UTF_8),0);
        System.out.println("parcopy timing: first prepare "+(System.nanoTime()-t0)/1_000_000+" ms with "+ViewerSandbox.copyThreads(Runtime.getRuntime().availableProcessors())+" threads (5001 files)");
        for(int d=0;d<50;d++)for(int i=0;i<100;i++)
            assertEquals("content-"+d+"-"+i,Files.readString(p[2].resolve("resourcepacks/sao/d"+d+"/f"+i+".png")));
        assertEquals("code",Files.readString(p[2].resolve("mods/a.jar")));
        List<String> lines=out.toString(StandardCharsets.UTF_8).lines().filter(l->l.startsWith("Copying game files")).toList();
        assertTrue(lines.getFirst().startsWith("Copying game files: 0/5001 (0.0/"),lines.getFirst());
        Matcher last=LINE.matcher(lines.getLast());assertTrue(last.matches(),lines.getLast());
        assertEquals("5001",last.group(1));assertEquals("5001",last.group(2));assertEquals(last.group(4),last.group(3));
        int previous=-1;
        for(String line:lines){Matcher m=LINE.matcher(line);assertTrue(m.matches(),line);int done=Integer.parseInt(m.group(1));assertTrue(done>=previous,"progress went backwards: "+lines);previous=done;}
        var again=new ByteArrayOutputStream();
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(again,true,StandardCharsets.UTF_8),60_000);
        assertTrue(again.toString(StandardCharsets.UTF_8).contains("Game files up to date (5001 files)"));
    }

    @Test void slowIntervalPrintsOnlyTheFirstAndLastLine()throws Exception{
        Path[] p=layout();
        Path dir=Files.createDirectories(p[1].resolve("resourcepacks/sao"));for(int i=0;i<200;i++)Files.writeString(dir.resolve("f"+i+".png"),"x");
        var out=new ByteArrayOutputStream();
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(out,true,StandardCharsets.UTF_8),60_000);
        List<String> lines=out.toString(StandardCharsets.UTF_8).lines().filter(l->l.startsWith("Copying game files")).toList();
        assertEquals(2,lines.size(),lines.toString());
        assertTrue(lines.getLast().startsWith("Copying game files: 201/201 "),lines.getLast());
    }

    /** 目的地是一個非空資料夾，複製這個檔案一定失敗。錯誤要原樣丟出，不能被吞掉或包成別的例外。 */
    @Test void aFailedFileStopsTheCopyAndItsErrorIsThrown()throws Exception{
        Path[] p=layout();
        Path dir=Files.createDirectories(p[1].resolve("resourcepacks/sao"));for(int i=0;i<300;i++)Files.writeString(dir.resolve("f"+i+".png"),"x");
        Path blocked=Files.createDirectories(p[2].resolve("resourcepacks/sao/f150.png"));Files.writeString(blocked.resolve("inside"),"keep");
        Files.createDirectories(p[2]);Files.writeString(p[2].resolve(".handoff-viewer"),"MinecraftHandoffViewer:1");
        var failure=assertThrows(IOException.class,()->ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),60_000));
        assertInstanceOf(java.nio.file.DirectoryNotEmptyException.class,failure);
        assertEquals("keep",Files.readString(blocked.resolve("inside")));
        // 下次啟動照舊會再試這個檔案，沒有被當成已複製。
        assertThrows(IOException.class,()->ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),60_000));
    }

    @Test void filesFromAFailedRunAreCopiedAgainAfterTheProblemIsGone()throws Exception{
        Path[] p=layout();
        Path dir=Files.createDirectories(p[1].resolve("resourcepacks/sao"));for(int i=0;i<50;i++)Files.writeString(dir.resolve("f"+i+".png"),"v"+i);
        Path blocked=Files.createDirectories(p[2].resolve("resourcepacks/sao/f10.png"));Files.writeString(blocked.resolve("inside"),"x");
        Files.writeString(p[2].resolve(".handoff-viewer"),"MinecraftHandoffViewer:1");
        assertThrows(IOException.class,()->ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),60_000));
        Files.delete(blocked.resolve("inside"));Files.delete(blocked);
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),60_000);
        for(int i=0;i<50;i++)assertEquals("v"+i,Files.readString(p[2].resolve("resourcepacks/sao/f"+i+".png")));
    }

    /** 寫入過的資料夾才會記住，check() 規劃時記住的資料夾不算，所以寫入前一定解析過一次真實路徑。 */
    @Test void aFolderRememberedOnlyByPlanningIsStillResolvedBeforeTheFirstWrite()throws Exception{
        Path root=Files.createDirectory(temp.resolve("viewer2"));
        Path from=Files.writeString(temp.resolve("src.txt"),"data");
        OutputGuard guard=new OutputGuard(root);
        guard.check(root.resolve("a/f.txt"));
        Files.createDirectories(root.resolve("a"));
        ViewerSandbox.copy(guard,from,root.resolve("a/f.txt"));
        ViewerSandbox.copy(guard,from,root.resolve("a/g.txt"));
        assertEquals("data",Files.readString(root.resolve("a/g.txt")));
    }
}
