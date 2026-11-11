package fr.sao.saoui.skills;
import java.util.*;
public final class SkillStatusClient {public static int slot=1,level=5;public static String skillName="test";public static Map<Integer,String> allSkills=new HashMap<>(Map.of(1,"test"));public static Map<String,Double> cooldowns=new HashMap<>(Map.of("test",10.0)),durations=new HashMap<>(Map.of("test",20.0));public static long lastTickNanos=-1;public static final String TOKEN="MUST_NEVER_BE_CAPTURED";}
