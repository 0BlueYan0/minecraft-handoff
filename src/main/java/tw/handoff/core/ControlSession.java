package tw.handoff.core;

import java.util.Optional;

/** Single-coordinator-thread ownership model; gameplay integration supplies synchronization boundaries. */
public final class ControlSession {
    public record Ticket(String clientId, long preparationSequence) {}
    public record Lease(String clientId, long epoch) {}

    private long preparationSequence;
    private long ownershipEpoch;
    private Ticket pending;
    private Lease controller;

    /** Starts one preparation, superseding any previous candidate without revoking the controller. */
    public Ticket prepare(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        preparationSequence = Math.incrementExact(preparationSequence);
        pending = new Ticket(clientId, preparationSequence);
        return pending;
    }

    /** Call only after the gameplay layer has established its final synchronization boundary. */
    public boolean ready(Ticket ticket) {
        if (ticket == null || ticket != pending) return false;
        ownershipEpoch = Math.incrementExact(ownershipEpoch);
        controller = new Lease(ticket.clientId(), ownershipEpoch);
        pending = null;
        return true;
    }

    public void cancel(Ticket ticket) {
        if (ticket != null && ticket == pending) pending = null;
    }

    /** IDs identify connections, not Minecraft accounts; a future network adapter must discard stale callbacks. */
    public void disconnect(String clientId) {
        if (pending != null && pending.clientId().equals(clientId)) pending = null;
        if (controller != null && controller.clientId().equals(clientId)) controller = null;
    }

    public boolean acceptsInput(String clientId, long epoch) {
        return controller != null && controller.clientId().equals(clientId) && controller.epoch() == epoch;
    }

    public Optional<Lease> controller() {
        return Optional.ofNullable(controller);
    }
}
