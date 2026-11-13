package tw.handoff.relay.bridge;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 常駐端的指令樹含有 Fabric 併進來的客戶端指令。其中有自訂參數型別的（2026-11-13 Mac 實服是 Flashback 的 BetterColorArgument）
 * 沒有註冊在 ArgumentTypeInfos，整棵樹組成 ClientboundCommandsPacket 時丟例外，viewer 每次加入都斷在 Snapshot unavailable。
 * 拿掉那些頂層指令不會讓 viewer 少指令，viewer 自己的 Fabric 收到封包後會把它的客戶端指令再併進去。
 */
final class CommandTreeFilter {
    record Result<S>(RootCommandNode<S> root,List<String> dropped){}

    private CommandTreeFilter(){}

    static boolean serializable(ArgumentType<?> type){
        return net.minecraft.commands.synchronization.ArgumentTypeInfos.isClassRecognized(type.getClass());
    }

    /**
     * 深複製，redirect 改指新樹的節點，指向舊 root 的改指新 root。
     * 頂層指令底下只要有一個參數型別不被 recognized 接受，或有 redirect 指向被丟掉的頂層指令，整個頂層指令就丟掉。
     */
    static <S> Result<S> copy(RootCommandNode<S> root,Predicate<ArgumentType<?>> recognized){
        Map<CommandNode<S>,CommandNode<S>> owner=new IdentityHashMap<>();
        for(CommandNode<S> top:root.getChildren())own(top,top,owner);
        Set<CommandNode<S>> kept=identitySet();
        for(CommandNode<S> top:root.getChildren())if(recognizedBelow(top,recognized,identitySet()))kept.add(top);
        while(true){
            dropRedirectsIntoDropped(root,kept,owner);
            RootCommandNode<S> copy=new RootCommandNode<>();
            Map<CommandNode<S>,CommandNode<S>> mapped=new IdentityHashMap<>();
            mapped.put(root,copy);
            try{
                for(CommandNode<S> top:root.getChildren())if(kept.contains(top))copy.addChild(build(top,mapped,identitySet()));
            }catch(RedirectCycle cycle){
                // 這種樹要 mod 事後 addChild 才建得出來。丟掉卡住的那個頂層指令重建，不讓整個快照失敗。
                kept.remove(owner.get(cycle.node));
                continue;
            }
            List<String> dropped=new ArrayList<>();
            for(CommandNode<S> top:root.getChildren())if(!kept.contains(top))dropped.add(top.getName());
            Collections.sort(dropped);
            return new Result<>(copy,List.copyOf(dropped));
        }
    }

    private static final class RedirectCycle extends RuntimeException {
        final CommandNode<?> node;
        RedirectCycle(CommandNode<?> node){super(null,null,false,false);this.node=node;}
    }

    /** redirect 可能指到另一個被丟掉的頂層指令，丟掉一個之後要重新檢查，直到沒有變化。 */
    private static <S> void dropRedirectsIntoDropped(RootCommandNode<S> root,Set<CommandNode<S>> kept,Map<CommandNode<S>,CommandNode<S>> owner){
        boolean changed=true;
        while(changed){
            changed=false;
            for(var it=kept.iterator();it.hasNext();){
                if(!redirectsKept(it.next(),root,kept,owner,identitySet())){it.remove();changed=true;}
            }
        }
    }

    private static <S> Set<CommandNode<S>> identitySet(){return Collections.newSetFromMap(new IdentityHashMap<>());}

    private static <S> void own(CommandNode<S> node,CommandNode<S> top,Map<CommandNode<S>,CommandNode<S>> owner){
        if(owner.putIfAbsent(node,top)!=null)return;
        for(CommandNode<S> child:node.getChildren())own(child,top,owner);
    }

    private static <S> boolean recognizedBelow(CommandNode<S> node,Predicate<ArgumentType<?>> recognized,Set<CommandNode<S>> seen){
        if(!seen.add(node))return true;
        if(node instanceof ArgumentCommandNode<S,?> argument&&!recognized.test(argument.getType()))return false;
        for(CommandNode<S> child:node.getChildren())if(!recognizedBelow(child,recognized,seen))return false;
        return true;
    }

    private static <S> boolean redirectsKept(CommandNode<S> node,RootCommandNode<S> root,Set<CommandNode<S>> kept,Map<CommandNode<S>,CommandNode<S>> owner,Set<CommandNode<S>> seen){
        if(!seen.add(node))return true;
        CommandNode<S> target=node.getRedirect();
        if(target!=null&&target!=root&&!kept.contains(owner.get(target)))return false;
        for(CommandNode<S> child:node.getChildren())if(!redirectsKept(child,root,kept,owner,seen))return false;
        return true;
    }

    /** brigadier 節點的 redirect 只能在 build 前設定，所以 redirect 目標要先建好。節點在建出來之後、處理子節點之前就登記，子節點 redirect 回祖先時拿得到。 */
    private static <S> CommandNode<S> build(CommandNode<S> node,Map<CommandNode<S>,CommandNode<S>> mapped,Set<CommandNode<S>> building){
        CommandNode<S> existing=mapped.get(node);
        if(existing!=null)return existing;
        if(!building.add(node))throw new RedirectCycle(node);
        ArgumentBuilder<S,?> builder=node.createBuilder();
        CommandNode<S> target=node.getRedirect();
        if(target!=null){
            CommandNode<S> newTarget=build(target,mapped,building);
            existing=mapped.get(node);
            if(existing!=null){building.remove(node);return existing;}
            builder.forward(newTarget,node.getRedirectModifier(),node.isFork());
        }
        CommandNode<S> made=builder.build();
        mapped.put(node,made);
        building.remove(node);
        for(CommandNode<S> child:node.getChildren())made.addChild(build(child,mapped,building));
        return made;
    }
}
