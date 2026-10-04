package me.zhenxin.zmusic.login;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原生 QQ/微信扫码及手机号验证码登录。
 * 流程参考 L-1124/QQMusicApi（GPL-3.0），独立实现为 Java，不依赖外部 API 服务。
 */
public final class QQLogin {
    private static final String API = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String REFERER = "https://xui.ptlogin2.qq.com/";
    private final LoginHttp http;
    private final String guid = UUID.randomUUID().toString().replace("-", "");
    private boolean wechat;
    private String identifier;
    private String sessionUid, sessionSid;
    public QQLogin() { this(new LoginHttp()); }
    public QQLogin(LoginHttp http) { this.http = http; }

    public Image createQr(boolean wechat) throws IOException {
        this.wechat = wechat;
        if (!wechat) {
            LoginHttp.Response response = http.request("GET", "https://ssl.ptlogin2.qq.com/ptqrshow?"
                    + LoginHttp.form(LoginHttp.params("appid", "716027609", "e", "2", "l", "M", "s", "3", "d", "72",
                    "v", "4", "t", Double.toString(Math.random()), "daid", "383", "pt_3rd_aid", "100497308")), REFERER, null, null);
            identifier = http.cookie("qrsig");
            if (identifier.isEmpty()) throw new IOException("QQ未返回二维码会话，请稍后重试。");
            return new Image(response.bytes, "image/png");
        }
        String url = "https://open.weixin.qq.com/connect/qrconnect?" + LoginHttp.form(LoginHttp.params(
                "appid", "wx48db31d50e334801", "redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/",
                "response_type", "code", "scope", "snsapi_login", "state", "STATE",
                "href", "https://y.qq.com/mediastyle/music_v17/src/css/popup_wechat.css#wechat_redirect"));
        String page = http.request("GET", url, "https://y.qq.com/", null, null).text();
        Matcher matcher = Pattern.compile("/connect/qrcode/([A-Za-z0-9_-]+)").matcher(page);
        if (!matcher.find()) throw new IOException("微信未返回登录二维码，请稍后重试。");
        identifier = matcher.group(1);
        return new Image(http.request("GET", "https://open.weixin.qq.com/connect/qrcode/" + identifier, url, null, null).bytes, "image/jpeg");
    }

