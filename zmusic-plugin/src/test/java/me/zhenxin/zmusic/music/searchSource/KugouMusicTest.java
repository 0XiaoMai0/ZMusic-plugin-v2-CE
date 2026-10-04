package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KugouMusicTest {
    @Test void restrictionCodeWinsOverEnglishRemoteMessage() {
        JsonObject response = new JsonObject();
        response.addProperty("err_code", 30020);
        response.addProperty("error", "client-only status");
        String message = KugouMusic.playbackError(response, "未返回播放地址。");
        assertTrue(message.contains("30020"));
        assertTrue(message.contains("酷狗"));
        assertFalse(message.matches("(?s).*[A-Za-z]{3,}.*"));
    }
    @Test void expiredIdentifierAndUnknownStatusAreChinese() {
        JsonObject response = new JsonObject();
        response.addProperty("err_code", 30000);
        assertTrue(KugouMusic.playbackError(response, "未返回播放地址。").contains("重新搜索"));
        response.addProperty("err_code", 12345);
        assertTrue(KugouMusic.playbackError(response, "未返回播放地址。").contains("12345"));
    }
    @Test void bothSupportedCookieLayoutsPreserveAccountCredentials() {
        Map<String, String> modern = KugouMusic.signedBaseParams("userid=123; token=example-modern");
        assertEquals("123", modern.get("userid"));
        assertEquals("example-modern", modern.get("token"));
        Map<String, String> legacy = KugouMusic.signedBaseParams("KuGoo=KugooID=456&t=example-legacy");
        assertEquals("456", legacy.get("userid"));
        assertEquals("example-legacy", legacy.get("token"));
    }
    @Test void fallbackPlaybackRequestAlsoForwardsModernCredentials() throws Exception {
        String url = KugouMusic.appendAuthParams("https://example.invalid/play?hash=example", "userid=123; token=example-modern");
        assertTrue(url.contains("&userid=123"));
        assertTrue(url.contains("&token=example-modern"));
    }
}
