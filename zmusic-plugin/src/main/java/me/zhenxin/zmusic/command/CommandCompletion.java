package me.zhenxin.zmusic.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bukkit、BungeeCord 和 Velocity 共用的指令参数树；不在补全时访问网络。 */
public final class CommandCompletion {
    public interface Context {
        boolean player();
        boolean use();
        boolean admin();
        boolean playAll();
        boolean directNetease();
        List<String> playlistIds(boolean global);
        List<String> songNumbers();
        List<String> currentPlaylistId();
        List<String> pageOffsets(boolean global, String id);
        List<String> noticeIds();
        List<String> songs(String source, boolean ids);
    }

    private static final List<String> SOURCES = Arrays.asList("163", "netease", "qq", "kugou", "kuwo", "bilibili");
    private static final List<String> NETEASE = Arrays.asList("163", "netease");
    private CommandCompletion() { }

    public static List<String> complete(String[] input, Context context) {
        String[] args = input.length == 0 ? new String[]{""} : input;
        String root = args[0].toLowerCase(Locale.ROOT);
        String prefix = args[args.length - 1];
        if (args.length == 1) {
            List<String> roots = new ArrayList<>();
            if (context.use()) {
                roots.add("help");
                if (context.player()) roots.addAll(Arrays.asList("play", "music", "search", "stop", "loop", "playlist", "url", "test"));
                if (context.admin() || context.playAll()) roots.add("playAll");
                if (context.admin()) roots.addAll(Arrays.asList("stopAll", "login", "diagnose", "reload", "update"));
            }
            // 公告操作在执行端独立检查管理员权限，不依赖 zmusic.use。
            if (context.player() && context.admin()) roots.add("notice");
            return filter(roots, prefix);
        }
        if (root.equals("notice")) {
            if (!context.player() || !context.admin()) return empty();
            if (args.length == 2) return filter(Arrays.asList("read"), prefix);
            if (args.length == 3 && args[1].equalsIgnoreCase("read")) return filter(context.noticeIds(), prefix);
            return empty();
        }
        if (!context.use()) return empty();
        switch (root) {
            case "help":
                if (args.length != 2) return empty();
                List<String> topics = new ArrayList<>(Arrays.asList("main", "play", "music", "search", "playlist", "url"));
                if (context.admin()) topics.add("admin");
                return filter(topics, prefix);
            case "play": case "music": case "search":
                if (!context.player()) return empty();
                return music(args, context, root.equals("search"));
            case "playall":
                if (!context.admin() && !context.playAll()) return empty();
                return music(args, context, false);
            case "diagnose":
                return context.admin() ? music(args, context, false) : empty();
            case "login":
                return context.admin() ? login(args, context) : empty();
            case "playlist":
                return context.player() ? playlist(args, context) : empty();
            // 无参数命令、URL、密码、验证码和 Cookie 不猜测或泄露输入。
            default: return empty();
        }
    }

    private static List<String> music(String[] args, Context context, boolean search) {
        String prefix = args[args.length - 1];
        if (args.length == 2) return filter(SOURCES, prefix);
        if (!contains(SOURCES, args[1])) return empty();
        boolean ids = !search && args.length == 3 && prefix.regionMatches(true, 0, "-id:", 0, 4);
        if (ids) return filter(context.songs(args[1], true), prefix);
        String prior = args.length == 3 ? "" : String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1)) + " ";
        List<String> values = new ArrayList<>();
        if (!search && args.length == 3) values.add("-id:");
        for (String song : context.songs(args[1], false)) {
            if (song.toLowerCase(Locale.ROOT).startsWith((prior + prefix).toLowerCase(Locale.ROOT))) {
                values.add(song.substring(prior.length()));
            }
        }
        return filter(values, prefix);
    }

    private static List<String> login(String[] args, Context context) {
        String prefix = args[args.length - 1];
        if (args.length == 2) {
            List<String> choices = new ArrayList<>(NETEASE);
            choices.addAll(Arrays.asList("qq", "kugou", "kuwo", "bilibili"));
            choices.addAll(loginMethods(context));
            return filter(choices, prefix);
        }
        int methodIndex = 1;
        if (contains(SOURCES, args[1])) {
            if (!contains(NETEASE, args[1])) {
                if (args[1].equalsIgnoreCase("qq")) {
                    if (args.length == 3) return filter(Arrays.asList("qr", "sendcode", "verify", "phone", "raw", "status", "cancel"), prefix);
                    if (args.length == 4 && args[2].equalsIgnoreCase("qr")) return filter(Arrays.asList("qq", "wechat"), prefix);
                    if (args.length == 4 && contains(Arrays.asList("sendcode", "verify", "phone"), args[2])) {
                        return filter(Arrays.asList("+86", "+852", "+853", "+886"), prefix);
                    }
                    return empty();
                }
                if (args[1].equalsIgnoreCase("kuwo")) return args.length == 3
                        ? filter(Arrays.asList("captcha", "password", "sendcode", "verify", "phone", "raw", "status", "cancel"), prefix) : empty();
                return args.length == 3 ? filter(Arrays.asList("raw", "status"), prefix) : empty();
            }
            methodIndex = 2;
            if (args.length == 3) return filter(loginMethods(context), prefix);
        }
        if (args.length == methodIndex + 2 && !context.directNetease()
                && contains(Arrays.asList("phone", "sendcode", "verify"), args[methodIndex])) {
            return filter(Arrays.asList("+86", "+852", "+853", "+886"), prefix);
        }
        return empty();
    }

    private static List<String> loginMethods(Context context) {
        return context.directNetease() ? Arrays.asList("raw", "status")
                : Arrays.asList("qr", "phone", "email", "sendcode", "verify", "raw", "status");
    }

    private static List<String> playlist(String[] args, Context context) {
        String prefix = args[args.length - 1];
        if (args.length == 2) return filter(Arrays.asList("163", "netease", "type", "next", "prev", "jump", "global"), prefix);
        String branch = args[1].toLowerCase(Locale.ROOT);
        if (branch.equals("type")) return args.length == 3 ? filter(Arrays.asList("normal", "loop", "random"), prefix) : empty();
        if (branch.equals("jump")) {
            if (args.length == 3) return filter(context.songNumbers(), prefix);
            if (args.length == 4) return filter(context.currentPlaylistId(), prefix);
            return empty();
        }
        boolean global = branch.equals("global");
        int actionIndex = global ? 3 : 2;
        if (global && args.length == 3) return filter(NETEASE, prefix);
        if (global ? !contains(NETEASE, args[2]) : !contains(NETEASE, branch)) return empty();
        List<String> actions = new ArrayList<>(Arrays.asList("play", "list", "show"));
        if (!global || context.admin()) actions.addAll(Arrays.asList("import", "update"));
        if (global && (context.admin() || context.playAll())) actions.add("playall");
        if (args.length == actionIndex + 1) return filter(actions, prefix);
        String action = args[actionIndex].toLowerCase(Locale.ROOT);
        if (!contains(actions, action)) return empty();
        if (args.length == actionIndex + 2 && contains(Arrays.asList("play", "show", "playall"), action)) {
            return filter(context.playlistIds(global), prefix);
        }
        if (args.length == actionIndex + 3 && action.equals("show")) {
            return filter(context.pageOffsets(global, args[actionIndex + 1]), prefix);
        }
        return empty();
    }

    private static boolean contains(List<String> values, String value) {
        for (String candidate : values) if (candidate.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        String match = prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(match) && !result.contains(value)) result.add(value);
            if (result.size() >= 100) break;
        }
        return result;
    }

    private static List<String> empty() { return Collections.emptyList(); }
}
