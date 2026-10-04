import me.zhenxin.zmusic.ZMusicPlayer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 编译官方模组的 ZMusicPlayer 源码后，使用其真实 playAsync/stopAsync 和原生库验证播放包。 */
public final class NativeModBridge {
    public static void main(String[] args) throws Exception {
        ZMusicPlayer player = new ZMusicPlayer();
        player.setEventListener(new ZMusicPlayer.EventListener() {
            public void onStateChanged(int state) { }
            public void onTrackEnded() { }
            public void onProgress(long position, long duration) { }
            public void onError(String message) { }
            public void onBuffering(boolean buffering) { }
        });
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                String[] command = line.split(" ", 3);
                if (command.length < 2) continue;
                String operation = command[0], source = command[1];
                if ("PLAY".equals(operation) && command.length == 3) {
                    player.playAsync(command[2]);
                    long deadline = System.currentTimeMillis() + 18000;
                    while (System.currentTimeMillis() < deadline && player.getPosition() < 1200
                            && player.getState() != ZMusicPlayer.STATE_ERROR) Thread.sleep(100);
                    System.out.println("{\"source\":\"" + source + "\",\"operation\":\"play\",\"state\":"
                            + player.getState() + ",\"positionMs\":" + player.getPosition() + ",\"durationMs\":"
                            + player.getDuration() + ",\"hasError\":" + (player.getLastError() != null) + "}");
                } else if ("STOP".equals(operation)) {
                    player.stopAsync();
                    long deadline = System.currentTimeMillis() + 2000;
                    while (System.currentTimeMillis() < deadline && player.getState() != ZMusicPlayer.STATE_STOPPED) Thread.sleep(50);
                    System.out.println("{\"source\":\"" + source + "\",\"operation\":\"stop\",\"state\":" + player.getState() + "}");
                } else if ("CHECK".equals(operation)) {
                    System.out.println("{\"source\":\"" + source + "\",\"operation\":\"check\",\"state\":"
                            + player.getState() + ",\"positionMs\":" + player.getPosition() + ",\"hasError\":" + (player.getLastError() != null) + "}");
                }
                System.out.flush();
            }
        } finally { player.destroy(); }
    }
}
