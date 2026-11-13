package tw.handoff.viewer.bootstrap;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/** Follows the game's console log and prints coarse loading progress. It never prints log content. */
final class LaunchProgress {
    static final String READY_MARKER="Connecting to 127.0.0.1";
    private LaunchProgress(){}
    static Thread start(Path log,BooleanSupplier alive,PrintStream console,long printIntervalMillis,long pollMillis){
        Thread thread=new Thread(()->follow(log,alive,console,printIntervalMillis,pollMillis),"viewer-launch-progress");
        thread.setDaemon(true);thread.start();return thread;
    }
    private static void follow(Path log,BooleanSupplier alive,PrintStream console,long printIntervalMillis,long pollMillis){
        long begin=System.nanoTime(),lastPrint=begin,offset=0;
        String tail="";
        try{
            while(true){
                boolean running=alive.getAsBoolean();
                try{
                    if(Files.isRegularFile(log)){
                        try(RandomAccessFile file=new RandomAccessFile(log.toFile(),"r")){
                            long length=file.length();
                            if(length<offset){offset=0;tail="";}
                            if(length>offset){
                                byte[] bytes=new byte[(int)Math.min(length-offset,1<<20)];
                                file.seek(offset);int read=file.read(bytes);
                                if(read>0){
                                    offset+=read;tail+=new String(bytes,0,read,StandardCharsets.ISO_8859_1);
                                    if(tail.contains(READY_MARKER)){console.println("Minecraft window ready, joining host");return;}
                                    tail=tail.substring(Math.max(0,tail.length()-READY_MARKER.length()+1));
                                }
                            }
                        }
                    }
                }catch(IOException|RuntimeException unreadable){/* the log may not exist yet or be locked; try again */}
                if(!running)return;
                long now=System.nanoTime();
                if(now-lastPrint>=printIntervalMillis*1_000_000L){lastPrint=now;console.println("Minecraft loading... "+(now-begin)/1_000_000_000L+"s");}
                Thread.sleep(pollMillis);
            }
        }catch(InterruptedException stop){Thread.currentThread().interrupt();}
    }
}
