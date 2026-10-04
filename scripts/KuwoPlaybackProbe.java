import java.io.File;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.libs.gson.*;
import me.zhenxin.zmusic.utils.*;
import me.zhenxin.zmusic.music.searchSource.KuwoMusic;
/** 只保存公开歌曲、状态、歌词数量，过滤媒体地址和账号凭据。 */
public class KuwoPlaybackProbe {
 public static void main(String[]args)throws Exception {
  ZMusic.dataFolder=new File("E:/Zhang/ZMusic-CE-Server/plugins/ZMusic");
  JsonArray result=new JsonArray();
  for(String name:new String[]{"晴天","宝宝巴士"}){
   JsonObject row=new JsonObject(); row.addProperty("keyword",name);
   JsonArray list=KuwoMusic.getMusicList(name); row.addProperty("searchCount",list==null?0:list.size());
   if(list!=null&&list.size()>0){
    String id=list.get(0).getAsJsonObject().get("id").getAsString(); row.addProperty("id",id);
    String url="https://www.kuwo.cn/api/v1/www/music/playUrl?mid="+id+"&type=music&httpsStatus=1&plat=web_www&from=";
    JsonObject before=new JsonParser().parse(WebMusicUtils.get(url,"https://www.kuwo.cn/",null)).getAsJsonObject();
    JsonObject after=new JsonParser().parse(KuwoWebAuth.get(url,"https://www.kuwo.cn/",null)).getAsJsonObject();
    row.add("beforeCode",before.get("code"));row.add("beforeMessage",before.get("message"));
    row.add("afterCode",after.get("code"));row.add("afterMessage",after.get("msg"));
   }
   JsonObject song=KuwoMusic.getMusicUrl(name);
   if(song!=null){row.addProperty("title",song.get("name").getAsString());row.addProperty("error",song.get("error").getAsString());String url=song.get("url").getAsString();row.addProperty("playable",!url.isEmpty());row.addProperty("lyricCharacters",song.get("lyric").getAsString().length()); if(!url.isEmpty()) row.addProperty("audio",WebMusicUtils.probeAudio(url,null,null));}
   result.add(row);
  }
  Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println(result);
 }
}
