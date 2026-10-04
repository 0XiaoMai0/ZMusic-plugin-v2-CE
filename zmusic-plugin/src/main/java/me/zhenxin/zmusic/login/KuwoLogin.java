package me.zhenxin.zmusic.login;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/** 按酷我官网的图形验证码、密码及短信流程登录；不绕过验证码。 */
public final class KuwoLogin {
    private final LoginHttp http;
    private String captchaToken, smsTime, phone;
    public KuwoLogin() { this(new LoginHttp()); }
    public KuwoLogin(LoginHttp http) { this.http = http; }

    public QQLogin.Image captcha() throws IOException {
        JsonObject data = data(http.request("GET", "https://www.kuwo.cn/api/common/captcha/getcode?httpsStatus=1&reqId="
                + UUID.randomUUID(), "https://www.kuwo.cn/", null, null).json());
        captchaToken = QQAccount.get(data, "token", "");
        String image = QQAccount.get(data, "img", "");
        if (captchaToken.isEmpty() || !image.startsWith("data:image/png;base64,")) throw new IOException("酷我未返回有效的图形验证码，请稍后重试。");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1)); }
        catch (RuntimeException error) { throw new IOException("酷我图形验证码格式异常。"); }
        return new QQLogin.Image(bytes, "image/png");
    }

    public String password(String user, String password, String captcha) throws IOException {
        requireCaptcha();
        JsonObject params = new JsonObject();
        params.addProperty("uname", user); params.addProperty("password", password);
        params.addProperty("verifyCode", captcha); params.addProperty("verifyCodeToken", captchaToken);
        return credentials(data(post("https://wapi.kuwo.cn/api/www/login/loginByKw", params)));
    }

    public void sendCode(String phone, String captcha) throws IOException {
        requireCaptcha();
        JsonObject params = new JsonObject();
        params.addProperty("mobile", phone); params.addProperty("verifyCode", captcha);
        params.addProperty("verifyCodeToken", captchaToken);
        JsonObject result = data(post("https://www.kuwo.cn/api/sms/mobileLoginCode", params));
        String time = QQAccount.get(result, "tm", "");
        if (time.isEmpty()) throw new IOException("酷我未返回短信登录会话，请重新获取验证码。");
        this.phone = phone; smsTime = time;
    }

    public String verify(String phone, String code) throws IOException {
        if (smsTime == null || !phone.equals(this.phone)) throw new IOException("请先为这个手机号发送短信验证码。");
        JsonObject params = new JsonObject();
        params.addProperty("mobile", phone); params.addProperty("smsCode", code); params.addProperty("tm", smsTime);
        return credentials(data(post("https://wapi.kuwo.cn/api/www/login/loginByMobile", params)));
    }
    private JsonObject post(String url, JsonObject params) throws IOException {
        return http.request("POST", url + "?httpsStatus=1&reqId=" + UUID.randomUUID(), "https://www.kuwo.cn/",
                params.toString(), "application/json; charset=UTF-8").json();
    }
    private void requireCaptcha() throws IOException {
        if (captchaToken == null || captchaToken.isEmpty()) throw new IOException("请先使用 /zm login kuwo captcha 获取图形验证码。");
    }
    static JsonObject data(JsonObject response) throws IOException {
        try {
            int code = response.get("code").getAsInt();
            if (code != 200) throw new IOException(error(code));
            return response.getAsJsonObject("data");
        } catch (RuntimeException error) { throw new IOException("酷我登录响应格式异常，请稍后重试。"); }
    }
    public static String credentials(JsonObject data) throws IOException {
        JsonObject cookies = data == null ? null : data.getAsJsonObject("cookies");
        if (cookies == null || QQAccount.get(cookies, "userid", "").isEmpty()
                || QQAccount.get(cookies, "sid", QQAccount.get(cookies, "websid", "")).isEmpty()) {
            throw new IOException("酷我未返回有效登录凭据，请重新登录。");
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, JsonElement> item : cookies.entrySet()) {
            if (!item.getKey().matches("[A-Za-z0-9_-]+") || !item.getValue().isJsonPrimitive()) continue;
            if (result.length() > 0) result.append("; ");
            result.append(item.getKey()).append('=').append(LoginHttp.encode(item.getValue().getAsString()).replace("+", "%20"));
        }
        return result.toString();
    }
    public static String error(int code) {
        switch (code) {
            case -10001: return "酷我账号或密码不正确，请重新输入。";
            case -102: return "酷我验证码不正确，请重新获取并输入验证码。";
            case 1108: return "酷我短信验证码不正确。";
            case 1107: return "酷我短信验证码已过期。";
            case 1100: return "酷我要求有效的中国大陆手机号。";
            case 1157: return "酷我限制本次账号操作，请在官方客户端确认账号状态。";
            default: return "酷我拒绝本次登录请求（状态码：" + code + "），请稍后重试。";
        }
    }
}
