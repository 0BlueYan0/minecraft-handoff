package tw.handoff.relay.core;
public final class PeerLifecycle {
    public enum Phase { HANDSHAKE, LOGIN_HELLO, LOGIN_ACK, CONFIG_PACKS, CONFIG_FINISH, PLAY_LOADING, ACTIVE, CLOSED }
    private Phase phase=Phase.HANDSHAKE;
    private boolean snapshot,loaded,notified;
    public synchronized Phase phase(){return phase;}
    public synchronized boolean active(){return phase==Phase.ACTIVE;}
    public synchronized boolean claimReadyNotification(){if(!active()||notified)return false;notified=true;return true;}
    private void change(Phase expected,Phase next){if(phase!=expected)throw new IllegalStateException("Invalid relay phase");phase=next;}
    public synchronized void handshake(){change(Phase.HANDSHAKE,Phase.LOGIN_HELLO);}
    public synchronized void hello(){change(Phase.LOGIN_HELLO,Phase.LOGIN_ACK);}
    public synchronized void loginAcknowledged(){change(Phase.LOGIN_ACK,Phase.CONFIG_PACKS);}
    public synchronized void packsSelected(){change(Phase.CONFIG_PACKS,Phase.CONFIG_FINISH);}
    public synchronized void configurationFinished(){change(Phase.CONFIG_FINISH,Phase.PLAY_LOADING);}
    public synchronized void snapshotSent(){if(phase!=Phase.PLAY_LOADING)throw new IllegalStateException("Snapshot outside loading");snapshot=true;ready();}
    public synchronized void playerLoaded(){if(phase==Phase.ACTIVE)return;if(phase!=Phase.PLAY_LOADING)throw new IllegalStateException("Readiness outside loading");loaded=true;ready();}
    private void ready(){if(snapshot&&loaded)phase=Phase.ACTIVE;}
    public synchronized void close(){phase=Phase.CLOSED;}
}
