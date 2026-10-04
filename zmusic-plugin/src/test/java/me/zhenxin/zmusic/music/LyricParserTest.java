package me.zhenxin.zmusic.music;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class LyricParserTest {
    @Test void parsesWholeSecondsFractionsAndMultipleTags() {
        Map<Long, String> result = LyricParser.parseSeconds("[ar:歌手]\n[00:01]第一行\n[00:02.5][00:03.500]重复行\n[00:04:12]最后一行");
        assertEquals("第一行", result.get(1L));
        assertEquals("重复行", result.get(2L));
        assertEquals("重复行", result.get(3L));
        assertEquals("最后一行", result.get(4L));
        assertEquals(4, result.size());
    }
    @Test void appliesOffsetWithoutTurningMetadataIntoLyrics() {
        Map<Long, String> result = LyricParser.parseSeconds("[offset:-500]\n[00:01.4]提前\n[00:61]错误\n[00:00.1]负时间");
        assertEquals("提前", result.get(0L)); assertEquals(1, result.size());
        assertTrue(LyricParser.parseSeconds(null).isEmpty());
    }
}
