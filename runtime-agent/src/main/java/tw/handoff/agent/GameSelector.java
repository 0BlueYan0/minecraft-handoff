package tw.handoff.agent;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.io.IOException;
final class GameSelector {
    static boolean matches(List<String> arguments, Path expected) {
        int knot = arguments.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient");
        int game = arguments.indexOf("--gameDir");
        if (knot < 0 || game <= knot || game != arguments.lastIndexOf("--gameDir") || game + 1 >= arguments.size()) return false;
        try {
            Path actual = Path.of(arguments.get(game + 1));
            return actual.isAbsolute() && actual.toRealPath().equals(expected.toRealPath());
        } catch (IOException | IllegalArgumentException failure) { return false; }
    }

    static ProcessHandle find(Path game) throws IOException {
        String user = ProcessHandle.current().info().user().orElseThrow();
        ProcessHandle selected = null;
        try (var processes = ProcessHandle.allProcesses()) {
            for (ProcessHandle process : processes.toList()) {
                ProcessHandle.Info info;
                try { info = process.info(); } catch (RuntimeException inaccessible) { continue; }
                if (!info.user().orElse("").equals(user) || info.command().isEmpty() || info.arguments().isEmpty()) continue;
                String file = Path.of(info.command().get()).getFileName().toString();
                if (!file.equals("java") && !file.equals("java.exe")) continue;
                if (!matches(Arrays.asList(info.arguments().get()), game)) continue;
                if (selected != null) throw new IllegalStateException("Multiple game instances");
                selected = process;
            }
        }
        return selected;
    }
}
