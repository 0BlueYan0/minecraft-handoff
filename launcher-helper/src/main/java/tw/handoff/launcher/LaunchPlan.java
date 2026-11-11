package tw.handoff.launcher;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

final class LaunchPlan {
    private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    private final List<String> command;
    private final Path workingDirectory;

    private LaunchPlan(List<String> command, Path workingDirectory) {
        this.command = List.copyOf(command);
        this.workingDirectory = workingDirectory;
    }

    static boolean matches(List<String> arguments, Path game) {
        int main = arguments.indexOf(KNOT);
        if (main < 0 || main != arguments.lastIndexOf(KNOT)) return false;
        int index = arguments.indexOf("--gameDir");
        if (index <= main || index != arguments.lastIndexOf("--gameDir") || index + 1 >= arguments.size()) return false;
        try {
            Path actual = Path.of(arguments.get(index + 1));
            return actual.isAbsolute() && actual.toRealPath().equals(game.toRealPath());
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    static LaunchPlan create(String javaExecutable, List<String> arguments, Path game, Path mod) throws IOException {
        if (!matches(arguments, game)) throw new IllegalArgumentException("Cannot identify the original game arguments");
        if (arguments.stream().anyMatch(arg -> arg.startsWith("@") || arg.startsWith("-Dfabric.addMods")
                || arg.equals("--add-mods") || arg.startsWith("--add-mods=") || arg.startsWith("--fabric.addMods"))) {
            throw new IllegalArgumentException("Additional mod arguments or argument files require separate review");
        }
        if (!Files.isRegularFile(mod)) throw new IllegalArgumentException("External diagnostic JAR is missing");
        Path actualGame = game.toRealPath();
        Path actualMod = mod.toRealPath();
        if (actualMod.startsWith(actualGame)) throw new IllegalArgumentException("Diagnostic JAR must be outside the managed instance");
        String path = actualMod.toString();
        if (path.contains(java.io.File.pathSeparator)) throw new IllegalArgumentException("Mod path contains a path-list separator");
        var command = new ArrayList<String>();
        command.add(javaExecutable);
        command.add("-Dfabric.addMods=" + path);
        command.addAll(arguments);
        return new LaunchPlan(command, actualGame);
    }

    static Map<String,String> childEnvironment(Map<String,String> environment) {
        var result = new HashMap<>(environment);
        result.remove("JAVA_TOOL_OPTIONS");
        result.remove("JDK_JAVA_OPTIONS");
        result.remove("_JAVA_OPTIONS");
        return result;
    }

    List<String> command() { return command; }
    Path workingDirectory() { return workingDirectory; }
    @Override public String toString() { return "LaunchPlan[arguments redacted]"; }
}
