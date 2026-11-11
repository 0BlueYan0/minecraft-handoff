package tw.handoff.probe.client;
import com.google.gson.*;
import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import tw.handoff.sao.*;
/** Test-only state injection, restricted to our isolated local fixture; never delivered as a runtime tool. */
final class SaoFixtureRoundtrip {
 static String run(Minecraft mc)throws Exception{
  Path root=mc.gameDirectory.toPath().toRealPath();if(!root.getFileName().toString().equals("fullmods-fixture")||!Files.readString(root.resolve(".handoff-viewer")).equals("MinecraftHandoffViewer:1")||mc.getCurrentServer()==null)throw new IllegalStateException("Private fixture only");
  if(!mc.getCurrentServer().ip.startsWith("127.0.0.1:"))throw new IllegalStateException("Private backend only");
  ClassLoader loader=mc.getClass().getClassLoader();JsonObject state=SaoSnapshot.capture(loader,System.nanoTime());JsonObject sections=state.getAsJsonObject("sections"),skills=sections.getAsJsonObject("skills");
  skills.addProperty("slot",1);skills.addProperty("skillName","fixture-skill");skills.addProperty("level",7);skills.add("allSkills",JsonParser.parseString("{\"1\":\"fixture-skill\"}"));skills.add("cooldowns",JsonParser.parseString("{\"fixture-skill\":10}"));skills.add("durations",JsonParser.parseString("{\"fixture-skill\":20}"));
  sections.getAsJsonObject("equipment").add("state",JsonParser.parseString("{\"pseudo\":\"fixture-only\",\"grade\":\"test\",\"cols\":0,\"playtimeSeconds\":0,\"level\":\"7\",\"playerClass\":\"fixture-only\",\"stats\":[],\"slots\":[],\"xp\":0,\"xpNeed\":100}"));
  sections.getAsJsonObject("party").add("state",JsonParser.parseString("{\"in\":true,\"leader\":true,\"max\":8,\"invitedBy\":\"\",\"lobby\":false,\"members\":[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"fixture-only\",\"leader\":true,\"online\":true}]}"));
  Class<?> payload=Class.forName("fr.sao.questtracker.messaging.QuestHudPayload",false,loader);var field=payload.getDeclaredField("DEFAULT_STYLE");field.setAccessible(true);Object style=field.get(null);
  var values=new StateValues(Set.of("fr.sao.questtracker.model.QuestHudStyle","fr.sao.questtracker.model.QuestHudAnimation","fr.sao.questtracker.model.QuestHudAnimationType","fr.sao.questtracker.model.QuestHudAnimationDirection","fr.sao.questtracker.model.QuestHudTextAlign","fr.sao.questtracker.model.QuestHudLayerMode"));
  JsonObject quest=new JsonObject();quest.addProperty("packetType",0);quest.addProperty("hudId","handoff-fixture-only");quest.addProperty("hudKind","TEXT");Class<?> anchor=Class.forName("fr.sao.questtracker.model.QuestHudAnchor",false,loader);quest.addProperty("anchor",((Enum<?>)anchor.getEnumConstants()[0]).name());quest.addProperty("x",0);quest.addProperty("y",0);quest.addProperty("scale",1);quest.add("style",values.encode(style));
  for(String name:new String[]{"questText","objectiveText","distanceText","topText","bottomText"})quest.addProperty(name,"\"Fixture snapshot\"");
  for(String name:new String[]{"playerLookItemId","trackedQuestItemId","compassMode","editorHelpText","trackerJson"})quest.addProperty(name,"");quest.addProperty("topIconScale",1);quest.addProperty("bottomIconScale",1);quest.addProperty("targetAngleDegrees",0);var quests=new JsonArray();quests.add(quest);state.add("quests",quests);
  SaoSnapshot.prepare(loader,state,3_000_000_000L).apply();var restored=SaoSnapshot.capture(loader,System.nanoTime());double remaining=restored.getAsJsonObject("sections").getAsJsonObject("skills").getAsJsonObject("cooldowns").get("fixture-skill").getAsDouble();
  if(remaining>7||remaining<6.5||restored.getAsJsonArray("quests").size()!=1||restored.getAsJsonObject("sections").getAsJsonObject("equipment").get("state").isJsonNull()||restored.getAsJsonObject("sections").getAsJsonObject("party").getAsJsonObject("state").getAsJsonArray("members").size()!=1)throw new IllegalStateException("Fixture state did not round-trip");
  return "SAO_FIXTURE_ROUNDTRIP:skills=1,cooldownAged=true,equipment=true,partyMembers=1,questHuds=1";
 }
}
