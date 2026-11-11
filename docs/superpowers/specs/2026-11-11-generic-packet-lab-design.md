# Generic packet and late-join feasibility experiment

Approved in chat: normal native client/mod behavior, one original authenticated transport, generic gameplay packet routing, and an independent test of pre-join state reconstruction. This is a bounded architecture experiment, not acceptance of a finished SAO relay.

Use isolated private fixtures only. Enable experimental code through `handoff.packetLab=true` plus the existing owned-viewer marker. Never enable this experiment in the real SAO process. Do not change official launcher verification.

Gate 1: a separate Fabric fixture mod defines a custom payload/channel unknown to relay code. The native viewer emits it through its ordinary networking API; the relay transports encoded bytes with epoch/sequence ownership. The backend receives the exact data and replies. Capture S2C custom payload frames before decoding so unregistered host payloads cannot be discarded. Keep the normal controller's native action/animation code enabled. Connection-owned messages remain local; unknown gameplay is not enumerated as semantic actions.

Gate 2: capture a bounded ordered custom-payload journal before the viewer joins. Compare a fresh viewer with host state after replay: absolute persistent state, receipt-relative expiring effects, and client-only state that never crossed the wire. Report supported cases and counterexamples separately. Replay is experimental; failure of a general-state assertion is useful evidence and must not be hidden by adding a channel-specific relay adapter.

The journal is bounded to 8 MiB/8192 frames and invalidates catch-up after overflow. Original transport remains live on experimental viewer failure. No credential-bearing payload is journaled; the experiment denies launcher authentication and connection ownership channels.

Deliver evidence and a recommendation. Mac/Windows final runtime delivery stays pending architecture selection and SAO compatibility acceptance.
