package me.zhenxin.zmusic.login;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.audio.ModAudioServer;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.utils.log.Log;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class LoginImagesTest {
    @Test void privateImagesServeAndCancelWhileAudioIsDisabled() throws Exception {
        Log log=ZMusic.log;
        boolean enabled=Config.audioEnabled;
        String bind=Config.audioBind, publicUrl=Config.audioPublicUrl;
        int port=Config.audioPort;
        try {
            ZMusic.log=(Log)Proxy.newProxyInstance(Log.class.getClassLoader(),new Class<?>[]{Log.class},(proxy,method,args)->null);
            Config.audioEnabled=false;Config.audioBind="127.0.0.1";
            try(ServerSocket socket=new ServerSocket(0)){Config.audioPort=socket.getLocalPort();}
            Config.audioPublicUrl="http://127.0.0.1:"+Config.audioPort;
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==");
            String address=LoginImages.publish(new QQLogin.Image(png,"image/png"));
            assertTrue(address.matches("http://127\\.0\\.0\\.1:\\d+/login-image/[A-Za-z0-9_-]{32}"));
            HttpURLConnection get=(HttpURLConnection)new URL(address).openConnection();
            try {
                assertEquals(200,get.getResponseCode());
                assertEquals("image/png",get.getContentType());
                assertEquals("private, no-store",get.getHeaderField("Cache-Control"));
                assertEquals("no-referrer",get.getHeaderField("Referrer-Policy"));
                assertEquals(png.length,get.getContentLength());
                try(java.io.InputStream in=get.getInputStream()){assertEquals(137,in.read());}
            } finally {get.disconnect();}
            assertThrows(java.io.IOException.class,()->ModAudioServer.prepare("BVtest","https://x.bilivideo.com/audio.m4s"));
            LoginImages.remove(address);
            HttpURLConnection removed=(HttpURLConnection)new URL(address).openConnection();
            try { assertEquals(410,removed.getResponseCode()); } finally {removed.disconnect();}
            assertThrows(java.io.IOException.class,()->LoginImages.publish(new QQLogin.Image("<html>login failed</html>".getBytes(),"image/png")));
        } finally {
            ModAudioServer.close();ZMusic.log=log;Config.audioEnabled=enabled;Config.audioBind=bind;Config.audioPublicUrl=publicUrl;Config.audioPort=port;
        }
    }
}
