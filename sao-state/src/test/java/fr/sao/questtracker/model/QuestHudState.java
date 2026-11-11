package fr.sao.questtracker.model;
/** Fields and the 20-argument constructor copied from SAOUI 2.51.13; animation and cached text fields are left out. */
public final class QuestHudState {
 private final String hudId;private final QuestHudKind hudKind;private QuestHudAnchor anchor;private int x,y;private float scale;private QuestHudStyle style;
 private String questTextJson,objectiveTextJson,distanceTextJson,topTextJson,bottomTextJson,playerLookItemId,trackedQuestItemId;private float topIconScale,bottomIconScale;
 private String editorHelpText,trackerJson;private float targetAngleDegrees;private String compassMode;private boolean targetVisible;
 public QuestHudState(String hudId,QuestHudKind hudKind,QuestHudAnchor anchor,int x,int y,float scale,QuestHudStyle style,String questTextJson,String objectiveTextJson,String distanceTextJson,String topTextJson,String bottomTextJson,String playerLookItemId,String trackedQuestItemId,float topIconScale,float bottomIconScale,float targetAngleDegrees,String compassMode,String editorHelpText,String trackerJson){
  this.hudId=hudId;this.hudKind=hudKind;this.anchor=anchor;this.x=x;this.y=y;this.scale=scale;this.style=style;this.questTextJson=questTextJson;this.objectiveTextJson=objectiveTextJson;this.distanceTextJson=distanceTextJson;this.topTextJson=topTextJson;this.bottomTextJson=bottomTextJson;
  this.playerLookItemId=playerLookItemId;this.trackedQuestItemId=trackedQuestItemId;this.topIconScale=topIconScale;this.bottomIconScale=bottomIconScale;this.editorHelpText=editorHelpText;this.trackerJson=trackerJson==null?"":trackerJson;this.targetAngleDegrees=targetAngleDegrees;this.compassMode=compassMode;this.targetVisible=true;
 }
}
