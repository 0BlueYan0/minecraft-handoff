package fr.sao.net;
import java.util.*;
/** Test-owned contract fixture; no vendor implementation. */
public final class Mux {public static final Set<String> SERVED=new HashSet<>(Set.of("saocore:equipment"));public static Set<String> sendable=Set.of("saogui:main");public static final String SECRET="MUST_NEVER_BE_CAPTURED";}
