package me.zhenxin.zmusic.music.searchSource;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QQPlaybackErrorTest {
    @Test void paidMusicExplainsLoginAndDoesNotPromiseMembership() {
        JsonObject song=new JsonObject();song.addProperty("name","晴天");song.addProperty("paid",true);
        String message=QQMusic.unavailableMessage(song,"");
        assertTrue(message.contains("付费播放"));
        assertTrue(message.contains("/zm login qq qr"));
        assertFalse(message.contains("403"));
        assertTrue(message.contains("不会自动获得"));
        assertFalse(QQMusic.unavailableMessage(song,"qqmusic_uin=123; qqmusic_key=dummy").contains("未配置"));
    }
}
