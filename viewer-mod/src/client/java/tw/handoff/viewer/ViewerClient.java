package tw.handoff.viewer;
import java.net.InetSocketAddress;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.LoggerFactory;
import tw.handoff.relay.core.EndpointPolicy;
import tw.handoff.relay.core.ControlFrame;
import tw.handoff.relay.core.ControlWire;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.GameProtocols;
import io.netty.buffer.Unpooled;
import java.util.Base64;
import java.util.Set;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ViewerClient implements ClientModInitializer {
    private static final EndpointPolicy ENDPOINT=new EndpointPolicy(Integer.parseInt(System.getProperty("handoff.viewerPort","0")));
    public static final boolean THIN=Boolean.parseBoolean(System.getProperty("handoff.viewerMode","true"));
    public static final boolean LAB=(Boolean.getBoolean("handoff.packetLab")||Boolean.getBoolean("handoff.generic"))&&!Boolean.getBoolean("handoff.labHost");
    private static long epoch,sequence;private static boolean takeDown,releaseDown;
    private static java.nio.channels.FileChannel gameLockFile;private static java.nio.channels.FileLock gameLock;
    public static String title(){return !THIN&&!LAB?"Minecraft Handoff - HOST FIXTURE":epoch>0?"Minecraft Handoff - CONTROLLER (F7 release)":"Minecraft Handoff - SPECTATOR (F6 take control)";}
    private static final Set<String> ACTIONS=Set.of("chat","chat_command","chat_command_signed","container_click","container_close","player_action","client_command");
    public static void check(InetSocketAddress address){if(!ENDPOINT.allows(address))throw new IllegalStateException("Viewer can only connect to its local handoff endpoint");}
    public void onInitializeClient(){
        try{gameLockFile=java.nio.channels.FileChannel.open(Minecraft.getInstance().gameDirectory.toPath().resolve(".game-lock"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.WRITE);gameLock=gameLockFile.tryLock();if(gameLock==null)throw new IllegalStateException("Viewer already running");}catch(java.io.IOException error){throw new IllegalStateException("Viewer profile lock unavailable",error);}
        LoggerFactory.getLogger("handoff-viewer").info("Viewer destination guard enabled");
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{epoch=sequence=0;JOIN_STATE.onJoin();client.getWindow().setTitle(THIN?"Minecraft Handoff - VIEWER (loading)":"Minecraft Handoff - HOST FIXTURE");});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{epoch=sequence=0;JOIN_STATE.onDisconnect();});
        ClientTickEvents.END_CLIENT_TICK.register(ViewerClient::tick);
    }
    private static final ViewerJoinState JOIN_STATE=new ViewerJoinState(ViewerClient::send,(snapshot,elapsed)->{var plan=tw.handoff.sao.SaoSnapshot.prepare(Minecraft.getInstance().getClass().getClassLoader(),snapshot,elapsed);return plan::apply;},command->Minecraft.getInstance().execute(command),System::nanoTime,text->Minecraft.getInstance().execute(()->Minecraft.getInstance().gui.setOverlayMessage(Component.literal(text),false)));
    public static void receive(String text){if((!THIN&&!LAB)||!text.startsWith(ControlWire.PREFIX))return;if(JOIN_STATE.receive(text,System.nanoTime()))return;Minecraft mc=Minecraft.getInstance();mc.execute(()->{
        try{
            if(text.startsWith(ControlWire.PREFIX+"R:")){epoch=Long.parseLong(text.substring((ControlWire.PREFIX+"R:").length()));sequence=0;mc.getWindow().setTitle(epoch>0?"Minecraft Handoff - CONTROLLER (F7 release)":"Minecraft Handoff - SPECTATOR (F6 take control)");mc.gui.setOverlayMessage(Component.literal(epoch>0?"已取得控制權 · F7 釋放":"觀察中 · F6 接管"),false);}
            else if(text.startsWith(ControlWire.PREFIX+"E:"))mc.gui.setOverlayMessage(Component.literal(text.substring((ControlWire.PREFIX+"E:").length())),false);
            else if(text.startsWith(ControlWire.PREFIX+"P:")&&mc.player!=null){String[] p=text.substring((ControlWire.PREFIX+"P:").length()).split(":");if(p.length!=7)return;
                double x=Double.parseDouble(p[0]),y=Double.parseDouble(p[1]),z=Double.parseDouble(p[2]);float yaw=Float.parseFloat(p[3]),pitch=Float.parseFloat(p[4]);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
                mc.player.setPos(x,y,z);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mc.player.setOnGround(p[5].equals("1"));
                if(epoch==0){mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.getInventory().selected=Integer.parseInt(p[6]);}
            }
        }catch(RuntimeException invalid){epoch=0;}
    });}
    private static void tick(Minecraft mc){if((!THIN&&!LAB)||mc.player==null||mc.getConnection()==null)return;
        long w=mc.getWindow().getWindow();boolean take=GLFW.glfwGetKey(w,GLFW.GLFW_KEY_F6)==GLFW.GLFW_PRESS,release=GLFW.glfwGetKey(w,GLFW.GLFW_KEY_F7)==GLFW.GLFW_PRESS;
        if(take&&!takeDown)send(ControlWire.PREFIX+"TAKE");if(release&&!releaseDown)send(ControlWire.PREFIX+"RELEASE");takeDown=take;releaseDown=release;
        if(epoch<=0)return;
        var o=mc.options;int keys=(o.keyUp.isDown()?1:0)|(o.keyDown.isDown()?2:0)|(o.keyLeft.isDown()?4:0)|(o.keyRight.isDown()?8:0)|(o.keyJump.isDown()?16:0)|(o.keyShift.isDown()?32:0)|(o.keySprint.isDown()?64:0)|(o.keyAttack.isDown()?128:0)|(o.keyUse.isDown()?256:0);
        send(new ControlFrame(epoch,++sequence,keys,mc.player.getYRot(),mc.player.getXRot(),mc.player.getInventory().selected,mc.screen!=null||!mc.isWindowActive()).encode());
    }
    private static void send(String text){var c=Minecraft.getInstance().getConnection();if(c!=null)c.send(new ServerboundCustomPayloadPacket(new BrandPayload(text)));}
    /** GUI/chat intents travel with the same generation as held-key frames. No viewer signature is reused. */
    @SuppressWarnings("unchecked") public static boolean wrapAction(Packet<?> packet){
        if(LAB){
            if(packet instanceof ServerboundCustomPayloadPacket c&&c.payload() instanceof BrandPayload b&&b.brand().startsWith(ControlWire.PREFIX))return false;
            if(Minecraft.getInstance().getConnection()==null)return false;
            String name=packet.type().id().getPath();if(java.util.Set.of("keep_alive","pong","accept_teleportation","player_loaded","chunk_batch_received","client_tick_end","chat_ack","client_information").contains(name)||name.contains("cookie"))return false;
        }else if(!THIN||!ACTIONS.contains(packet.type().id().getPath()))return false;
        Minecraft mc=Minecraft.getInstance();if(epoch<=0||mc.getConnection()==null)return true;
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.getConnection().registryAccess());
        try{((net.minecraft.network.codec.StreamCodec)GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(mc.getConnection().registryAccess())).codec()).encode(buffer,packet);
            if(buffer.readableBytes()>16000)return true;byte[] bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);String value=ControlWire.action(epoch,++sequence,Base64.getEncoder().encodeToString(bytes)).encode();send(LAB?value.replaceFirst("HANDOFF2:A:","HANDOFF2:G:"):value);
        }finally{buffer.release();}return true;
    }
}