    public Result checkQr() throws IOException {
        if (identifier == null) throw new IOException("请先生成登录二维码。");
        if (wechat) {
            String raw;
            try {
                raw = http.request("GET", "https://lp.open.weixin.qq.com/connect/l/qrconnect?uuid=" + LoginHttp.encode(identifier),
                        "https://open.weixin.qq.com/", null, null).text();
            } catch (java.net.SocketTimeoutException waiting) { return new Result("WAIT", ""); }
            Matcher code = Pattern.compile("wx_errcode\\s*=\\s*(\\d+)").matcher(raw);
            int status = code.find() ? Integer.parseInt(code.group(1)) : -1;
            if (status == 408) return new Result("WAIT", "");
            if (status == 404) return new Result("CONFIRM", "");
            if (status == 402 || status == 403) return new Result("EXPIRED", "");
            Matcher auth = Pattern.compile("wx_code\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(raw);
            if (status != 405 || !auth.find()) throw new IOException("微信登录状态异常，请重新扫码。");
            JsonObject params = new JsonObject();
            params.addProperty("code", auth.group(1)); params.addProperty("strAppid", "wx48db31d50e334801");
            return new Result("DONE", QQAccount.credentialCookies(cgi("music.login.LoginServer", "Login", params, 1, false), 1));
        }
        String response = http.request("GET", "https://ssl.ptlogin2.qq.com/ptqrlogin?" + LoginHttp.form(LoginHttp.params(
                "u1", "https://graph.qq.com/oauth2.0/login_jump", "ptqrtoken", Integer.toString(QQAccount.hash(identifier, 0)),
                "ptredirect", "0", "h", "1", "t", "1", "g", "1", "from_ui", "1", "ptlang", "2052",
                "action", "0-0-" + System.currentTimeMillis(), "js_ver", "20102616", "js_type", "1", "pt_uistyle", "40",
                "aid", "716027609", "daid", "383", "pt_3rd_aid", "100497308", "has_onekey", "1")), REFERER, null, null).text();
        List<String> args = callbackArgs(response);
        String status = args.isEmpty() ? "" : args.get(0);
        if ("66".equals(status)) return new Result("WAIT", "");
        if ("67".equals(status)) return new Result("CONFIRM", "");
        if ("65".equals(status)) return new Result("EXPIRED", "");
        if (!"0".equals(status) || args.size() < 3) throw new IOException("QQ登录状态异常，请重新扫码。");
        String uin = LoginHttp.query(args.get(2), "uin"), sigx = LoginHttp.query(args.get(2), "ptsigx");
        if (!uin.matches("\\d+") || sigx.isEmpty()) throw new IOException("QQ未返回有效的扫码授权信息。");
        http.request("GET", "https://ssl.ptlogin2.graph.qq.com/check_sig?" + LoginHttp.form(LoginHttp.params(
                "uin", uin, "pttype", "1", "service", "ptqrlogin", "nodirect", "0", "ptsigx", sigx,
                "s_url", "https://graph.qq.com/oauth2.0/login_jump", "ptlang", "2052", "ptredirect", "100",
                "aid", "716027609", "daid", "383", "j_later", "0", "low_login_hour", "0", "regmaster", "0",
                "pt_login_type", "3", "pt_aid", "0", "pt_aaid", "16", "pt_light", "0", "pt_3rd_aid", "100497308")), REFERER, null, null);
        String skey = http.cookie("p_skey");
        if (skey.isEmpty()) throw new IOException("QQ扫码成功，但未完成音乐授权，请重新扫码。");
        LoginHttp.Response authorization = http.request("POST", "https://graph.qq.com/oauth2.0/authorize", REFERER,
                LoginHttp.form(LoginHttp.params("response_type", "code", "client_id", "100497308",
                "redirect_uri", "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/",
                "scope", "get_user_info,get_app_friends", "state", "state", "switch", "", "from_ptlogin", "1",
                "src", "1", "update_auth", "1", "openapi", "1010_1030", "g_tk", Integer.toString(QQAccount.hash(skey, 5381)),
                "auth_time", Long.toString(System.currentTimeMillis()), "ui", UUID.randomUUID().toString())), "application/x-www-form-urlencoded");
        String code = LoginHttp.query(authorization.location, "code");
        if (code.isEmpty()) throw new IOException("QQ账号未完成音乐授权，请重试或使用微信扫码。");
        JsonObject params = new JsonObject(); params.addProperty("code", code);
        return new Result("DONE", QQAccount.credentialCookies(cgi("QQConnectLogin.LoginServer", "QQLogin", params, 2, false), 2));
    }

    public String sendCode(String phone, String country) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("phoneNo", phone); params.addProperty("areaCode", country); params.addProperty("tmeAppid", "qqmusic");
        JsonObject response = requestCgi("music.login.LoginServer", "SendPhoneAuthCode", params, 0, true);
        JsonObject req = response.getAsJsonObject("req_0");
        int code = req.get("code").getAsInt();
        if (code == 0) return "";
        if (code == 104400) throw new IOException("QQ音乐拒绝发送短信，请检查手机号和国家区号是否正确。");
        if (code == 100001 && req.has("data")) {
            String captcha = QQAccount.get(req.getAsJsonObject("data"), "securityURL", "");
            try {
                java.net.URI target = java.net.URI.create(captcha);
                String host = target.getHost();
                if ("https".equals(target.getScheme()) && target.getUserInfo() == null && host != null
                        && (host.equals("qq.com") || host.endsWith(".qq.com") || host.equals("qcloud.com") || host.endsWith(".qcloud.com"))) return captcha;
            } catch (IllegalArgumentException ignored) { }
        }
        throw new IOException(error(code));
    }

