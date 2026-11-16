package tw.handoff.relay.core;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;

/**
 * The music link SAO's audio mod receives once, at login. A host attached after login only has the mod's copy, which the mod
 * rewrote to its own local page with the host's language appended; the viewer's mod rewrites the link again, so only the query matters.
 */
public final class ZapAudioUrl {
 public static final String CHANNEL="zapaudio:audio_url";

 public static Optional<String> forViewer(String hostUrl){
  if(hostUrl==null||hostUrl.isEmpty())return Optional.empty();
  if(hostUrl.startsWith("https://")||hostUrl.startsWith("http://"))return Optional.of(hostUrl);
  if(!hostUrl.startsWith("file:"))return Optional.empty();
  int q=hostUrl.indexOf('?');
  if(q<0)return Optional.empty();
  var kept=new ArrayList<String>();String base=null;
  for(String pair:hostUrl.substring(q+1).split("&")){
   if(pair.isEmpty()||pair.startsWith("zapLang="))continue;
   if(pair.startsWith("audioBaseUrl="))base=URLDecoder.decode(pair.substring("audioBaseUrl=".length()),StandardCharsets.UTF_8);
   kept.add(pair);
  }
  if(base==null||!(base.startsWith("https://")||base.startsWith("http://")))return Optional.empty();
  // The mod derives audioBaseUrl as origin + "/audio" when the server leaves it out.
  String origin=base.endsWith("/audio")?base.substring(0,base.length()-"/audio".length()):base;
  return Optional.of(origin+"?"+String.join("&",kept));
 }

 /** Clientbound custom payload frame carrying the link as one length-prefixed UTF-8 string. */
 public static byte[] frame(int customPayloadPacketId,String url){
  byte[] text=url.getBytes(StandardCharsets.UTF_8);
  var data=new ByteArrayOutputStream();int n=text.length;
  do{int b=n&127;n>>>=7;data.write(n==0?b:b|128);}while(n!=0);
  data.writeBytes(text);
  return OpaqueFrames.payload(customPayloadPacketId,CHANNEL,data.toByteArray());
 }

 private ZapAudioUrl(){}
}
