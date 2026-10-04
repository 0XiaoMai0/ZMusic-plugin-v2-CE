package me.zhenxin.zmusic.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 酷我官网访客请求校验；不生成账号凭据，也不改变歌曲权限。 */
public final class KuwoWebAuth {
    private static final String VISITOR_KEY = "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324";
    private KuwoWebAuth() { }

    public static String get(String url, String referer, String cookie) throws IOException {
        String normalized = ServiceCookieUtils.normalizeRawCookie(cookie);
        String visitor = ServiceCookieUtils.getCookieValue(normalized, VISITOR_KEY);
        if (visitor.isEmpty()) {
            visitor = UUID.randomUUID().toString().replace("-", "");
            normalized += (normalized.isEmpty() ? "" : "; ") + VISITOR_KEY + "=" + visitor;
        }
        return WebMusicUtils.getWithHeaders(url, referer, normalized,
                Collections.singletonMap("Secret", secret(visitor, ThreadLocalRandom.current().nextInt(10000000, 100000000))));
    }

    static String secret(String visitor, int nonce) {
        if (nonce < 10000000 || nonce > 99999999) throw new IllegalArgumentException("Eight-digit nonce required");
        // 官网公开校验算法使用八位十进制随机数时，JavaScript 浮点折叠后的初始状态固定。
        // 使用整数递推保留相同的协议结果，避免 Java 与 JavaScript 科学计数格式不同。
        long state = 297374397L;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < visitor.length(); i++) {
            int value = visitor.charAt(i) ^ (int) Math.floor(state / 2147483647.0 * 255);
            if (value < 16) result.append('0');
            result.append(Integer.toHexString(value));
            state = (9253 * state + 23) % 2147483647L;
        }
        String suffix = Integer.toHexString(nonce);
        for (int i = suffix.length(); i < 8; i++) result.append('0');
        return result.append(suffix).toString();
    }
}
