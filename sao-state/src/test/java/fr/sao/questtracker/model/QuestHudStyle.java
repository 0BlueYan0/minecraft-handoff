package fr.sao.questtracker.model;
/** The SAOUI 2.51.13 record has 30+ components; two are enough to exercise the nested-record path. */
public record QuestHudStyle(boolean textShadow,int paddingX){}
