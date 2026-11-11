package org.slf4j;
public final class LoggerFactory {
    public static Logger getLogger(String name) {
        return value -> System.out.println("[" + Thread.currentThread().getName() + "] [" + name + "] " + value);
    }
}
