package me.zhenxin.zmusic.utils.music;

import me.zhenxin.zmusic.ZMusic;

public class Music {

    public void play(String url, Object playerObj) {
        ZMusic.send.sendAM(playerObj, "[Play]" + url);
        ZMusic.send.sendABF(playerObj, "[Net]" + url);

    }

    public void stop(Object playerObj) {
        ZMusic.send.sendAM(playerObj, "[Stop]");
        ZMusic.send.sendABF(playerObj, "[Stop]");
    }
}
