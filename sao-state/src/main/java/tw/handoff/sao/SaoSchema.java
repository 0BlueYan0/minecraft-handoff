package tw.handoff.sao;
import java.util.*;
public final class SaoSchema {
 // A whole-JAR hash broke on 2.51.14, which only changed pioneers/tab/markdown/SaoUiMod. The contract is pinned to the
 // bytecode of the classes this module actually reads or constructs, so unrelated SAO updates keep working and any change
 // to these classes still fails closed. Verified equal for 2.51.13 and 2.51.14 by ArtifactContractTest.
 public static final String CONTRACT_FINGERPRINT="380799690915ceb0a77e031562d9c354cec73df48a100188d1caa2aed5ae3fb6";
 static final Map<String,String> CLASSES=new LinkedHashMap<>();
 static final Map<String,List<String>> FIELDS=new LinkedHashMap<>();
 static{
  CLASSES.put("mux","fr.sao.net.Mux");FIELDS.put("mux",List.of("SERVED","sendable"));
  CLASSES.put("skills","fr.sao.saoui.skills.SkillStatusClient");FIELDS.put("skills",List.of("slot","skillName","level","allSkills","cooldowns","durations"));
  CLASSES.put("equipment","fr.sao.equipment.EquipmentClient");FIELDS.put("equipment",List.of("state","listSlot","list","lists","presets","listBatch","cursorOps"));
  CLASSES.put("party","fr.sao.party.PartyClient");FIELDS.put("party",List.of("state"));
 }
 static final List<String> QUEST_FIELDS=List.of("hudId","hudKind","anchor","x","y","scale","style","questTextJson","objectiveTextJson","distanceTextJson","topTextJson","bottomTextJson","playerLookItemId","trackedQuestItemId","topIconScale","bottomIconScale","targetAngleDegrees","compassMode","editorHelpText","trackerJson");
 static final Set<String> VALUES;
 static{var names=new HashSet<String>();for(String name:List.of("State","Item","Slot","Stat","Preset","Presets"))names.add("fr.sao.equipment.EquipmentClient$"+name);for(String name:List.of("State","Member"))names.add("fr.sao.party.PartyClient$"+name);for(String name:List.of("QuestHudStyle","QuestHudAnimation","QuestHudKind","QuestHudAnchor","QuestHudTextAlign","QuestHudLayerMode","QuestHudAnimationType","QuestHudAnimationDirection"))names.add("fr.sao.questtracker.model."+name);names.add("fr.sao.questtracker.messaging.QuestHudPayload");VALUES=Set.copyOf(names);}
 /** Every SAO class whose fields are read, written or constructed by SaoSnapshot. */
 public static final List<String> CONTRACT_CLASSES;
 static{var names=new TreeSet<String>(CLASSES.values());names.addAll(VALUES);names.add("fr.sao.questtracker.model.QuestHudStore");names.add("fr.sao.questtracker.model.QuestHudState");CONTRACT_CLASSES=List.copyOf(names);}
 /** SHA-256 over each contract class name and the SHA-256 of its class file, in sorted name order. */
 public static String fingerprint(java.util.function.Function<String,byte[]> classBytes)throws java.security.NoSuchAlgorithmException{
  var all=java.security.MessageDigest.getInstance("SHA-256");var one=java.security.MessageDigest.getInstance("SHA-256");
  for(String name:CONTRACT_CLASSES){byte[] bytes=classBytes.apply(name);if(bytes==null)throw new IllegalArgumentException("SAO version requires a new state contract");
   all.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));all.update((byte)0);all.update(one.digest(bytes));}
  return HexFormat.of().formatHex(all.digest());
 }
 private SaoSchema(){}
}
