package me.zhenxin.zmusic.utils;

import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.api.MultiMap;
import me.zhenxin.zmusic.api.bossbar.BossBar;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.data.PlayerData;
import me.zhenxin.zmusic.proto.Toast;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtherUtils {

    /**
     * 参数合一
     *
     * @param args 参数
     * @return 合并的值
     */
    public static String argsXin1(String[] args) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i != 0 & i != 1) {
                s.append(args[i]).append(" ");
            }
        }
        return s.toString().trim();
    }

    public static void resetPlayerStatus(Object player) {
        synchronized (player) {
            ZMusic.music.stop(player);
            if (Config.supportBossBar) {
                BossBar bossBar = PlayerData.getPlayerBoosBar(player);
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                }
            }
            if (Config.supportTitle) {
                ZMusic.message.sendTitleMessage("", "", player);
            }
            if (Config.supportHud) {
                ZMusic.send.sendAM(player, "[Lyric]");
                ZMusic.send.sendAM(player, "[Info]");
            }
            PlayerData.setPlayerPlayStatus(player, false);
            PlayerData.setPlayerMusicName(player, null);
            PlayerData.setPlayerMusicSinger(player, null);
            PlayerData.setPlayerCurrentTime(player, null);
            PlayerData.setPlayerMaxTime(player, null);
            PlayerData.setPlayerLyric(player, null);
            PlayerData.setPlayerPlatform(player, null);
            PlayerData.setPlayerPlaySource(player, null);
        }
    }

    /**
     * 从输入流中获取字符串
     *
     * @param inputStream 输入流
     * @return 文本
     * @throws IOException IO异常
     */
    public static String readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static String readInputStream(InputStreamReader inputStream) throws IOException {
        char[] cbuffer = new char[1024];
        int len = 0;
        StringBuilder stringBuilder = new StringBuilder();
        while ((len = inputStream.read(cbuffer)) != -1) {
            stringBuilder.append(cbuffer);
        }
        return stringBuilder.toString();
    }

    /**
     * 格式化歌词信息
     *
     * @param lyric   歌词
     * @param lyricTr 歌词翻译
     * @return 格式化后的Json
     */
    public static JsonObject formatLyric(String lyric, String lyricTr) {
        Map<Long, String> lrcMap = formatLyric(lyric);
        Map<Long, String> lrcTrMap = formatLyric(lyricTr);
        JsonObject json = new JsonObject();
        for (Map.Entry<Long, String> entry : lrcMap.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("lrc", entry.getValue());
            if (lrcTrMap.get(entry.getKey()) != null) {
                j.addProperty("lrcTr", lrcTrMap.get(entry.getKey()));
            } else {
                j.addProperty("lrcTr", "");
            }
            json.add(String.valueOf(entry.getKey()), j);
        }
        return json;
    }

    private static Map<Long, String> formatLyric(String lyric) {
        return me.zhenxin.zmusic.music.LyricParser.parseSeconds(lyric);
    }

    public static String getMD5String(String str) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(str.getBytes("UTF8"));
            byte s[] = m.digest();
            String result = "";
            for (int i = 0; i < s.length; i++) {
                result += Integer.toHexString((0x000000FF & s[i]) | 0xFFFFFF00).substring(6);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static ArrayList<String> queryFileNames(String filePath) {
        ArrayList<String> es = new ArrayList<>();
        File f = new File(filePath);
        if (!f.exists()) {
            return null;
        }
        File[] fs = f.listFiles();
        for (File file : fs) {
            if (file.isFile()) {
                es.add(file.getName());
            }
        }
        return es;
    }

    public static String readFileToString(File file) {
        String s = "";
        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            s = readFileToString(isr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    public static String readFileToString(InputStreamReader isr) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append("\r\n"); // 补上换行符
            }
            isr.close();
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    /**
     * 将文本写入本地文件
     *
     * @param file 文件路径
     * @param text 文本
     * @throws IOException IOException
     */
    public static void saveStringToLocal(File file, String text) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        osw.write(text);
        osw.flush();
        osw.close();
    }

    /**
     * 将InputStream写入本地文件
     *
     * @param destination 写入本地目录
     * @param input       输入流
     * @throws IOException IOException
     */
    public static void writeToLocal(String destination, InputStream input) throws IOException {
        int index;
        byte[] bytes = new byte[1024];
        FileOutputStream downloadFile = new FileOutputStream(destination);
        while ((index = input.read(bytes)) != -1) {
            downloadFile.write(bytes, 0, index);
            downloadFile.flush();
        }
        input.close();
        downloadFile.close();
    }

    public static String formatTime(Long time) {
        if (time != null) {
            if (time < 60) {
                return "00" + ":" + String.format("%02d", time);
            } else if (time < 3600) {
                long m = time / 60;
                long s = time % 60;
                return String.format("%02d", m) + ":" + String.format("%02d", s);
            } else {
                long h = time / 3600;
                long m = (time % 3600) / 60;
                long s = (time % 3600) % 60;
                return String.format("%02d", h) + ":" + String.format("%02d", m) + ":" + String.format("%02d", s);
            }
        } else {
            return "--:--";
        }
    }

    public static void sendAdv(Object player, String title) {
        if (Config.realSupportAdvancement) {
            if (ZMusic.isBC || ZMusic.isVelocity) {
                JsonObject json = new JsonObject();
                json.addProperty("isAdv", true);
                json.addProperty("title", title);
                ZMusic.send.sendToZMusicAddon(player, json.toString());
            } else {
                ZMusic.runTask.run(() -> {
                    Toast.sendToast(player, title);
                });
            }
        }
    }

    public static String md5(String str) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(str.getBytes("UTF8"));
            byte s[] = m.digest();
            StringBuilder result = new StringBuilder();
            for (byte value : s) {
                result.append(Integer.toHexString((0x000000FF & value) | 0xFFFFFF00).substring(6));
            }
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
