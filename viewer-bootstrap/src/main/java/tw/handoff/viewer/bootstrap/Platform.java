package tw.handoff.viewer.bootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
public record Platform(String os,String arch,String version){
    public boolean allows(JsonArray rules){
        if(rules==null)return true;
        boolean allowed=false;
        for(var element:rules){
            JsonObject rule=element.getAsJsonObject();boolean matches=true;
            if(rule.has("os")){
                JsonObject target=rule.getAsJsonObject("os");
                if(target.has("name")&&!target.get("name").getAsString().equals(os))matches=false;
                if(target.has("arch")&&!arch.matches(target.get("arch").getAsString()))matches=false;
                if(target.has("version")&&!java.util.regex.Pattern.compile(target.get("version").getAsString()).matcher(version).find())matches=false;
            }
            if(rule.has("features"))for(var feature:rule.getAsJsonObject("features").entrySet())if(feature.getValue().getAsBoolean())matches=false;
            if(matches)allowed=rule.get("action").getAsString().equals("allow");
        }
        return allowed;
    }
    public static Platform current(){String name=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);return new Platform(name.contains("win")?"windows":name.contains("mac")?"osx":"linux",System.getProperty("os.arch"),System.getProperty("os.version"));}
}
