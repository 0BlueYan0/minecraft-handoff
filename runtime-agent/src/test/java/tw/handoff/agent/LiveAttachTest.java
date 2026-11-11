package tw.handoff.agent;

import com.sun.tools.attach.VirtualMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class LiveAttachTest {
    @TempDir Path temp;
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < end) { if (condition.getAsBoolean()) return; Thread.sleep(30); }
        fail("Timed out waiting for the controlled agent fixture");
    }
    private static String read(Path p) {
        try { return Files.exists(p) ? Files.readString(p) : ""; } catch (Exception e) { return ""; }
    }
    @Test void attachesInsideExistingJvmAndIsIdempotentAcrossClassLoaders() throws Exception {
        Path fixture = Path.of(AgentTargetMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path heartbeat = temp.resolve("heartbeat"), stop = temp.resolve("stop"), action = temp.resolve("action"), log = temp.resolve("target.log");
        Process target = new ProcessBuilder(javaBinary, "-XX:+EnableDynamicAgentLoading", "-Dfixture.gameDir=" + temp, "-cp", fixture.toString(),
                AgentTargetMain.class.getName(), fixture.toString(), heartbeat.toString(), stop.toString(), action.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            await(() -> read(heartbeat).endsWith(":0,0,0"));
            Path status = Files.createFile(temp.resolve("agent.status"));
            String options = Base64.getUrlEncoder().withoutPadding().encodeToString(status.toString().getBytes(StandardCharsets.UTF_8))
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(temp.toString().getBytes(StandardCharsets.UTF_8));
            Process helper = new ProcessBuilder(javaBinary, "--add-modules", "jdk.attach", "-jar", System.getProperty("handoff.agentJar"),
                    "--game-dir", temp.toString(), "--pid", Long.toString(target.pid())).redirectErrorStream(true).start();
            assertTrue(helper.waitFor(20, TimeUnit.SECONDS));
            String helperOutput = new String(helper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, helper.exitValue(), helperOutput);
            assertTrue(helperOutput.contains("AGENT_READY"));
            assertTrue(target.isAlive());
            await(() -> read(heartbeat).equals(target.pid() + ":1,1,1"));
            assertTrue(read(log).contains("ATTACHED_IN_GAME"));
            Files.writeString(action, "disconnect");
            await(() -> read(log).contains("\"state\":\"DISCONNECTED\""));
            assertFalse(read(log).contains("[FixtureNetty] [minecraft-handoff-agent]"));
            Files.writeString(status, "");
            VirtualMachine vm = VirtualMachine.attach(Long.toString(target.pid()));
            try { vm.loadAgent(System.getProperty("handoff.agentJar"), options); } finally { vm.detach(); }
            await(() -> read(status).equals("ALREADY_READY"));
            assertEquals(target.pid() + ":1,1,1", read(heartbeat));
            Path wrongGame = Files.createDirectory(temp.resolve("other-instance"));
            String wrongOptions = Base64.getUrlEncoder().withoutPadding().encodeToString(status.toString().getBytes(StandardCharsets.UTF_8))
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(wrongGame.toString().getBytes(StandardCharsets.UTF_8));
            vm = VirtualMachine.attach(Long.toString(target.pid()));
            try { vm.loadAgent(System.getProperty("handoff.agentJar"), wrongOptions); } finally { vm.detach(); }
            await(() -> read(status).equals("FAILED:WRONG_GAME_DIRECTORY"));
            assertEquals(target.pid() + ":1,1,1", read(heartbeat));
        } finally {
            Files.writeString(stop, "stop");
            if (!target.waitFor(3, TimeUnit.SECONDS)) target.destroyForcibly().waitFor();
        }
    }

    @Test void disabledAgentLoadingFailsWithoutStoppingOrChangingTheTarget() throws Exception {
        Path fixture = Path.of(AgentTargetMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path heartbeat = temp.resolve("heartbeat"), stop = temp.resolve("stop"), action = temp.resolve("action");
        Process target = new ProcessBuilder(javaBinary, "-XX:-EnableDynamicAgentLoading", "-Dfixture.gameDir=" + temp,
                "-cp", fixture.toString(), AgentTargetMain.class.getName(), fixture.toString(), heartbeat.toString(), stop.toString(), action.toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("target.log").toFile()).start();
        try {
            await(() -> read(heartbeat).endsWith(":0,0,0"));
            Process helper = new ProcessBuilder(javaBinary, "--add-modules", "jdk.attach", "-jar", System.getProperty("handoff.agentJar"),
                    "--game-dir", temp.toString(), "--pid", Long.toString(target.pid())).redirectErrorStream(true).start();
            assertTrue(helper.waitFor(20, TimeUnit.SECONDS));
            String output = new String(helper.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertNotEquals(0, helper.exitValue());
            assertTrue(output.contains("ATTACH_FAILED: AgentLoadException"), output);
            assertTrue(target.isAlive());
            assertEquals(target.pid() + ":0,0,0", read(heartbeat));
        } finally {
            Files.writeString(stop, "stop");
            if (!target.waitFor(3, TimeUnit.SECONDS)) target.destroyForcibly().waitFor();
        }
    }

    @Test void rejectedSchedulingIsReportedAsFailureOnSubsequentAttach() throws Exception {
        Path fixture = Path.of(AgentTargetMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path heartbeat = temp.resolve("heartbeat"), stop = temp.resolve("stop"), action = temp.resolve("action");
        Process target = new ProcessBuilder(javaBinary, "-XX:+EnableDynamicAgentLoading", "-Dfixture.rejectNextExecute=true", "-Dfixture.gameDir=" + temp,
                "-cp", fixture.toString(), AgentTargetMain.class.getName(), fixture.toString(), heartbeat.toString(), stop.toString(), action.toString())
                .redirectErrorStream(true).redirectOutput(temp.resolve("target.log").toFile()).start();
        try {
            await(() -> read(heartbeat).endsWith(":0,0,0"));
            Path channel = Files.createFile(temp.resolve("agent.status"));
            String options = Base64.getUrlEncoder().withoutPadding().encodeToString(channel.toString().getBytes(StandardCharsets.UTF_8))
                    + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(temp.toString().getBytes(StandardCharsets.UTF_8));
            VirtualMachine vm = VirtualMachine.attach(Long.toString(target.pid()));
            try {
                vm.loadAgent(System.getProperty("handoff.agentJar"), options);
                assertTrue(read(channel).startsWith("FAILED:"));
                Files.writeString(channel, "");
                vm.loadAgent(System.getProperty("handoff.agentJar"), options);
                assertTrue(read(channel).startsWith("FAILED:"), read(channel));
            } finally { vm.detach(); }
            assertTrue(target.isAlive());
            assertEquals(target.pid() + ":0,0,0", read(heartbeat));
        } finally {
            Files.writeString(stop, "stop");
            if (!target.waitFor(3, TimeUnit.SECONDS)) target.destroyForcibly().waitFor();
        }
    }
}
