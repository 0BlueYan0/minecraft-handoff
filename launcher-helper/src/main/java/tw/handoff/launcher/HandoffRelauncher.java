package tw.handoff.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/** Local, memory-only reuse of the owner's original game launch. Never serializes or prints arguments. */
public final class HandoffRelauncher {
    private static final String MOD_SHA256 = "71d2fa1db53c6271b17eeb501f78f29933062f926c36c444f0269fa6183fe105";

    public static void main(String[] args) {
        if (args.length != 4 || !args[0].equals("--game-dir") || !args[2].equals("--mod")) {
            System.out.println("Usage: java -jar handoff-launcher-helper-0.1.1.jar --game-dir <instance> --mod <diagnostic.jar>");
            System.exit(2);
        }
        if (!System.getProperty("os.name").equals("Mac OS X")) {
            System.out.println("This helper is intended for the Mac acceptance test.");
            System.exit(2);
        }
        try {
            run(Path.of(args[1]), Path.of(args[3]));
        } catch (Exception exception) {
            // Exception messages and stack traces can contain command arguments. Do not print them.
            System.out.println("Helper stopped (" + exception.getClass().getSimpleName() + "). No launcher or game files were modified.");
            System.exit(1);
        }
    }

    private static void run(Path game, Path mod) throws Exception {
        if (!Files.isDirectory(game) || !Files.isRegularFile(mod)) {
            System.out.println("Instance directory or diagnostic JAR is missing.");
            return;
        }
        if (ProcessHandle.current().info().arguments().isEmpty()) {
            System.out.println("This Java runtime does not expose process argument arrays. No relaunch was attempted.");
            return;
        }
        if (Files.size(mod) != 13585) {
            System.out.println("Diagnostic JAR size mismatch; use the supplied unmodified 0.1.0 JAR.");
            return;
        }
        try (var stream = Files.newInputStream(mod)) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            if (!MOD_SHA256.equals(digest)) {
                System.out.println("Diagnostic JAR checksum mismatch; use the unmodified 0.1.0 diagnostic JAR supplied with this helper.");
                return;
            }
        }
        System.out.println("Start SAO with the original launcher and leave the game open. Waiting up to 5 minutes...");
        OriginalGameFinder.Match candidate = awaitCandidate(game, mod, Duration.ofMinutes(5));
        if (candidate == null) {
            System.out.println("No uniquely identifiable SAO game process was found. Keep only one SAO game open, then retry.");
            return;
        }
        System.out.println("Original game detected. Launch arguments are held only in memory and will not be printed or saved.");
        System.out.println("Within 2 minutes, close that game normally. Keep this terminal open. Do not press Play again in the original launcher.");
        candidate.process().onExit().get(2, TimeUnit.MINUTES);
        ProcessBuilder child = new ProcessBuilder(candidate.plan().command());
        child.directory(candidate.plan().workingDirectory().toFile());
        var environment = LaunchPlan.childEnvironment(child.environment());
        child.environment().clear();
        child.environment().putAll(environment);
        // The game maintains its normal latest.log; avoid another console copy of third-party output.
        child.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        child.redirectError(ProcessBuilder.Redirect.DISCARD);
        System.out.println("Starting the same Java/game with the external diagnostic JAR. Keep this terminal open until the game closes.");
        int exit = child.start().waitFor();
        System.out.println("Game exited with code " + exit + ". Inspect only [minecraft-handoff] lines in the instance latest.log.");
    }

    private static OriginalGameFinder.Match awaitCandidate(Path game, Path mod, Duration timeout) throws Exception {
        long started = System.nanoTime();
        String user = ProcessHandle.current().info().user().orElseThrow();
        while (System.nanoTime() - started < timeout.toNanos()) {
            try (var processes = ProcessHandle.allProcesses()) {
                var candidate = OriginalGameFinder.find(processes.toList(), game, mod, user);
                if (candidate.isPresent()) return candidate.get();
            }
            Thread.sleep(1000);
        }
        return null;
    }
}
