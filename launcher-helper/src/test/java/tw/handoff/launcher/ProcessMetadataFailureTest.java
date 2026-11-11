package tw.handoff.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProcessMetadataFailureTest {
    @TempDir Path temp;
    @Test void inaccessibleUnrelatedProcessDoesNotAbortDiscovery() throws Exception {
        ProcessHandle inaccessible = (ProcessHandle) Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(),
                new Class<?>[]{ProcessHandle.class}, (proxy, method, args) -> {
                    throw new IllegalStateException("simulated OS metadata denial");
                });
        assertTrue(OriginalGameFinder.find(List.of(inaccessible), temp, temp.resolve("mod.jar"), "owner").isEmpty());
    }
}
