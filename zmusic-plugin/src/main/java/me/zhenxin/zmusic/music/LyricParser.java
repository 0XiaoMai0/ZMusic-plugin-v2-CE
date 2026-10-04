package me.zhenxin.zmusic.music;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** 解析标准 LRC、重复时间标签和 offset，输出以秒为单位的歌词时间轴。 */
public final class LyricParser {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);
    private LyricParser() { }
    public static Map<Long, String> parseSeconds(String lrc) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (lrc == null || lrc.trim().isEmpty()) return result;
        long offset = 0;
        Matcher metadata = OFFSET.matcher(lrc);
        if (metadata.find()) {
            try { offset = Long.parseLong(metadata.group(1)); } catch (NumberFormatException ignored) { }
        }
        for (String line : lrc.replace("\r", "").split("\n")) {
            Matcher matcher = TIME.matcher(line);
            String text = TIME.matcher(line).replaceAll("").trim();
            while (matcher.find()) {
                int seconds = Integer.parseInt(matcher.group(2));
                if (seconds >= 60) continue;
                String fraction = matcher.group(3);
                int millis = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
                long time = Integer.parseInt(matcher.group(1)) * 60000L + seconds * 1000L + millis + offset;
                if (time < 0 || text.isEmpty()) continue;
                result.merge(time / 1000L, text, (first, next) -> first.equals(next) ? first : first + "\n" + next);
            }
        }
        return result;
    }
}
