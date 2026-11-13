package tw.handoff.viewer;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 區塊沒載入時用來比對常駐端那一行 [handoff-diag]：viewer 收到多少區塊封包、送出多少移動。 */
public final class ViewerDiag {
    private static final Logger LOG=LoggerFactory.getLogger("handoff-viewer");
    public static final AtomicInteger CHUNKS=new AtomicInteger(),CENTERS=new AtomicInteger(),FORGETS=new AtomicInteger(),MOVES=new AtomicInteger();
    public static volatile int centerX,centerZ,radius=-1;
    private static int ticks;private static String last="";
    public static void center(int x,int z){centerX=x;centerZ=z;CENTERS.incrementAndGet();}
    static void tick(Minecraft mc,long epoch){
        if(++ticks%100!=0||mc.player==null)return;
        int chunks=CHUNKS.getAndSet(0),centers=CENTERS.getAndSet(0),forgets=FORGETS.getAndSet(0),moves=MOVES.getAndSet(0);
        var pos=mc.player.chunkPosition();
        String state="epoch="+epoch+" player="+pos.x+","+pos.z+" center="+centerX+","+centerZ+" radius="+radius;
        if(chunks==0&&centers==0&&forgets==0&&moves==0&&state.equals(last))return;
        last=state;
        LOG.info("[handoff-diag] {} chunks={} centers={} forgets={} moves={}",state,chunks,centers,forgets,moves);
    }
    private ViewerDiag(){}
}