    public String verify(String phone, String code) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("phoneNo", phone); params.addProperty("code", code); params.addProperty("loginMode", 1);
        return QQAccount.credentialCookies(cgi("music.login.LoginServer", "Login", params, 0, true), 0);
    }

    public JsonObject cgi(String module, String method, JsonObject params, int type, boolean phone) throws IOException {
        return data(requestCgi(module, method, params, type, phone));
    }
    private JsonObject requestCgi(String module, String method, JsonObject params, int type, boolean phone) throws IOException {
        if (phone && sessionSid == null) ensurePhoneSession();
        JsonObject comm = phone ? androidComm() : QQAccount.comm("");
        comm.addProperty("tmeLoginType", type);
        if (phone) {
            comm.addProperty("tmeLoginMethod", 3); comm.addProperty("tmeAppID", "qqmusic");
            comm.addProperty("uid", sessionUid); comm.addProperty("sid", sessionSid);
        }
        JsonObject request = new JsonObject();
        request.addProperty("module", module); request.addProperty("method", method); request.add("param", params);
        JsonObject payload = new JsonObject(); payload.add("comm", comm); payload.add("req_0", request);
        return http.request("POST", API, "https://y.qq.com/", payload.toString(), "application/json; charset=UTF-8").json();
    }
    private JsonObject androidComm() {
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 11); comm.addProperty("cv", 14090008); comm.addProperty("v", 14090008);
        comm.addProperty("chid", "10003505"); comm.addProperty("tmeAppID", "qqmusic");
        comm.addProperty("OpenUDID", guid); comm.addProperty("OpenUDID2", guid); comm.addProperty("udid", guid);
        comm.addProperty("aid", guid.substring(0, 16)); comm.addProperty("os_ver", "13");
        comm.addProperty("phonetype", "MI 11"); comm.addProperty("devicelevel", "33"); comm.addProperty("newdevicelevel", "33");
        comm.addProperty("QIMEI", ""); comm.addProperty("QIMEI36", "");
        return comm;
    }
    private void ensurePhoneSession() throws IOException {
        http.androidAgent();
        JsonObject params = new JsonObject(); params.addProperty("uid", ""); params.addProperty("vkey", 0); params.addProperty("caller", 2);
        JsonObject req = new JsonObject(); req.addProperty("module", "music.getSession.session"); req.addProperty("method", "GetSession"); req.add("param", params);
        JsonObject payload = new JsonObject(); payload.add("comm", androidComm()); payload.add("req_0", req);
        JsonObject data = data(http.request("POST", API, "https://y.qq.com/", payload.toString(), "application/json; charset=UTF-8").json());
        JsonObject session = data == null ? null : data.getAsJsonObject("session");
        sessionUid = QQAccount.get(session, "uid", ""); sessionSid = QQAccount.get(session, "sid", "");
        if (sessionUid.isEmpty() || sessionSid.isEmpty()) {
            sessionSid = null;
            throw new IOException("QQ音乐未建立短信登录会话，请使用 /zm login qq qr 扫码登录。");
        }
    }
    public static JsonObject data(JsonObject response) throws IOException {
        try {
            int global = response.has("code") ? response.get("code").getAsInt() : 0;
            JsonObject req = response.getAsJsonObject("req_0");
            int code = req.get("code").getAsInt();
            if (global != 0 || code != 0) throw new IOException(error(global != 0 ? global : code));
            JsonObject data = req.getAsJsonObject("data");
            if (data != null && data.has("code") && data.get("code").getAsInt() != 0) throw new IOException(error(data.get("code").getAsInt()));
            return data;
        } catch (RuntimeException malformed) { throw new IOException("QQ音乐登录响应格式异常，请稍后重试。"); }
    }
    static List<String> callbackArgs(String raw) {
        List<String> args = new ArrayList<>();
        int begin = raw.indexOf("ptuiCB(");
        if (begin < 0) return args;
        Matcher matcher = Pattern.compile("'((?:\\\\.|[^'\\\\])*)'").matcher(raw.substring(begin + 7));
        while (matcher.find()) args.add(matcher.group(1).replace("\\'", "'").replace("\\\\", "\\"));
        return args;
    }
    public static String error(int code) {
        switch (code) {
            case 1000: case 104400: case 104401: return "QQ音乐登录凭据已失效，请重新扫码登录。";
            case 20271: return "QQ音乐短信验证码不正确。";
            case 20279: return "QQ音乐账号登录设备数量达到上限。";
            case 100002: case 104604: return "QQ音乐操作过于频繁，请稍后再试。";
            default: return "QQ音乐拒绝本次登录请求（状态码：" + code + "），请使用扫码或在官方客户端完成安全验证。";
        }
    }
    public static final class Image {
        public final byte[] bytes; public final String mime;
        public Image(byte[] bytes, String mime) { this.bytes = bytes; this.mime = mime; }
    }
    public static final class Result {
        public final String state, cookies;
        public Result(String state, String cookies) { this.state = state; this.cookies = cookies; }
    }
}
