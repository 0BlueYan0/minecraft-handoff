package tw.handoff.sao;
import java.util.*;
public final class SaoSchema {
 public static final String SHA256="2e73a789779e14630e68b5053732eaaaba1fe99dadeff2c2a3f6efd682a58979";
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
 private SaoSchema(){}
}
