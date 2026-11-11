package fr.sao.party;
import java.util.*;
public final class PartyClient {public record Member(String name){}public record State(boolean in,List<Member> members){}public static State state=new State(false,List.of());public static int version;public static long stateAt;}
