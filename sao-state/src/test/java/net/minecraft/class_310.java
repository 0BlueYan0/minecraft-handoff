package net.minecraft;
import java.io.File;
/** Test-owned JVM target, no Minecraft implementation. */
public final class class_310 implements java.util.concurrent.Executor {
 private static class_310 instance;
 public File field_1697;public Object field_1724=new Object(),field_1687=new Object();
 public static void init(File path){instance=new class_310();instance.field_1697=path;}
 public static class_310 method_1551(){return instance;}
 public void execute(Runnable work){work.run();}
}
