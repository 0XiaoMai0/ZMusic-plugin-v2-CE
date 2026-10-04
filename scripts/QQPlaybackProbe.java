import java.io.File;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.music.searchSource.QQMusic;
import me.zhenxin.zmusic.libs.gson.*;
import me.zhenxin.zmusic.utils.WebMusicUtils;

/** 只记录公开曲目编号、主机与响应状态，不保存签名地址或账号凭据。 */
public class QQPlaybackProbe {
    public static void main(String[] args) throws Exception {
        ZMusic.dataFolder=new File("E:/Zhang/ZMusic-CE-Server/plugins/ZMusic");
        JsonArray report=new JsonArray();
        for(String keyword:new String[]{"晴天","宝宝巴士"}) {
            JsonObject row=new JsonObject();row.addProperty("keyword",keyword);
            JsonArray search=QQMusic.getMusicList(keyword);
            row.addProperty("searchCount",search==null?0:search.size());
            if(search!=null&&search.size()>0) {
                JsonObject selected=search.get(0).getAsJsonObject();
                row.add("selected",selected);
                Method getUrl=QQMusic.class.getDeclaredMethod("getPlayUrlByFilename",String.class,String.class);getUrl.setAccessible(true);
                JsonArray formats=new JsonArray();
                for(String type:new String[]{"M800","M500"}) {
                    JsonObject f=new JsonObject();f.addProperty("format",type);
                    String url=(String)getUrl.invoke(null,selected.get("songMid").getAsString(),type+selected.get("mediaMid").getAsString()+".mp3");
                    f.addProperty("hasUrl",url!=null&&!url.isEmpty());
                    if(url!=null&&!url.isEmpty()) {
                        f.addProperty("host",new URI(url).getHost());
                        f.addProperty("audio",WebMusicUtils.probeAudio(url,null,null));
                        f.addProperty("withReferer",WebMusicUtils.probeAudio(url,"https://y.qq.com/",null));
                    }
                    formats.add(f);
                }
                row.add("formats",formats);
            }
            JsonObject track=QQMusic.getMusicUrl(keyword);
            if(track!=null){row.addProperty("title",track.get("name").getAsString());row.addProperty("error",track.get("error").getAsString());row.addProperty("playable",!track.get("url").getAsString().isEmpty());row.addProperty("lyricCharacters",track.get("lyric").getAsString().length());}
            report.add(row);
        }
        Files.write(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println(report);
    }
}
