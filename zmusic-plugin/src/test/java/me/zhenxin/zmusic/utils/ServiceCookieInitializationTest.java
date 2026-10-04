package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ServiceCookieInitializationTest {
    @TempDir Path directory;
    @Test void createsEmptyFilesWithoutOverwritingExistingAccount() throws Exception {
        File original=ZMusic.dataFolder;
        try {
            ZMusic.dataFolder=directory.resolve("ZMusic").toFile();
            Files.createDirectories(ZMusic.dataFolder.toPath());
            Files.write(ZMusic.dataFolder.toPath().resolve("kugou-cookies.txt"),"userid=dummy; token=dummy".getBytes(StandardCharsets.UTF_8));
            ServiceCookieUtils.initializeFiles();
            ServiceCookieUtils.initializeFiles();
            for(String source:new String[]{"qq","kuwo","kugou","bilibili"}) assertTrue(Files.isRegularFile(ZMusic.dataFolder.toPath().resolve(source+"-cookies.txt")));
            assertFalse(ServiceCookieUtils.hasCookies("qq"));
            assertFalse(ServiceCookieUtils.hasCookies("kuwo"));
            assertEquals("userid=dummy; token=dummy",ServiceCookieUtils.getCookies("kugou"));
        } finally { ZMusic.dataFolder=original; }
    }
}
