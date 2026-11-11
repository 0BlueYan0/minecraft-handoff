package fr.sao.questtracker.messaging;
import fr.sao.questtracker.model.*;
/** Component names and types copied from SAOUI 2.51.13; the network codec is left out. */
public record QuestHudPayload(byte packetType,String hudId,QuestHudKind hudKind,QuestHudAnchor anchor,int x,int y,float scale,QuestHudStyle style,String questText,String objectiveText,String distanceText,String topText,String bottomText,String playerLookItemId,String trackedQuestItemId,float topIconScale,float bottomIconScale,float targetAngleDegrees,String compassMode,String editorHelpText,String trackerJson){}
