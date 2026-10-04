package me.zhenxin.zmusic.language;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicErrorMessagesTest {
    @Test void playerDoesNotReceiveProbeTokensOrExceptionNames() {
        for (String status : new String[]{"HTTP:403", "HTTP:404", "HTTP:429", "HTTP:502", "INVALID_AUDIO",
                "NETWORK:SocketTimeoutException", "NETWORK:UnknownHostException", "NETWORK:SSLHandshakeException", "ERROR:IllegalStateException"}) {
            String message = MusicErrorMessages.audioStatus(status);
            assertTrue(message.matches("(?s).*[\\u4e00-\\u9fff].*"), message);
            assertFalse(message.matches("(?s).*[A-Za-z]{3,}.*"), message);
        }
    }
    @Test void rawProviderTextAndCredentialFragmentsUseSafeChineseFallback() {
        String fallback = "音乐源未提供播放地址。";
        assertEquals(fallback, MusicErrorMessages.remoteMessage("Access denied token=example", fallback));
        assertEquals(fallback, MusicErrorMessages.remoteMessage("请求失败：https://example.invalid/?token=example", fallback));
        assertEquals(fallback, MusicErrorMessages.remoteMessage("音频适配失败: IllegalStateException", fallback));
        assertEquals("歌曲暂时无法播放。", MusicErrorMessages.remoteMessage("歌曲暂时无法播放。", fallback));
    }
    @Test void wrappedNetworkFailureUsesMeaningfulChineseCause() {
        String message = MusicErrorMessages.requestFailure(new IOException("conversion failed", new SocketTimeoutException("read timed out")), "音频处理失败。");
        assertTrue(message.contains("超时"));
        assertFalse(message.contains("Exception"));
    }
}
