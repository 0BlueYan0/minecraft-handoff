package tw.handoff.launcher;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.Locale;
import java.io.IOException;

final class OriginalGameFinder {
    record Match(ProcessHandle process, LaunchPlan plan) {}
    static Optional<Match> find(List<ProcessHandle> processes, Path game, Path mod, String user) throws IOException {
        Match selected = null;
        for (ProcessHandle process : processes) {
            ProcessHandle.Info info;
            try { info = process.info(); }
            catch (RuntimeException inaccessible) { continue; }
            if (!info.user().orElse("").equals(user)) continue;
            String executable = info.command().orElse("");
            if (executable.isEmpty()) continue;
            String file = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
            if (!file.equals("java") && !file.equals("java.exe")) continue;
            var arguments = info.arguments();
            if (arguments.isEmpty()) continue;
            List<String> values = Arrays.asList(arguments.get());
            if (!LaunchPlan.matches(values, game)) continue;
            if (selected != null) throw new IllegalStateException("More than one matching game is open");
            selected = new Match(process, LaunchPlan.create(executable, values, game, mod));
        }
        return Optional.ofNullable(selected);
    }
}
