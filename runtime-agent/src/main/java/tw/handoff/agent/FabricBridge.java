package tw.handoff.agent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

/** Uses only public Fabric callbacks. No class redefinition, packet hooks, or validation changes. */
final class FabricBridge {
    static boolean isExpectedDirectory(Object client, java.nio.file.Path expected) {
        for (String name : new String[]{"field_1697", "gameDirectory"}) {
            try {
                Object value = client.getClass().getField(name).get(client);
                if (value instanceof java.io.File directory && directory.toPath().toRealPath().equals(expected.toRealPath())) return true;
            } catch (ReflectiveOperationException | java.io.IOException ignored) { }
        }
        return false;
    }
    private record Binding(Object event, Method register, Class<?> callback) {
        void add(Runnable action) throws ReflectiveOperationException {
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "MinecraftHandoffDiagnosticListener";
                        default -> null;
                    };
                }
                action.run();
                return null;
            });
            register.invoke(event, listener);
        }
    }

    private static Binding binding(ClassLoader loader, String owner, String field, String callback) throws ReflectiveOperationException {
        Object event = Class.forName(owner, true, loader).getField(field).get(null);
        Method register = Class.forName("net.fabricmc.fabric.api.event.Event", true, loader).getMethod("register", Object.class);
        return new Binding(event, register, Class.forName(callback, true, loader));
    }

    static void install(Object client, ClassLoader loader) throws ReflectiveOperationException {
        Executor executor = (Executor) client;
        String connections = "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents";
        String ticks = "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents";
        // Resolve every public dependency before registering listeners.
        Binding join = binding(loader, connections, "JOIN", connections + "$Join");
        Binding disconnect = binding(loader, connections, "DISCONNECT", connections + "$Disconnect");
        Binding tick = binding(loader, ticks, "END_CLIENT_TICK", ticks + "$EndTick");
        Method network = method(client.getClass(), "method_1562", "getConnection");
        Object logger = Class.forName("org.slf4j.LoggerFactory", true, loader).getMethod("getLogger", String.class)
                .invoke(null, "minecraft-handoff-agent");
        Method info = Class.forName("org.slf4j.Logger", true, loader).getMethod("info", String.class);
        info.invoke(logger, "[minecraft-handoff-agent] Installing observer in existing game process");
        ObserverGuard guard = new ObserverGuard(type -> {
            RuntimeAgent.observerDisabled(type);
            try { info.invoke(logger, "[minecraft-handoff-agent] OBSERVER_DISABLED:" + type); }
            catch (ReflectiveOperationException unavailable) { System.err.println("[minecraft-handoff-agent] OBSERVER_DISABLED:LogSink"); }
        });
        AgentState state = new AgentState(System::nanoTime, value -> {
            try { info.invoke(logger, "[minecraft-handoff-agent] " + value); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Diagnostic log sink unavailable"); }
        });
        join.add(() -> guard.run(() -> executor.execute(() -> guard.run(state::joined))));
        disconnect.add(() -> guard.run(() -> executor.execute(() -> guard.run(state::disconnected))));
        tick.add(() -> guard.run(state::tick));
        state.start(network.invoke(client) != null);
    }

    static Method method(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try { return type.getMethod(name); } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException("Supported public Minecraft accessor unavailable");
    }
}
