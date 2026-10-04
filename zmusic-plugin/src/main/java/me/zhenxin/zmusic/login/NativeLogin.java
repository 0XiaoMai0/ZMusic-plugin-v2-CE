package me.zhenxin.zmusic.login;

import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.component.ZClickEvent;
import me.zhenxin.zmusic.component.ZHoverEvent;
import me.zhenxin.zmusic.component.ZTextComponent;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.language.MusicErrorMessages;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 管理员使用的原生平台登录；会话有界，关闭或取消后不会继续保存凭据。 */
public final class NativeLogin {
    private static final Object LOCK = new Object();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static ScheduledExecutorService executor;
    private static volatile boolean enabled = true;
    private NativeLogin() { }

    public static void close() {
        synchronized (LOCK) {
            enabled = false;
            for (Session session : SESSIONS.values()) session.cancel();
            SESSIONS.clear();
            if (executor != null) executor.shutdownNow();
            executor = null;
        }
    }

    public static void open() { synchronized (LOCK) { enabled = true; } }

    public static void handle(Object sender, String service, String[] args) {
        try {
            if (!enabled) throw new IOException("插件正在关闭或重载，请稍后登录。");
            String method = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
            switch (method) {
                case "status": status(sender, service); return;
                case "cancel":
                    cancel(service);
                    normal(sender, "已取消" + name(service) + "当前登录会话。"); return;
                case "raw":
                    if (args.length < 4) { help(sender, service); return; }
                    String cookie = ServiceCookieUtils.normalizeRawCookie(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                    if ("qq".equals(service) && (QQAccount.uin(cookie).equals("0") || QQAccount.key(cookie).isEmpty())) {
                        throw new IOException("QQ音乐凭据缺少账号编号或音乐登录密钥，请使用扫码登录或复制完整的 QQ音乐 Cookie。");
                    }
                    if ("kuwo".equals(service) && (QQAccount.value(cookie, "userid").isEmpty() || QQAccount.value(cookie, "sid", "websid").isEmpty())) {
                        throw new IOException("酷我凭据缺少 userid 或 sid，请使用密码、短信登录或复制完整的酷我 Cookie。");
                    }
                    cancel(service);
                    ServiceCookieUtils.saveCookies(service, cookie);
                    normal(sender, name(service) + "凭据已保存；请使用 status 检查登录状态。"); return;
                case "qr":
                    if (!service.equals("qq")) { help(sender, service); return; }
                    if (args.length > 4 || args.length == 4 && !Arrays.asList("qq", "wechat", "wx").contains(args[3].toLowerCase(Locale.ROOT))) {
                        help(sender, service); return;
                    }
                    Session qr = replace(service, sender);
                    boolean wechat = args.length == 4 && !args[3].equalsIgnoreCase("qq");
                    try {
                        QQLogin.Image created = qr.qq.createQr(wechat);
                        synchronized (LOCK) {
                            if (!enabled || SESSIONS.get(service) != qr) throw new IOException("登录会话已取消。");
                            qr.image = LoginImages.publish(created);
                        }
                        image(sender, qr.image, "打开" + (wechat ? "微信" : "QQ") + "登录二维码，在手机上扫码并确认；有效期约 3 分钟。");
                        schedule(qr);
                    } catch (Exception error) { cancel(service, qr); throw error; }
                    return;
                case "captcha":
                    if (!service.equals("kuwo") || args.length != 3) { help(sender, service); return; }
                    Session captcha = replace(service, sender);
                    try {
                        QQLogin.Image created = captcha.kuwo.captcha();
                        synchronized (LOCK) {
                            if (!enabled || SESSIONS.get(service) != captcha) throw new IOException("登录会话已取消。");
                            captcha.image = LoginImages.publish(created);
                        }
                        image(sender, captcha.image, "打开酷我图形验证码，然后使用 password 或 sendcode 登录；有效期 5 分钟。");
                    } catch (Exception error) { cancel(service, captcha); throw error; }
                    return;
                case "password":
                    if (!service.equals("kuwo") || args.length != 6) { help(sender, service); return; }
                    Session password = existing(service, sender);
                    synchronized (password) { complete(password, password.kuwo.password(args[3], args[4], args[5])); }
                    return;
                case "sendcode":
                    if (service.equals("qq")) {
                        PhoneInput input = phone(args, false);
                        Session sms = replace(service, sender);
                        String security;
                        synchronized (sms) { security = sms.qq.sendCode(input.phone, input.country); sms.phone = input.phone; }
                        if (!security.isEmpty()) {
                            image(sender, security, "QQ要求先完成安全验证，请打开链接验证后重新执行 sendcode。");
                        } else normal(sender, "QQ音乐短信验证码已发送，请使用 /zm login qq verify <手机号> <短信验证码>。");
                    } else {
                        if (args.length != 5 || !args[3].matches("1\\d{10}")) { help(sender, service); return; }
                        Session sms = existing(service, sender);
                        synchronized (sms) { sms.kuwo.sendCode(args[3], args[4]); sms.phone = args[3]; }
                        normal(sender, "酷我短信验证码已发送，请使用 /zm login kuwo verify <手机号> <短信验证码>。");
                    }
                    return;
                case "verify": case "phone":
                    if (service.equals("qq")) {
                        PhoneInput input = phone(args, true);
                        Session sms = existing(service, sender);
                        if (!input.phone.equals(sms.phone)) throw new IOException("请先为这个手机号发送短信验证码。");
                        synchronized (sms) { complete(sms, sms.qq.verify(input.phone, input.code)); }
                    } else {
                        if (args.length != 5) { help(sender, service); return; }
                        Session sms = existing(service, sender);
                        synchronized (sms) { complete(sms, sms.kuwo.verify(args[3], args[4])); }
                    }
                    return;
                default: help(sender, service);
            }
        } catch (Exception error) {
            String message = error instanceof IOException || error instanceof IllegalStateException
                    ? MusicErrorMessages.remoteMessage(error.getMessage(), "平台登录失败，请稍后重试。")
                    : "登录响应异常，请稍后重试。";
            ZMusic.message.sendErrorMessage(message, sender);
        }
    }

    private static Session replace(String service, Object sender) {
        synchronized (LOCK) {
            if (!enabled) throw new IllegalStateException("插件正在关闭，请稍后登录。");
            cancel(service);
            Session session = new Session(service, sender);
            SESSIONS.put(service, session);
            return session;
        }
    }
    private static Session existing(String service, Object sender) throws IOException {
        Session session = SESSIONS.get(service);
        if (session == null || session.expires < System.currentTimeMillis() || !session.owner.equals(owner(sender))) {
            throw new IOException("登录会话不存在或已过期，请重新获取二维码、图形验证码或短信验证码。");
        }
        return session;
    }
    private static void cancel(String service) {
        synchronized (LOCK) { Session session = SESSIONS.remove(service); if (session != null) session.cancel(); }
    }
    private static void cancel(String service, Session expected) {
        synchronized (LOCK) { if (SESSIONS.remove(service, expected)) expected.cancel(); }
    }
    private static void complete(Session session, String cookies) throws IOException {
        synchronized (LOCK) {
            if (SESSIONS.get(session.service) != session || session.expires < System.currentTimeMillis()) throw new IOException("登录会话已结束，请重新登录。");
            ServiceCookieUtils.saveCookies(session.service, cookies);
            cancel(session.service, session);
        }
        normal(session.sender, name(session.service) + "登录成功，凭据已保存。歌曲播放仍需账号拥有相应会员、购买或版权权限。");
    }
    private static void schedule(Session session) {
        synchronized (LOCK) {
            if (SESSIONS.get(session.service) != session) return;
            if (executor == null) executor = Executors.newScheduledThreadPool(2, task -> {
                Thread thread = new Thread(task, "ZMusic-Login"); thread.setDaemon(true); return thread;
            });
            session.expires = System.currentTimeMillis() + 3 * 60 * 1000L;
            session.poll = executor.scheduleWithFixedDelay(() -> poll(session), 3, 3, TimeUnit.SECONDS);
        }
    }
    private static void poll(Session session) {
        if (SESSIONS.get(session.service) != session) return;
        if (session.expires < System.currentTimeMillis()) {
            cancel(session.service, session); normal(session.sender, "QQ音乐登录二维码已过期，请重新获取。"); return;
        }
        try {
            QQLogin.Result result;
            synchronized (session) { result = session.qq.checkQr(); }
            if (SESSIONS.get(session.service) != session) return;
            session.failures = 0;
            switch (result.state) {
                case "DONE": complete(session, result.cookies); break;
                case "CONFIRM":
                    if (!session.confirmed) { session.confirmed = true; normal(session.sender, "已扫码，请在手机上确认登录 QQ音乐。"); }
                    break;
                case "EXPIRED": cancel(session.service, session); normal(session.sender, "QQ音乐二维码已失效，请重新获取。"); break;
                default: break;
            }
        } catch (Exception error) {
            if (SESSIONS.get(session.service) == session && ++session.failures >= 3) {
                cancel(session.service, session);
                ZMusic.message.sendErrorMessage("QQ音乐扫码登录失败，请重新获取二维码或使用微信扫码。", session.sender);
            }
        }
    }

    private static void status(Object sender, String service) throws IOException {
        String cookies = ServiceCookieUtils.getCookies(service);
        if (cookies.isEmpty()) { normal(sender, name(service) + "尚未登录，请输入 /zm login " + service + " 后按 Tab 选择登录方式。"); return; }
        if (service.equals("qq")) {
            if (QQAccount.uin(cookies).equals("0") || QQAccount.key(cookies).isEmpty()) throw new IOException("QQ音乐凭据不完整，请重新扫码登录。");
            JsonObject request = new JsonObject(); request.addProperty("module", "music.UserInfo.userInfoServer");
            request.addProperty("method", "GetLoginUserInfo"); request.add("param", new JsonObject());
            JsonObject payload = new JsonObject(); payload.add("comm", QQAccount.comm(cookies)); payload.add("req_0", request);
            JsonObject data = QQLogin.data(com.google.gson.JsonParser.parseString(WebMusicUtils.postJson(
                    "https://u.y.qq.com/cgi-bin/musicu.fcg", "https://y.qq.com/", cookies, payload.toString())).getAsJsonObject());
            if (data == null || data.entrySet().isEmpty()) throw new IOException("QQ音乐未确认登录账号，请重新扫码。");
            normal(sender, "QQ音乐账号鉴权已通过；具体歌曲的播放权限请使用 /zm diagnose qq <歌名> 检查。");
        } else {
            boolean complete = !QQAccount.value(cookies, "userid").isEmpty() && !QQAccount.value(cookies, "sid", "websid").isEmpty();
            normal(sender, complete ? "已保存酷我账号凭据；尚未验证会话有效性，请使用 /zm diagnose kuwo <歌名> 检查实际播放权限。" : "酷我凭据不完整，请重新使用密码或短信验证码登录。");
        }
    }

    static PhoneInput phone(String[] args, boolean code) throws IOException {
        int index = 3; String country = "86";
        if (args.length > index && args[index].startsWith("+")) { country = args[index++].substring(1); }
        if (!country.matches("\\d{1,4}") || args.length != index + (code ? 2 : 1) || !args[index].matches("\\d{5,15}")) {
            throw new IOException(code ? "用法：/zm login qq verify [+国家代码] <手机号> <短信验证码>" : "用法：/zm login qq sendcode [+国家代码] <手机号>");
        }
        if (code && !args[index + 1].matches("\\d{4,8}")) throw new IOException("请输入收到的数字短信验证码。");
        return new PhoneInput(country, args[index], code ? args[index + 1] : "");
    }
    private static String owner(Object sender) { return ZMusic.player.isPlayer(sender) ? ZMusic.player.getUniqueId(sender) : "console"; }
    private static String name(String service) { return service.equals("qq") ? "QQ音乐" : "酷我音乐"; }
    private static void normal(Object sender, String text) { ZMusic.message.sendNormalMessage(text, sender); }
    private static void image(Object sender, String address, String text) {
        normal(sender, text);
        if (!ZMusic.player.isPlayer(sender)) { normal(sender, address); return; }
        ZTextComponent link = ZTextComponent.of(Config.prefix + "§e[点击打开登录图片或安全验证]");
        link.setClickEvent(ZClickEvent.openUrl(address)); link.setHoverEvent(ZHoverEvent.showText("仅向你显示此登录链接，请勿转发。"));
        ZMusic.message.sendJsonMessage(link, sender);
    }
    private static void help(Object sender, String service) {
        normal(sender, "QQ".equalsIgnoreCase(service) ? "QQ音乐支持 QQ/微信扫码与手机号验证码登录，扫码登录推荐使用 /zm login qq qr。"
                : "酷我支持账号密码和手机号验证码登录，需要先获取图形验证码。");
        if (service.equals("qq")) {
            normal(sender, "/zm login qq qr [qq|wechat] - 生成二维码，扫码确认后自动保存登录凭据。");
            normal(sender, "/zm login qq sendcode [+国家代码] <手机号> - 发送短信验证码。");
            normal(sender, "/zm login qq verify [+国家代码] <手机号> <验证码> - 完成短信登录。");
        } else {
            normal(sender, "/zm login kuwo captcha - 获取图形验证码。");
            normal(sender, "/zm login kuwo password <账号> <密码> <图形验证码> - 账号密码登录。");
            normal(sender, "/zm login kuwo sendcode <手机号> <图形验证码> - 发送短信验证码。");
            normal(sender, "/zm login kuwo verify <手机号> <短信验证码> - 完成短信登录。");
        }
        normal(sender, "/zm login " + service + " <status|cancel|raw 完整Cookie> - 查看状态、取消会话或导入已有凭据。");
    }
    private static final class Session {
        final String service, owner; final Object sender;
        final QQLogin qq = new QQLogin(); final KuwoLogin kuwo = new KuwoLogin();
        volatile long expires = System.currentTimeMillis() + 5 * 60 * 1000L;
        volatile ScheduledFuture<?> poll; volatile String image, phone;
        boolean confirmed; int failures;
        Session(String service, Object sender) { this.service = service; this.sender = sender; this.owner = NativeLogin.owner(sender); }
        void cancel() { if (poll != null) poll.cancel(true); LoginImages.remove(image); }
    }
    static final class PhoneInput {
        final String country, phone, code;
        PhoneInput(String country, String phone, String code) { this.country = country; this.phone = phone; this.code = code; }
    }
}
