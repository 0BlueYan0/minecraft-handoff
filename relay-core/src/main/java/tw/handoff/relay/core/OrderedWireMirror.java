package tw.handoff.relay.core;
import java.util.function.*;
/** One inbound wire path, including bundle delimiters; never mix raw children with decoded bundles. */
public final class OrderedWireMirror {
 private final int customId;private final IntPredicate forward;private final PacketJournal journal;private final Consumer<byte[]> sink;
 public OrderedWireMirror(int customId,IntPredicate forward,PacketJournal journal,Consumer<byte[]> sink){this.customId=customId;this.forward=forward;this.journal=journal;this.sink=sink;}
 public void receive(byte[] frame){int id=OpaqueFrames.readVarInt(frame,new int[]{0});if(!forward.test(id))return;
  if(id==customId){var view=OpaqueFrames.inspect(frame);if(!OpaqueFrames.gameplayChannel(view.channel()))return;journal.append(frame);}
  sink.accept(frame.clone());
 }
}
