package tw.handoff.relay.bridge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import static org.junit.jupiter.api.Assertions.*;

class CommandTreeFilterTest {
    /** Flashback 的 BetterColorArgument 這類只在客戶端註冊的參數型別。 */
    static final class ClientOnlyArgument implements ArgumentType<String> {
        public String parse(StringReader reader){return reader.readUnquotedString();}
    }

    @BeforeAll static void boot(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}

    private static CommandDispatcher<SharedSuggestionProvider> tree(){
        CommandDispatcher<SharedSuggestionProvider> d=new CommandDispatcher<>();
        d.register(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("give").then(RequiredArgumentBuilder.<SharedSuggestionProvider,Integer>argument("count",IntegerArgumentType.integer()).executes(c->1)));
        d.register(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("flashback").then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("color").then(RequiredArgumentBuilder.<SharedSuggestionProvider,String>argument("value",new ClientOnlyArgument()).executes(c->1))));
        d.register(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("execute").then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("run").redirect(d.getRoot())));
        d.register(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("fb").redirect(d.getRoot().getChild("flashback")));
        return d;
    }

    private static Set<CommandNode<?>> reachable(CommandNode<?> root){
        Set<CommandNode<?>> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        walk(root,seen);return seen;
    }
    private static void walk(CommandNode<?> node,Set<CommandNode<?>> seen){
        if(!seen.add(node))return;
        for(CommandNode<?> child:node.getChildren())walk(child,seen);
        if(node.getRedirect()!=null)walk(node.getRedirect(),seen);
    }

    private static boolean known(ArgumentType<?> type){return !(type instanceof ClientOnlyArgument);}

    @Test void unrecognizedArgumentDropsItsTopLevelCommand(){
        var copy=CommandTreeFilter.copy(tree().getRoot(),CommandTreeFilterTest::known);
        assertNotNull(copy.root().getChild("give"));
        assertNull(copy.root().getChild("flashback"));
        assertEquals(java.util.List.of("fb","flashback"),copy.dropped());
    }

    @Test void redirectToRootPointsAtTheNewRoot(){
        var original=tree().getRoot();
        var copy=CommandTreeFilter.copy(original,CommandTreeFilterTest::known);
        assertSame(copy.root(),copy.root().getChild("execute").getChild("run").getRedirect());
        var copied=reachable(copy.root());
        for(CommandNode<?> old:reachable(original))assertFalse(copied.contains(old),"old node "+old.getName()+" reachable from the copy");
    }

    private static LiteralArgumentBuilder<SharedSuggestionProvider> lit(String name){return LiteralArgumentBuilder.literal(name);}

    @Test void chainedRedirectsIntoDroppedCommandsAreAllDropped(){
        CommandDispatcher<SharedSuggestionProvider> d=tree();
        d.register(lit("a").redirect(d.getRoot().getChild("fb")));
        d.register(lit("c").redirect(d.getRoot().getChild("a")));
        var copy=CommandTreeFilter.copy(d.getRoot(),CommandTreeFilterTest::known);
        assertEquals(java.util.List.of("a","c","fb","flashback"),copy.dropped());
        assertNotNull(copy.root().getChild("give"));
    }

    @Test void childRedirectBackToItsAncestorIsKept(){
        CommandDispatcher<SharedSuggestionProvider> d=new CommandDispatcher<>();
        var loop=d.register(lit("loop").then(lit("again").executes(c->1)));
        loop.getChild("again").addChild(lit("back").redirect(loop).build());
        var copy=CommandTreeFilter.copy(d.getRoot(),t->true);
        var newLoop=copy.root().getChild("loop");
        assertSame(newLoop,newLoop.getChild("again").getChild("back").getRedirect());
        assertTrue(copy.dropped().isEmpty());
    }

    /** reviewer 第 1 輪重現的形狀：先建的頂層 e 底下 redirect 到後建的 t 的子節點，那個子節點又 redirect 回 t。 */
    @Test void redirectCycleDropsTheCommandsInsteadOfFailing(){
        CommandDispatcher<SharedSuggestionProvider> d=new CommandDispatcher<>();
        d.register(lit("give").executes(c->1));
        var e=d.register(lit("e").then(lit("ec").executes(c->1)));
        var t=d.register(lit("t").executes(c->1));
        var back=lit("back").redirect(t).build();
        t.addChild(back);
        e.getChild("ec").addChild(lit("r").redirect(back).build());
        var copy=assertDoesNotThrow(()->CommandTreeFilter.copy(d.getRoot(),x->true));
        assertNotNull(copy.root().getChild("give"));
        assertTrue(copy.dropped().contains("t"));
        assertDoesNotThrow(()->new ClientboundCommandsPacket(copy.root()));
    }

    @Test void copiedNodesKeepRequirementCommandForkAndSuggestions(){
        CommandDispatcher<SharedSuggestionProvider> d=new CommandDispatcher<>();
        com.mojang.brigadier.Command<SharedSuggestionProvider> run=c->7;
        java.util.function.Predicate<SharedSuggestionProvider> needs=s->false;
        com.mojang.brigadier.suggestion.SuggestionProvider<SharedSuggestionProvider> hints=(c,b)->b.buildFuture();
        com.mojang.brigadier.RedirectModifier<SharedSuggestionProvider> modifier=c->java.util.List.of();
        d.register(lit("p").requires(needs).executes(run)
            .then(RequiredArgumentBuilder.<SharedSuggestionProvider,Integer>argument("n",IntegerArgumentType.integer()).suggests(hints).executes(run))
            .then(lit("f").fork(d.getRoot(),modifier)));
        var p=CommandTreeFilter.copy(d.getRoot(),t->true).root().getChild("p");
        assertSame(needs,p.getRequirement());
        assertSame(run,p.getCommand());
        var n=(com.mojang.brigadier.tree.ArgumentCommandNode<SharedSuggestionProvider,?>)p.getChild("n");
        assertSame(hints,n.getCustomSuggestions());
        assertSame(run,n.getCommand());
        var f=p.getChild("f");
        assertTrue(f.isFork());
        assertSame(modifier,f.getRedirectModifier());
    }

    @Test void redirectToDroppedNodeIsDropped(){
        var copy=CommandTreeFilter.copy(tree().getRoot(),CommandTreeFilterTest::known);
        assertNull(copy.root().getChild("fb"));
    }

    @Test void everythingRecognizedKeepsEveryNode(){
        var original=tree().getRoot();
        var copy=CommandTreeFilter.copy(original,t->true);
        assertEquals(reachable(original).size(),reachable(copy.root()).size());
        assertTrue(copy.dropped().isEmpty());
    }

    @Test void filteredTreeSerializesWhereTheOriginalFails(){
        RootCommandNode<SharedSuggestionProvider> original=tree().getRoot();
        assertThrows(IllegalArgumentException.class,()->new ClientboundCommandsPacket(original));
        var copy=CommandTreeFilter.copy(original,CommandTreeFilter::serializable);
        assertDoesNotThrow(()->new ClientboundCommandsPacket(copy.root()));
    }
}
