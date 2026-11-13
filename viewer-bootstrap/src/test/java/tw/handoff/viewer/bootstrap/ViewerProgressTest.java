package tw.handoff.viewer.bootstrap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ViewerProgressTest {
    @TempDir Path temp;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final PrintStream console=new PrintStream(bytes,true,StandardCharsets.UTF_8);
    private String output(){return bytes.toString(StandardCharsets.UTF_8);}
    private Path[] layout()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install")),source=install.resolve("instances/Sword Art Online");
        Files.createDirectories(source.resolve("mods"));Files.createDirectories(source.resolve("resourcepacks/sao"));
        return new Path[]{install,source,temp.resolve("viewer")};
    }
    @Test void unchangedFilesPrintOnlyUpToDate()throws Exception{
        Path[] p=layout();Files.write(p[1].resolve("mods/a.jar"),new byte[10]);Files.writeString(p[1].resolve("resourcepacks/sao/pack.mcmeta"),"{}");
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),0);
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,console,60_000);
        assertEquals("Game files up to date (2 files)"+System.lineSeparator(),output());
        assertFalse(output().contains("Copying game files"));
    }
    @Test void changedFilesAreCountedAndOnlyThoseAreCopied()throws Exception{
        Path[] p=layout();Path a=p[1].resolve("mods/a.jar"),b=p[1].resolve("mods/b.jar");
        Files.write(a,new byte[1024*1024]);Files.write(b,new byte[1024*1024]);
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,new PrintStream(new ByteArrayOutputStream()),0);
        Path viewerA=p[2].resolve("mods/a.jar");FileTime before=Files.getLastModifiedTime(viewerA);
        Thread.sleep(20);Files.write(b,new byte[3*1024*1024]);
        ViewerSandbox.prepare(p[0],p[1],p[2],false,true,console,60_000);
        List<String> lines=output().lines().toList();
        assertEquals("Copying game files: 1/1 (3.0/3.0 MB)",lines.getLast());
        assertFalse(output().contains("up to date"));
        assertEquals(before,Files.getLastModifiedTime(viewerA));
        assertEquals(3*1024*1024,Files.size(p[2].resolve("mods/b.jar")));
    }
    @Test void nativesWithSameSizeAreNotRewritten()throws Exception{
        Path viewer=Files.createDirectory(temp.resolve("viewer")),jar=temp.resolve("lwjgl-natives-windows.jar");
        try(var zip=new ZipOutputStream(Files.newOutputStream(jar))){zip.putNextEntry(new ZipEntry("lwjgl.dll"));zip.write(new byte[]{1,2,3,4});zip.closeEntry();}
        var install=new GameInstall(temp,temp.resolve("client.jar"),temp,"idx",List.of(jar));
        ViewerSandbox.extractNatives(install,viewer,new PrintStream(new ByteArrayOutputStream()));
        Path dll=viewer.resolve("natives/lwjgl.dll");FileTime stamp=FileTime.fromMillis(1_000_000_000L);Files.setLastModifiedTime(dll,stamp);
        ViewerSandbox.extractNatives(install,viewer,console);
        assertEquals(stamp,Files.getLastModifiedTime(dll));assertEquals("Natives ready"+System.lineSeparator(),output());
    }
    @Test void readyLineStopsLoadingSecondsAndThreadEndsWithProcess()throws Exception{
        Path log=temp.resolve("launch-console.log");AtomicBoolean alive=new AtomicBoolean(true);
        Thread thread=LaunchProgress.start(log,alive::get,console,30,10);
        Thread.sleep(150);assertTrue(output().contains("Minecraft loading... "));
        Files.writeString(log,"[Render thread/INFO]: Connecting to 127.0.0.1, 25587\n");
        long deadline=System.currentTimeMillis()+5000;while(!output().contains("Minecraft window ready, joining host")&&System.currentTimeMillis()<deadline)Thread.sleep(10);
        assertTrue(output().contains("Minecraft window ready, joining host"));
        thread.join(2000);assertFalse(thread.isAlive());
        String settled=output();Thread.sleep(100);assertEquals(settled,output());
        assertTrue(thread.isDaemon());
    }
    @Test void threadEndsWhenProcessEndsWithoutReadyLine()throws Exception{
        AtomicBoolean alive=new AtomicBoolean(true);
        Thread thread=LaunchProgress.start(temp.resolve("missing.log"),alive::get,console,1000,10);
        Thread.sleep(50);assertTrue(thread.isAlive());
        alive.set(false);thread.join(2000);assertFalse(thread.isAlive());
        assertFalse(output().contains("ready"));
    }
}
