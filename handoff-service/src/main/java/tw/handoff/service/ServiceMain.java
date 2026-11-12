package tw.handoff.service;
import com.google.gson.Gson;
import com.sun.tools.attach.VirtualMachine;
import java.net.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tw.handoff.viewer.bootstrap.ViewerMain;

/** Standalone encrypted sidecar. Stopping it never terminates the original Minecraft process. */
public final class ServiceMain {
 public static void main(String[] args){try{System.exit(run(args));}catch(Exception|LinkageError failure){System.err.println("HANDOFF_FAILED: "+failure.getClass().getSimpleName()+". See the startup guide; no launcher session was changed.");System.exit(1);}}
 static int run(String[] args)throws Exception{
  if(args.length==1&&args[0].equals("list")){for(var vm:VirtualMachine.list()){String main=vm.displayName().split(" ",2)[0];if(main.equals("net.fabricmc.loader.impl.launch.knot.KnotClient"))System.out.println("Minecraft PID "+vm.id());}return 0;}
  if(args.length<1)throw new IllegalArgumentException("Use host or viewer");
  var parsed=parse(args);
  if(parsed.command().equals("host"))return host(parsed.options(),parsed.generic(),parsed.idleWalk());
  if(parsed.command().equals("viewer")){if(parsed.idleWalk())throw new IllegalArgumentException("--idle-walk is for host only");return viewer(parsed.options(),parsed.vanilla(),parsed.generic());}
  throw new IllegalArgumentException("Use host or viewer");
 }
 /** --vanilla, --generic and --idle-walk are flags without a value; every other option takes one. */
 record Parsed(String command,Map<String,String> options,boolean vanilla,boolean generic,boolean idleWalk){}
 static Parsed parse(String[] args){
  var options=new HashMap<String,String>();boolean vanilla=false,generic=false,idleWalk=false;
  for(int i=1;i<args.length;i++){if(args[i].equals("--vanilla")){vanilla=true;continue;}if(args[i].equals("--generic")){generic=true;continue;}if(args[i].equals("--idle-walk")){idleWalk=true;continue;}if(!args[i].startsWith("--")||i+1==args.length)throw new IllegalArgumentException();options.put(args[i],args[++i]);}
  return new Parsed(args[0],options,vanilla,generic,idleWalk);
 }
 /** The relay helper that attaches to the game; --generic and --idle-walk are forwarded so the agent starts packet forwarding in PRODUCTION mode and the timed walk. */
 static List<String> attachCommand(String java,Path relay,long pid,Path gameDirectory,boolean generic){return attachCommand(java,relay,pid,gameDirectory,generic,false);}
 static List<String> attachCommand(String java,Path relay,long pid,Path gameDirectory,boolean generic,boolean idleWalk){
  var command=new ArrayList<>(List.of(java,"--add-modules","jdk.attach","-jar",relay.toString(),"--pid",Long.toString(pid),"--game-dir",gameDirectory.toString()));
  if(generic)command.add("--generic");
  if(idleWalk)command.add("--idle-walk");
  return command;
 }
 static String[] viewerArguments(Map<String,String> o,String pairPort,Pairing pair,boolean vanilla,boolean generic){
  var args=new ArrayList<>(List.of("--install",required(o,"--install"),"--viewer-dir",required(o,"--viewer-dir"),"--guard",required(o,"--guard"),"--port",pairPort,"--name",pair.name(),"--uuid",pair.uuid()));
  if(vanilla)args.add("--vanilla");
  if(generic)args.add("--generic");
  return args.toArray(String[]::new);
 }
 private static String required(Map<String,String> o,String key){String value=o.get(key);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing "+key);return value;}
 private static int host(Map<String,String> o,boolean generic,boolean idleWalk)throws Exception{
  Path state=Path.of(required(o,"--state")).toAbsolutePath();Files.createDirectories(state);PrivateFiles.restrict(state,true);
  try(var lockFile=FileChannel.open(state.resolve("service.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=lockFile.tryLock()){
   if(lock==null)throw new IllegalStateException("Host service already running");
   long pid=Long.parseLong(required(o,"--pid"));ProcessHandle game=ProcessHandle.of(pid).orElseThrow();
   Path directory=Path.of(required(o,"--game-dir")).toRealPath(),relay=Path.of(required(o,"--relay-jar")).toRealPath();
   Path javaBinary=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
   ProcessBuilder attach=new ProcessBuilder(attachCommand(javaBinary.toString(),relay,pid,directory,generic,idleWalk));
   attach.environment().remove("JAVA_TOOL_OPTIONS");attach.environment().remove("JDK_JAVA_OPTIONS");attach.environment().remove("_JAVA_OPTIONS");
   attach.redirectError(ProcessBuilder.Redirect.DISCARD);Process helper=attach.start();
   if(!helper.waitFor(75,TimeUnit.SECONDS)){helper.destroyForcibly();throw new IllegalStateException("Attach timed out");}
   String output=new String(helper.getInputStream().readNBytes(4096),java.nio.charset.StandardCharsets.UTF_8);
   if(helper.exitValue()!=0)throw new IllegalStateException("Original game attach failed");
   String ready=output.lines().filter(line->line.matches("READY:[0-9]{1,5}:[0-9a-f-]{36}:[A-Za-z0-9_]{1,16}")).findFirst().orElseThrow();String[] fields=ready.split(":");
   int nativePort=Integer.parseInt(fields[1]);String token=TlsIdentity.randomToken();var identity=TlsIdentity.open(state);
   int port=Integer.parseInt(o.getOrDefault("--port","25575"));String address=o.getOrDefault("--bind","0.0.0.0"),published=required(o,"--pair-host");
   try(var tunnel=new HostTunnel(identity.context(),InetAddress.getByName(address),port,nativePort,token)){
    var pair=new Pairing(2,published,tunnel.port(),identity.fingerprint(),token,fields[3],fields[2]);
    Path pairing=Path.of(o.getOrDefault("--pair-out",state.resolve("pairing.json").toString())).toAbsolutePath();PrivateFiles.write(pairing,new Gson().toJson(pair));
    Runtime.getRuntime().addShutdownHook(new Thread(tunnel::close));tunnel.start();
    System.out.println("HOST_READY: encrypted port "+tunnel.port()+"; original Minecraft PID "+pid);
    System.out.println("Pairing file: "+pairing+" (contains remote-control credentials; transfer privately)");
    System.out.println("Leave the original launcher and game open. Ctrl+C stops remote access only. Host F8 releases control.");
    while(game.isAlive())Thread.sleep(1000);
   }
  }return 0;
 }
 private static int viewer(Map<String,String> o,boolean vanilla,boolean generic)throws Exception{
  Path pairing=Path.of(required(o,"--pair"));if(Files.size(pairing)>8192)throw new IllegalArgumentException("Invalid pairing file");
  Pairing pair;try(var reader=Files.newBufferedReader(pairing)){pair=new Gson().fromJson(reader,Pairing.class);}if(pair==null)throw new IllegalArgumentException();
  System.out.println("Checking paired host identity...");try(var probe=ClientTunnel.connect(pair)){}
  try(var tunnel=new ClientTunnel(pair)){
   tunnel.start();Runtime.getRuntime().addShutdownHook(new Thread(tunnel::close));
   System.out.println("Paired. Launching local-rendering viewer; F6 takes control, F7 releases it.");
   return ViewerMain.run(viewerArguments(o,Integer.toString(tunnel.port()),pair,vanilla,generic));
  }
 }
}
