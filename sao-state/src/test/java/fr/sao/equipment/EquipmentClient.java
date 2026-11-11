package fr.sao.equipment;
import java.util.*;
public final class EquipmentClient {public record State(String name,int level){}public record Item(String name,int count){}public static State state=new State("fixture",5);public static Object presets=null;public static String listSlot="hand";public static List<Item> list=List.of(new Item("fixture",1));public static final Map<String,List<Item>> lists=new HashMap<>(Map.of("hand",list));public static boolean listBatch=true,cursorOps=true;public static int version;}
