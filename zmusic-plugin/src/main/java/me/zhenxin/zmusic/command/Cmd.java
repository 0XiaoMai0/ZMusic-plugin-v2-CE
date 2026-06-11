package me.zhenxin.zmusic.command;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.api.ProgressBar;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.config.LoadConfig;
import me.zhenxin.zmusic.data.PlayerData;
import me.zhenxin.zmusic.language.LoadLang;
import me.zhenxin.zmusic.login.NeteaseLogin;
import me.zhenxin.zmusic.music.PlayList;
import me.zhenxin.zmusic.music.PlayListPlayer;
import me.zhenxin.zmusic.music.PlayMusic;
import me.zhenxin.zmusic.music.SearchMusic;
import me.zhenxin.zmusic.utils.HelpUtils;
import me.zhenxin.zmusic.utils.OtherUtils;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.Vault;
import me.zhenxin.zmusic.login.LoginMethod;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Cmd {

    static Map<Object, Integer> cooldown = new ConcurrentHashMap<>();
    static List<Object> cooldownStats = Collections.synchronizedList(new ArrayList<>());

    public static boolean cmd(Object sender, String[] args) { // 指令输出
        if (ZMusic.isEnableEd) {
            if (ZMusic.isEnable) {
                boolean isUse;
                boolean isAdmin;
                boolean isPlayAll;
                if (ZMusic.player.isPlayer(sender)) {
                    isUse = ZMusic.player.hasPermission(sender, "zmusic.use");
                    isAdmin = ZMusic.player.hasPermission(sender, "zmusic.admin");
                    isPlayAll = ZMusic.player.hasPermission(sender, "zmusic.playall");
                } else {
                    isUse = true;
                    isAdmin = true;
                    isPlayAll = true;
                }
                if (isUse) {
                    if (args.length == 0) {
                        ZMusic.message.sendNull(sender);
                    } else {
                        switch (args[0].toLowerCase()) {
                            case "music":
                                handleMusicCommand(sender, args);
                                break;
                            case "play":
                                handlePlayCommand(sender, args);
                                break;
                            case "search":
                                handleSearchCommand(sender, args);
                                break;
                            case "stop":
                                handleStopCommand(sender);
                                break;
                            case "loop":
                                handleLoopCommand(sender);
                                break;
                            case "playlist":
                                handlePlaylistCommand(sender, args);
                                break;
                            case "url":
                                handleUrlCommand(sender, args);
                                break;
                            case "playall":
                                handlePlayAllCommand(sender, args, isPlayAll, isAdmin);
                                break;
                            case "stopall":
                                handleStopAllCommand(sender, isAdmin);
                                break;
                            case "login":
                                handleLoginCommand(sender, args, isAdmin);
                                break;
                            case "help":
                                handleHelpCommand(sender, args);
                                break;
                            case "reload":
                                handleReloadCommand(sender, isAdmin);
                                break;
                            case "update":
                                handleUpdateCommand(sender, isAdmin);
                                break;
                            case "test":
                                handleTestCommand(sender);
                                break;
                            default:
                                ZMusic.message.sendNull(sender);
                                break;
                        }
                    }
                } else {
                    ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.use 权限此使用命令.", sender);
                }
            } else {
                ZMusic.message.sendErrorMessage("错误: 请删除AudioBuffer/AllMusic插件.", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 请等待插件加载完毕.", sender);
        }
        return true;
    }

    public static List<String> tab(Object sender, String[] args) {
        boolean isAdmin;
        if (ZMusic.player.isPlayer(sender)) {
            isAdmin = ZMusic.player.hasPermission(sender, "zmusic.admin");
        } else {
            isAdmin = true;
        }

        String[] commandList = new String[0];
        if (args.length == 0) {
            return new ArrayList<>();
        } else if (args.length >= 1) {
            if (args.length == 1) {
                if (isAdmin) {
                    commandList = new String[] { "help", "play", "playlist", "music", "stop", "loop", "login",
                            "search", "url", "playAll", "stopAll", "update", "reload" };
                } else {
                    commandList = new String[] { "help", "play", "playlist", "music", "stop", "loop", "search",
                            "url" };
                }
                return Arrays.stream(commandList).filter(s -> s.startsWith(args[0])).collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("play") ||
                    args[0].equalsIgnoreCase("music") ||
                    args[0].equalsIgnoreCase("search") ||
                    args[0].equalsIgnoreCase("playAll")) {
                if (args.length == 2) {
                    commandList = new String[] { "qq", "163", "netease", "kuwo", "kugou", "bilibili" };
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
                return new ArrayList<>();
            } else if (args[0].equalsIgnoreCase("help")) {
                if (args.length == 2) {
                    if (isAdmin) {
                        commandList = new String[] { "play", "playlist", "music", "search", "url", "admin" };
                    } else {
                        commandList = new String[] { "play", "playlist", "music", "search", "url" };
                    }
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                }
                return new ArrayList<>();
            } else if (args[0].equalsIgnoreCase("playlist")) {
                if (args.length == 2) {
                    commandList = new String[] { "qq", "netease", "163", "type", "global", "next", "prev", "jump" };
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("type")) {
                        commandList = new String[] { "random", "normal", "loop" };
                        return Arrays.stream(commandList).filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    } else if (args[1].equalsIgnoreCase("global")) {
                        commandList = new String[] { "qq", "netease", "163" };
                        return Arrays.stream(commandList).filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    } else {
                        commandList = new String[] { "import", "play", "list", "update", "show" };
                        return Arrays.stream(commandList).filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    }
                } else if (args.length == 4) {
                    if (args[2].equalsIgnoreCase("qq") ||
                            args[2].equalsIgnoreCase("163") ||
                            args[2].equalsIgnoreCase("netease")) {
                        commandList = new String[] { "import", "play", "list", "update", "show" };
                        return Arrays.stream(commandList).filter(s -> s.startsWith(args[3]))
                                .collect(Collectors.toList());
                    }
                    return new ArrayList<>();
                }
                return new ArrayList<>();
            } else if (args[0].equalsIgnoreCase("login")) {
                if (!isAdmin) {
                    return new ArrayList<>();
                }
                if (args.length == 2) {
                    commandList = new String[] { "qr", "phone", "email", "sendcode", "verify", "status", "raw", "qq", "kugou", "kuwo" };
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                } else if (args.length == 3 && (args[1].equalsIgnoreCase("qq")
                        || args[1].equalsIgnoreCase("kugou")
                        || args[1].equalsIgnoreCase("kuwo"))) {
                    commandList = new String[] { "qr", "raw", "status" };
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
                } else if (args.length == 3 && (args[1].equalsIgnoreCase("phone")
                        || args[1].equalsIgnoreCase("sendcode")
                        || args[1].equalsIgnoreCase("verify"))) {
                    commandList = new String[] { "+86", "+852", "+853", "+886" };
                    return Arrays.stream(commandList).filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
                }
                return new ArrayList<>();
            }
            return new ArrayList<>();
        }
        return Arrays.stream(commandList).filter(s -> s.startsWith(args[0])).collect(Collectors.toList());
    }

    private static void handleMusicCommand(Object sender, String[] args) {
        // Extracted logic for "music" command
        if (ZMusic.player.isPlayer(sender)) {
            if (args.length >= 2) {
                int cooldownSec = Config.cooldown;
                Runnable startPlay = () -> {
                    List<Object> players = ZMusic.player.getOnlinePlayerList();
                    PlayMusic.play(OtherUtils.argsXin1(args), args[1], sender, "music", players);
                };
                if (!ZMusic.player.hasPermission(sender, "zmusic.bypass")) {
                    if (!cooldownStats.contains(sender)) {
                        if (!ZMusic.isBC) {
                            if (Config.realSupportVault) {
                                if (Config.money > 0) {
                                    if (!Vault.take(sender)) {
                                        return;
                                    }
                                }
                            }
                        }
                        ZMusic.runTask.runAsync(startPlay);
                        if (cooldownSec > 0) {
                            cooldownStats.add(sender);
                            cooldown.put(sender, cooldownSec);
                            Timer timer = new Timer();
                            TimerTask timerTask = new TimerTask() {
                                @Override
                                public void run() {
                                    int sec = cooldown.get(sender);
                                    if (sec != 1) {
                                        sec--;
                                        cooldown.put(sender, sec);
                                    } else {
                                        cooldownStats.remove(sender);
                                        cancel();
                                    }
                                }
                            };
                            timer.schedule(timerTask, 1000L, 1000L);
                        }
                    } else {
                        ZMusic.message.sendErrorMessage("冷却时间未到,还有§e " + cooldown.get(sender) + "§c 秒", sender);
                    }
                } else {
                    ZMusic.runTask.runAsync(startPlay);
                }
            } else {
                HelpUtils.sendHelp("music", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handlePlayCommand(Object sender, String[] args) {
        // Extracted logic for "play" command
        if (ZMusic.player.isPlayer(sender)) {
            if (args.length >= 2) {
                ZMusic.runTask.runAsync(() -> {
                    PlayMusic.play(OtherUtils.argsXin1(args), args[1], sender, "self", null);
                });
            } else {
                HelpUtils.sendHelp("play", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handleSearchCommand(Object sender, String[] args) {
        // Extracted logic for "search" command
        if (ZMusic.player.isPlayer(sender)) {
            if (args.length >= 2) {
                ZMusic.runTask.runAsync(() -> {
                    SearchMusic.sendList(OtherUtils.argsXin1(args), args[1], sender);
                });
            } else {
                HelpUtils.sendHelp("search", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handleStopCommand(Object sender) {
        // Extracted logic for "stop" command
        if (ZMusic.player.isPlayer(sender)) {
            PlayListPlayer plp = PlayerData.getPlayerPlayListPlayer(sender);
            if (plp != null) {
                plp.isStop = true;
                PlayerData.setPlayerPlayListPlayer(sender, null);
                OtherUtils.resetPlayerStatus(sender);
            }
            OtherUtils.resetPlayerStatus(sender);
            ZMusic.message.sendNormalMessage("停止播放音乐成功!", sender);
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handleLoopCommand(Object sender) {
        // Extracted logic for "loop" command
        if (ZMusic.player.isPlayer(sender)) {
            if (PlayerData.getPlayerLoopPlay(sender) != null && PlayerData.getPlayerLoopPlay(sender)) {
                PlayerData.setPlayerLoopPlay(sender, false);
                ZMusic.message.sendNormalMessage("循环播放已关闭!", sender);
            } else {
                PlayerData.setPlayerLoopPlay(sender, true);
                ZMusic.message.sendNormalMessage("循环播放已开启!", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handlePlaylistCommand(Object sender, String[] args) {
        // Extracted logic for "playlist" command
        if (ZMusic.player.isPlayer(sender)) {
            if (args.length >= 2) {
                ZMusic.runTask.runAsync(() -> {
                    PlayList.subCommand(args, sender);
                });
            } else {
                HelpUtils.sendHelp("playlist", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handleUrlCommand(Object sender, String[] args) {
        // Extracted logic for "url" command
        if (ZMusic.player.isPlayer(sender)) {
            if (args.length == 2) {
                ZMusic.runTask.runAsync(() -> {
                    ZMusic.music.stop(sender);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ZMusic.music.play(args[1], sender);
                    ZMusic.message.sendNormalMessage("播放成功!", sender);
                });
            } else {
                HelpUtils.sendHelp("url", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void handlePlayAllCommand(Object sender, String[] args, boolean isPlayAll, boolean isAdmin) {
        // Extracted logic for "playall" command
        if (isPlayAll || isAdmin) {
            List<Object> players = ZMusic.player.getOnlinePlayerList();
            if (args.length >= 2) {
                ZMusic.runTask.runAsync(() -> {
                    PlayMusic.play(OtherUtils.argsXin1(args), args[1], sender, "all", players);
                });
            } else {
                HelpUtils.sendHelp("admin", sender);
            }
        } else {
            ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.admin 权限此使用命令.", sender);
        }
    }

    private static void handleStopAllCommand(Object sender, boolean isAdmin) {
        // Extracted logic for "stopall" command
        if (isAdmin) {
            List<Object> players = ZMusic.player.getOnlinePlayerList();
            for (Object player : players) {
                OtherUtils.resetPlayerStatus(player);
            }
            ZMusic.message.sendNormalMessage("强制全部玩家停止播放音乐成功!", sender);
        } else {
            ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.admin 权限此使用命令.", sender);
        }
    }

    private static void handleLoginCommand(Object sender, String[] args, boolean isAdmin) {
        if (!isAdmin) {
            ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.admin 权限此使用命令.", sender);
            return;
        }

        if (args.length < 2) {
            ZMusic.message.sendErrorMessage("用法: /zm login <qr|phone|email|sendcode|verify|status|raw>", sender);
            return;
        }

        if ("qq".equalsIgnoreCase(args[1]) || "kugou".equalsIgnoreCase(args[1]) || "kuwo".equalsIgnoreCase(args[1])) {
            handleServiceLoginCommand(sender, args);
            return;
        }

        LoginMethod loginMethod = LoginMethod.fromToken(args[1]);
        if (loginMethod == null) {
            if ("status".equalsIgnoreCase(args[1])) {
                handleLoginStatus(sender);
                return;
            }
            ZMusic.message.sendErrorMessage("未知登录子命令: " + args[1], sender);
            ZMusic.message.sendErrorMessage("用法: /zm login <qr|phone|email|sendcode|verify|status|raw>", sender);
            return;
        }

        // NOTE: case "status" is handled separately above.
        switch (loginMethod) {
            case QR:
                loginByQRCode(sender);
                break;
            case PHONE:
                ParsedPhoneInput phoneLoginInput = parseCtCodeAndPhone(args, 2, sender,
                        "用法: /zm login phone +<ctcode=86> [phone] [password]");
                if (phoneLoginInput == null || args.length <= phoneLoginInput.nextIndex) {
                    ZMusic.message.sendErrorMessage("用法: /zm login phone +<ctcode=86> [phone] [password]", sender);
                    return;
                }

                loginByPhone(sender, phoneLoginInput.phone, args[phoneLoginInput.nextIndex], phoneLoginInput.ctCode);
                break;
            case EMAIL:
                if (args.length < 4) {
                    ZMusic.message.sendErrorMessage("用法: /zm login email [username] [password]", sender);
                    return;
                }
                loginByEmail(sender, args[2], args[3]);
                break;
            case SEND_CODE:
                ParsedPhoneInput sendCodeInput = parseCtCodeAndPhone(args, 2, sender,
                        "用法: /zm login sendcode +<ctcode=86> [phone]");
                if (sendCodeInput == null) {
                    return;
                }

                loginSendCode(sender, sendCodeInput.phone, sendCodeInput.ctCode);
                break;
            case VERIFY:
                ParsedPhoneInput verifyInput = parseCtCodeAndPhone(args, 2, sender,
                        "用法: /zm login verify +<ctcode=86> [phone] [code]");
                if (verifyInput == null || args.length <= verifyInput.nextIndex) {
                    ZMusic.message.sendErrorMessage("用法: /zm login verify +<ctcode=86> [phone] [code]", sender);
                    return;
                }

                loginByVerifyCode(sender, verifyInput.phone, verifyInput.ctCode, args[verifyInput.nextIndex]);
                break;
            case RAW:
                if (args.length < 3) {
                    String rawHelpMsg = "用法: /zm login raw [cookie1|cookie2|...]\n" +
                            "可以在浏览器控制台执行下面的命令: \n" +
                            "console.log(document.cookie);\n" +
                            "然后粘贴。\n" +
                            "如果超过256字符上限，请粘贴到命令方块内。\n\n" +
                            "有效cookie的字段有: \n" +
                            "MUSIC_R_T; MUSIC_U; MUSIC_A_T; __csrf; MUSIC_SNS; MUSIC_R_U; __remember_me";

                    ZMusic.message.sendErrorMessage(rawHelpMsg, sender);
                    return;
                }

                String rawCookies = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                NeteaseLogin.loginRaw(rawCookies);
                ZMusic.message.sendNormalMessage("Cookies 已提交处理。", sender);
                break;
            default:
                ZMusic.message.sendErrorMessage("未知登录方式", sender);
                break;
        }
    }

    private static void handleLoginStatus(Object sender) {
        String nickname = NeteaseLogin.nickname();
        if (nickname.isEmpty()) {
            ZMusic.message.sendNormalMessage("您当前未登录网易云音乐。", sender);
            ZMusic.message.sendNormalMessage("NetEase status: " + NeteaseLogin.statusSummary(), sender);
        } else {
            ZMusic.message.sendNormalMessage("您已登录网易云音乐，昵称: " + nickname, sender);
        }
    }

    private static void handleServiceLoginCommand(Object sender, String[] args) {
        String service = args[1].toLowerCase(Locale.ROOT);
        String displayName = "qq".equals(service) ? "QQ音乐" : "酷狗音乐";
        if ("kuwo".equals(service)) {
            displayName = "Kuwo Music";
        }
        if (args.length < 3) {
            ZMusic.message.sendErrorMessage("用法: /zm login " + service + " <qr|raw|status>", sender);
            return;
        }
        if ("qr".equalsIgnoreCase(args[2])) {
            sendServiceQrHelp(service, displayName, sender);
            return;
        }
        if ("status".equalsIgnoreCase(args[2])) {
            if (ServiceCookieUtils.hasCookies(service)) {
                ZMusic.message.sendNormalMessage(displayName + " cookies 已保存。", sender);
                ZMusic.message.sendNormalMessage(describeServiceCookies(service), sender);
            } else {
                ZMusic.message.sendNormalMessage(displayName + " cookies 未保存。", sender);
            }
            return;
        }
        if (!"raw".equalsIgnoreCase(args[2])) {
            ZMusic.message.sendErrorMessage("用法: /zm login " + service + " <qr|raw|status>", sender);
            return;
        }
        if (args.length < 4) {
            ZMusic.message.sendErrorMessage("用法: /zm login " + service + " raw [cookie1=value1; cookie2=value2; ...]", sender);
            ZMusic.message.sendErrorMessage("请先在浏览器登录" + displayName + "网页版，然后在控制台执行 console.log(document.cookie); 再粘贴结果。", sender);
            return;
        }
        String rawCookies = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (!rawCookies.contains("=")) {
            ZMusic.message.sendErrorMessage("无效的 Cookies 格式。", sender);
            return;
        }
        ServiceCookieUtils.saveCookies(service, rawCookies);
        ZMusic.message.sendNormalMessage(displayName + " cookies 已保存。", sender);
        ZMusic.message.sendNormalMessage(describeServiceCookies(service), sender);
    }

    private static void sendServiceQrHelp(String service, String displayName, Object sender) {
        if ("kuwo".equalsIgnoreCase(service)) {
            ZMusic.message.sendNormalMessage("Kuwo Music does not provide a stable public QR login API for this plugin.", sender);
            ZMusic.message.sendNormalMessage("Open https://www.kuwo.cn/ in your browser, login, then copy the full Network Cookie.", sender);
            ZMusic.message.sendNormalMessage("Then use /zm login kuwo raw <full Cookie>", sender);
            ZMusic.message.sendNormalMessage("Tip: the Cookie should usually include kw_token; Network Cookie is better than document.cookie.", sender);
            return;
        }
        if ("qq".equalsIgnoreCase(service)) {
            ZMusic.message.sendNormalMessage("QQ音乐没有稳定公开的插件二维码登录 API。", sender);
            ZMusic.message.sendNormalMessage("请打开 https://y.qq.com/ ，网页登录后复制 Network 请求头里的完整 Cookie。", sender);
        } else {
            ZMusic.message.sendNormalMessage("酷狗音乐没有稳定公开的插件二维码登录 API。", sender);
            ZMusic.message.sendNormalMessage("请打开 https://www.kugou.com/ ，网页登录后复制 Network 请求头里的完整 Cookie。", sender);
        }
        ZMusic.message.sendNormalMessage("然后使用 /zm login " + service + " raw <完整Cookie>", sender);
        ZMusic.message.sendNormalMessage("注意: document.cookie 可能缺少 HttpOnly 登录字段，优先复制 Network 里的 Cookie 请求头。", sender);
    }

    private static String describeServiceCookies(String service) {
        String cookies = ServiceCookieUtils.getCookies(service);
        if (cookies.isEmpty()) {
            return "Cookie status: empty";
        }
        if ("qq".equalsIgnoreCase(service)) {
            String uin = ServiceCookieUtils.getCookieValue(cookies, "uin");
            if (uin.isEmpty()) {
                uin = ServiceCookieUtils.getCookieValue(cookies, "qqmusic_uin");
            }
            boolean hasToken = !ServiceCookieUtils.getCookieValue(cookies, "qm_keyst").isEmpty()
                    || !ServiceCookieUtils.getCookieValue(cookies, "qqmusic_key").isEmpty()
                    || !ServiceCookieUtils.getCookieValue(cookies, "p_skey").isEmpty()
                    || !ServiceCookieUtils.getCookieValue(cookies, "skey").isEmpty();
            return "QQ cookie status: uin=" + (uin.isEmpty() ? "missing" : mask(uin))
                    + ", auth token=" + (hasToken ? "present" : "missing")
                    + ". If purl is empty, QQ official API did not authorize this song URL.";
        }
        if ("kugou".equalsIgnoreCase(service)) {
            String kugoo = ServiceCookieUtils.getCookieValue(cookies, "KuGoo");
            String mid = ServiceCookieUtils.getCookieValue(cookies, "kg_mid");
            boolean hasUser = kugoo.contains("KugooID=");
            boolean hasToken = kugoo.contains("&t=") || kugoo.contains(" t=");
            return "Kugou cookie status: KugooID=" + (hasUser ? "present" : "missing")
                    + ", token=" + (hasToken ? "present" : "missing")
                    + ", kg_mid=" + (mid.isEmpty() ? "missing" : "present")
                    + ". If error says paid/official API returned no URL, the web cookie was not enough for that member song.";
        }
        if ("kuwo".equalsIgnoreCase(service)) {
            String token = ServiceCookieUtils.getCookieValue(cookies, "kw_token");
            return "Kuwo cookie status: kw_token=" + (token.isEmpty() ? "missing" : "present")
                    + ", cookie=" + (cookies.isEmpty() ? "missing" : "present")
                    + ". If VIP URL is empty, Kuwo official API did not authorize this song URL.";
        }
        return "Cookie status: length=" + cookies.length();
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "present";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private static void handleHelpCommand(Object sender, String[] args) {
        // Extracted logic for "help" command
        if (args.length == 2) {
            HelpUtils.sendHelp(args[1], sender);
        } else {
            HelpUtils.sendHelp("main", sender);
        }
    }

    private static void handleReloadCommand(Object sender, boolean isAdmin) {
        // Extracted logic for "reload" command
        if (isAdmin) {
            new LoadConfig().reload(sender);
            ZMusic.runTask.runAsync(() -> new LoadLang().load());
        } else {
            ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.admin 权限此使用命令.", sender);
        }
    }

    private static void handleUpdateCommand(Object sender, boolean isAdmin) {
        // Extracted logic for "update" command
        if (isAdmin) {
            OtherUtils.checkUpdate(sender, true);
        } else {
            ZMusic.message.sendErrorMessage("权限不足，你需要 zmusic.admin 权限此使用命令.", sender);
        }
    }

    private static void handleTestCommand(Object sender) {
        // Extracted logic for "test" command
        if (ZMusic.player.isPlayer(sender)) {
            ZMusic.runTask.runAsync(() -> {
                ProgressBar progressBar = new ProgressBar('■', '□', 100 - 1);
                for (int i = 0; i < 100; i++) {
                    progressBar.setProgress(i);
                    try {
                        ZMusic.message.sendActionBarMessage(progressBar.getString(), sender);
                        Thread.sleep(10);
                    } catch (Exception ignored) {
                    }
                }
            });
        } else {
            ZMusic.message.sendErrorMessage("错误: 该命令只能由玩家使用", sender);
        }
    }

    private static void loginByQRCode(Object sender) {
        ZMusic.runTask.runAsync(() -> {
            try {
                String key = NeteaseLogin.key();
                String qrcode = NeteaseLogin.create(key);
                ZMusic.message.sendNormalMessage("请打开如下网址扫描二维码登录:", sender);
                ZMusic.message.sendNormalMessage(qrcode, sender);
                ZMusic.runTask.runAsync(() -> {
                    boolean cancel = false;
                    while (!cancel) {
                        try {
                            Thread.sleep(3000);
                            int code = NeteaseLogin.check(key);
                            switch (code) {
                                case 800:
                                    ZMusic.message.sendErrorMessage("二维码已过期!", sender);
                                    cancel = true;
                                    break;
                                case 802:
                                    ZMusic.message.sendNormalMessage("请在手机上确认登录!", sender);
                                    break;
                                case 803:
                                    String nickname = NeteaseLogin.nickname();
                                    if (nickname.isEmpty()) {
                                        ZMusic.message.sendErrorMessage("NetEase QR confirmed, but API still reports not logged in.", sender);
                                        ZMusic.message.sendErrorMessage("NetEase status: " + NeteaseLogin.statusSummary(), sender);
                                    } else {
                                        ZMusic.message.sendNormalMessage(
                                                "您已登录网易云音乐, 昵称: " + nickname,
                                                sender);
                                    }
                                    cancel = true;
                                    break;
                                default:
                            }
                        } catch (InterruptedException e) {
                            ZMusic.message.sendErrorMessage("登录失败! 请检查后台错误. (中断信号)",
                                    sender);
                            e.printStackTrace();
                            cancel = true;
                        }
                    }
                });
            } catch (UnsupportedEncodingException e) {
                ZMusic.message.sendErrorMessage("登录失败! 请检查后台错误. (不支持的编码)", sender);
                e.printStackTrace();
            } catch (Exception e) {
                ZMusic.message.sendErrorMessage("登录失败! 请检查后台错误和 api.netease。", sender);
                e.printStackTrace();
            }
        });
    }

    private static void loginByPhone(Object sender, String username, String password, String ctCode) {
        if (System.currentTimeMillis() >= 0) {
            ZMusic.message.sendErrorMessage("警告: 密码登录容易触发风控，建议使用二维码或 raw cookie 登录。", sender);
            ZMusic.runTask.runAsync(() -> {
                String md5 = OtherUtils.md5(password);
                boolean ok = NeteaseLogin.password_phone(username, md5, ctCode, true);
                if (ok) {
                    ZMusic.message.sendNormalMessage("网易云手机号登录成功。", sender);
                } else {
                    ZMusic.message.sendErrorMessage("网易云手机号登录失败，请检查后台日志和 api.netease。", sender);
                }
            });
            return;
        }
        ZMusic.message.sendErrorMessage("警告: 密码登录容易引发风控，建议使用二维码登录。", sender);
        ZMusic.message.sendErrorMessage("安全提示: 密码可能会被服务器日志记录，请确保服务器安全。", sender);

        String md5 = OtherUtils.md5(password);
        NeteaseLogin.password_phone(username, md5, ctCode, true);
    }

    private static void loginByEmail(Object sender, String username, String password) {
        if (System.currentTimeMillis() >= 0) {
            ZMusic.message.sendErrorMessage("警告: 密码登录容易触发风控，建议使用二维码或 raw cookie 登录。", sender);
            ZMusic.runTask.runAsync(() -> {
                String md5 = OtherUtils.md5(password);
                boolean ok = NeteaseLogin.password_email(username, md5, true);
                if (ok) {
                    ZMusic.message.sendNormalMessage("网易云邮箱登录成功。", sender);
                } else {
                    ZMusic.message.sendErrorMessage("网易云邮箱登录失败，请检查后台日志和 api.netease。", sender);
                }
            });
            return;
        }
        ZMusic.message.sendErrorMessage("警告: 密码登录容易引发风控，建议使用二维码登录。", sender);
        ZMusic.message.sendErrorMessage("安全提示: 密码可能会被服务器日志记录，请确保服务器安全。", sender);

        String md5 = OtherUtils.md5(password);
        NeteaseLogin.password_email(username, md5, true);
    }

    private static void loginSendCode(Object sender, String phone, String ctCode) {
        if (System.currentTimeMillis() >= 0) {
            ZMusic.runTask.runAsync(() -> {
                boolean ok = NeteaseLogin.sendCode(phone, ctCode);
                if (ok) {
                    ZMusic.message.sendNormalMessage("验证码已发送至: " + phone + "，请查收后使用 verify 校验。", sender);
                } else {
                    ZMusic.message.sendErrorMessage("验证码发送失败，请检查后台日志和 api.netease。", sender);
                }
            });
            return;
        }
        NeteaseLogin.sendCode(phone, ctCode);
        ZMusic.message.sendNormalMessage("验证码已发送至: " + phone + ", 请查收后使用 verify 校验", sender);
    }

    private static void loginByVerifyCode(Object sender, String phone, String ctCode, String code) {
        if (System.currentTimeMillis() >= 0) {
            ZMusic.runTask.runAsync(() -> {
                boolean ok = NeteaseLogin.verify(phone, code, ctCode);
                if (ok) {
                    ZMusic.message.sendNormalMessage("网易云验证码登录成功: " + phone, sender);
                } else {
                    ZMusic.message.sendErrorMessage("网易云验证码登录失败，请检查验证码、后台日志和 api.netease。", sender);
                }
            });
            return;
        }
        NeteaseLogin.verify(phone, code, ctCode);
        ZMusic.message.sendNormalMessage("验证码验证请求已提交: " + phone, sender);

    }

    private static ParsedPhoneInput parseCtCodeAndPhone(String[] args, int startIndex, Object sender, String usage) {
        if (args.length <= startIndex) {
            ZMusic.message.sendErrorMessage(usage, sender);
            return null;
        }

        String ctCode = "86";
        int phoneIndex = startIndex;
        String firstToken = String.valueOf(args[startIndex]);
        if (firstToken.startsWith("+")) {
            ctCode = firstToken.substring(1);
            phoneIndex = startIndex + 1;
        }

        if (ctCode.isEmpty() || args.length <= phoneIndex) {
            ZMusic.message.sendErrorMessage(usage, sender);
            return null;
        }

        String phone = String.valueOf(args[phoneIndex]);
        if (phone.isEmpty()) {
            ZMusic.message.sendErrorMessage(usage, sender);
            return null;
        }

        return new ParsedPhoneInput(ctCode, phone, phoneIndex + 1);
    }

    private static class ParsedPhoneInput {
        private final String ctCode;
        private final String phone;
        private final int nextIndex;

        private ParsedPhoneInput(String ctCode, String phone, int nextIndex) {
            this.ctCode = ctCode;
            this.phone = phone;
            this.nextIndex = nextIndex;
        }

    }
}
