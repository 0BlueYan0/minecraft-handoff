package net.fabricmc.loader.impl.launch.knot;

/** Test-only process fixture. Does not load Fabric or Minecraft. */
public final class KnotClient {
    public static void main(String[] args) throws Exception {
        System.out.println("READY");
        System.out.println("TOKEN_PRESERVED=" + "TEST_SECRET WITH SPACE".equals(System.getProperty("launcher.token")));
        System.out.println("MOD_PROPERTY=" + (System.getProperty("fabric.addMods") != null));
        System.out.flush();
        System.in.read();
    }
}
