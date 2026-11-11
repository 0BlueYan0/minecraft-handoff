package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlWireTest {
 @Test void framesRoundTripAndBoundUntrustedValues(){
  var f=new ControlFrame(12,3,ControlFrame.FORWARD|ControlFrame.ATTACK,21.5f,-12,8,false);
  assertEquals(f,ControlFrame.decode(f.encode()));
  assertThrows(IllegalArgumentException.class,()->new ControlFrame(1,1,0,Float.NaN,0,0,false));
  assertThrows(IllegalArgumentException.class,()->new ControlFrame(1,1,0,0,91,0,false));
  assertThrows(IllegalArgumentException.class,()->ControlFrame.decode(f.encode()+":extra"));
 }
 @Test void actionEnvelopeRejectsUnknownOrOversizedPayload(){
  assertEquals("YQ==",ControlWire.action(4,5,"YQ==").data());
  assertThrows(IllegalArgumentException.class,()->ControlWire.action(0,5,"YQ=="));
  assertThrows(IllegalArgumentException.class,()->ControlWire.action(4,5,"x".repeat(22001)));
 }
}
