package tw.handoff.agent;

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;

/** Attaches an observer to the original game. Never stops a process, replays a launch or reads auth properties. */
public final class AttachClient {
    public static void main(String[] args) {
        if ((args.length != 2 && args.length != 4) || !args[0].equals("--game-dir")
                || (args.length == 4 && !args[2].equals("--pid"))) {
            System.out.println("Usage: java --add-modules jdk.attach -jar handoff-runtime-agent-0.1.2.jar --game-dir <instance> [--pid <PID>]");
            System.exit(2);
        }
        try {
            int result = run(Path.of(args[1]), args.length == 4 ? Long.parseLong(args[3]) : null);
            System.exit(result);
        } catch (Exception | LinkageError failure) {
            System.out.println("ATTACH_FAILED: " + failure.getClass().getSimpleName() + ". Original game was not stopped or relaunched.");
            System.exit(1);
        }
    }

    private static int run(Path game, Long explicitPid) throws Exception {
        if (!Files.isDirectory(game)) { System.out.println("GAME_DIRECTORY_MISSING"); return 2; }
        Path jar = Path.of(AttachClient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        if (!Files.isRegularFile(jar)) { System.out.println("Run the supplied agent JAR."); return 2; }
        ProcessHandle target;
        if (explicitPid == null) {
            System.out.println("Waiting for the original SAO game. Keep the game and launcher running; do not close either.");
            long start = System.nanoTime();
            do {
                target = GameSelector.find(game);
                if (target != null) break;
                Thread.sleep(1000);
            } while (System.nanoTime() - start < Duration.ofMinutes(2).toNanos());
        } else target = ProcessHandle.of(explicitPid).orElse(null);
        if (target == null || !target.isAlive()) { System.out.println("NO_UNIQUE_GAME_PROCESS"); return 3; }
        String owner = ProcessHandle.current().info().user().orElseThrow();
        if (!target.info().user().orElse("").equals(owner) || target.pid() == ProcessHandle.current().pid()) {
            System.out.println("TARGET_NOT_OWN_GAME"); return 3;
        }
        long pid = target.pid();
        System.out.println("Attaching observer to existing game PID " + pid + ". No login arguments are copied.");
        Path folder = Files.createTempDirectory("minecraft-handoff-attach-");
        Path channel = Files.createFile(folder.resolve("status"));
        try {
            String options = encode(channel.toString()) + "." + encode(game.toRealPath().toString());
            VirtualMachine vm = VirtualMachine.attach(Long.toString(pid));
            try { vm.loadAgent(jar.toString(), options); } finally { vm.detach(); }
            long start = System.nanoTime();
            String result;
            do {
                result = Files.readString(channel);
                if (result.equals("READY") || result.equals("ALREADY_READY")) {
                    if (!target.isAlive()) { System.out.println("GAME_EXITED_DURING_ATTACH"); return 1; }
                    System.out.println("AGENT_" + result + ": original game PID " + pid + " is still running.");
                    System.out.println("Keep playing normally. Inspect [minecraft-handoff-agent] in the original latest.log.");
                    return 0;
                }
                if (result.startsWith("FAILED:")) {
                    // The agent writes only fixed stage codes and exception class names, never messages or arguments.
                    if (result.matches("FAILED:[A-Z_]+(?::[A-Za-z0-9_$]+)?")) System.out.println(result);
                    else System.out.println("AGENT_INITIALIZATION_FAILED");
                    return 1;
                }
                Thread.sleep(100);
            } while (System.nanoTime() - start < Duration.ofSeconds(30).toNanos());
            System.out.println("AGENT_PENDING: initialization may still be queued; do not repeatedly attach. Check the original game log.");
            return 4;
        } finally {
            try { Files.deleteIfExists(channel); Files.deleteIfExists(folder); } catch (Exception ignored) { }
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
