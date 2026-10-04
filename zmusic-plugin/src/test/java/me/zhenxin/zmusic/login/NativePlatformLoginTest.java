package me.zhenxin.zmusic.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativePlatformLoginTest {
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==";
    @Test void qqQrExchangesAuthorizationForMusicCredentials() throws Exception {
        MockHttp http = new MockHttp() {
            public String cookie(String name) { return name.equals("qrsig") ? "dummy-qr-session" : name.equals("p_skey") ? "dummy-oauth-key" : ""; }
            public Response request(String method, String url, String referer, String body, String type) {
                calls.add(url);
                if (url.contains("ptqrshow")) return image();
                if (url.contains("ptqrlogin")) return text("ptuiCB('0','0','https://ssl.ptlogin2.graph.qq.com/check_sig?uin=123&ptsigx=test-signature&service=ptqrlogin','0','登录成功！','昵称');");
                if (url.contains("check_sig")) return text("");
                if (url.contains("authorize")) {
                    assertEquals("POST", method);
                    assertTrue(body.contains("client_id=100497308"));
                    return new Response(302, new byte[0], "https://y.qq.com/portal/wx_redirect.html?code=test-auth-code&state=state");
                }
                JsonObject payload = JsonParser.parseString(body).getAsJsonObject();
                assertEquals("QQConnectLogin.LoginServer", payload.getAsJsonObject("req_0").get("module").getAsString());
                assertEquals("test-auth-code", payload.getAsJsonObject("req_0").getAsJsonObject("param").get("code").getAsString());
                assertEquals(2, payload.getAsJsonObject("comm").get("tmeLoginType").getAsInt());
                return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"musicid\":123,\"musickey\":\"dummy-music-key\"}}}");
            }
        };
        QQLogin login = new QQLogin(http);
        assertEquals("image/png", login.createQr(false).mime);
        QQLogin.Result result = login.checkQr();
        assertEquals("DONE", result.state);
        assertEquals("123", QQAccount.uin(result.cookies));
        assertEquals("dummy-music-key", QQAccount.key(result.cookies));
        assertEquals(5, http.calls.size());
    }
    @Test void qqWaitingAndExpiredQrNeverProduceCredentials() throws Exception {
        for (String status : new String[]{"66", "67", "65"}) {
            MockHttp http = new MockHttp() {
                public String cookie(String name) { return "dummy-qr"; }
                public Response request(String method, String url, String referer, String body, String type) {
                    return url.contains("ptqrshow") ? image() : text("ptuiCB('" + status + "','0','','0','状态','');");
                }
            };
            QQLogin login = new QQLogin(http); login.createQr(false);
            QQLogin.Result result = login.checkQr();
            assertEquals(status.equals("66") ? "WAIT" : status.equals("67") ? "CONFIRM" : "EXPIRED", result.state);
            assertTrue(result.cookies.isEmpty());
        }
    }
    @Test void qqRejectsMalformedQrWithoutLeakingResponse() throws Exception {
        MockHttp http = new MockHttp() {
            public String cookie(String name) { return "dummy-qr"; }
            public Response request(String method, String url, String referer, String body, String type) {
                return url.contains("ptqrshow") ? image() : text("upstream-secret=do-not-display");
            }
        };
        QQLogin login = new QQLogin(http); login.createQr(false);
        IOException error = assertThrows(IOException.class, login::checkQr);
        assertFalse(error.getMessage().contains("upstream-secret"));
    }
    @Test void wechatExchangesItsOwnCodeAndUsesWechatLoginType() throws Exception {
        MockHttp http = new MockHttp() {
            public Response request(String method, String url, String referer, String body, String type) {
                if (url.startsWith("https://open.weixin.qq.com/connect/qrconnect?")) return text("<img src=\"/connect/qrcode/test-wx-session\"/>");
                if (url.contains("/connect/qrcode/")) return new Response(200, new byte[]{(byte)255,(byte)216,0,0,0,0,0,0}, null);
                if (url.contains("/connect/l/")) return text("window.wx_errcode=405;window.wx_code='test-wx-code';");
                JsonObject payload=JsonParser.parseString(body).getAsJsonObject();
                assertEquals(1,payload.getAsJsonObject("comm").get("tmeLoginType").getAsInt());
                assertEquals("test-wx-code",payload.getAsJsonObject("req_0").getAsJsonObject("param").get("code").getAsString());
                return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"musicid\":456,\"musickey\":\"W_X_dummy-key\"}}}");
            }
        };
        QQLogin login=new QQLogin(http);
        assertEquals("image/jpeg",login.createQr(true).mime);
        String cookies=login.checkQr().cookies;
        assertEquals("456",QQAccount.uin(cookies));
        assertEquals(1,QQAccount.loginType(cookies));
    }
    @Test void qqPhoneRequestsKeepPhoneSeparateFromCodeAndReturnMusicCredentials() throws Exception {
        MockHttp http = new MockHttp() {
            public Response request(String method, String url, String referer, String body, String type) {
                JsonObject payload=JsonParser.parseString(body).getAsJsonObject();
                if (payload.getAsJsonObject("req_0").get("method").getAsString().equals("GetSession")) {
                    return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"session\":{\"uid\":\"dummy-session-uid\",\"sid\":\"dummy-session-sid\"}}}}");
                }
                assertEquals(3,payload.getAsJsonObject("comm").get("tmeLoginMethod").getAsInt());
                assertEquals("dummy-session-sid",payload.getAsJsonObject("comm").get("sid").getAsString());
                JsonObject req=payload.getAsJsonObject("req_0");
                assertEquals("13800138000",req.getAsJsonObject("param").get("phoneNo").getAsString());
                if(req.get("method").getAsString().equals("SendPhoneAuthCode")) {
                    assertEquals("86",req.getAsJsonObject("param").get("areaCode").getAsString());
                    return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{}}}");
                }
                assertEquals("123456",req.getAsJsonObject("param").get("code").getAsString());
                return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"musicid\":789,\"musickey\":\"dummy-phone-key\"}}}");
            }
        };
        QQLogin login=new QQLogin(http);
        assertEquals("",login.sendCode("13800138000","86"));
        assertEquals("789",QQAccount.uin(login.verify("13800138000","123456")));
        assertEquals(0,QQAccount.loginType(login.verify("13800138000","123456")));
    }
    @Test void qqSecurityVerificationIsNotReportedAsSmsSent() throws Exception {
        QQLogin login=new QQLogin(new MockHttp() {
            public Response request(String method, String url, String referer, String body, String type) {
                if (body.contains("GetSession")) return text("{\"code\":0,\"req_0\":{\"code\":0,\"data\":{\"session\":{\"uid\":\"dummy-uid\",\"sid\":\"dummy-sid\"}}}}");
                return text("{\"code\":0,\"req_0\":{\"code\":100001,\"data\":{\"securityURL\":\"https://y.qq.com/security\"}}}");
            }
        });
        assertEquals("https://y.qq.com/security",login.sendCode("13800138000","86"));
        assertThrows(IOException.class,()->QQLogin.data(JsonParser.parseString("{\"code\":0,\"req_0\":{\"code\":20271}}").getAsJsonObject()));
    }
    @Test void qqCookieAliasesSupplyActualAccountAndHashToPlaybackComm() {
        String cookie="uin=o0000123; qm_keyst=dummy-auth; tmeLoginType=2";
        assertEquals("123",QQAccount.uin(cookie));
        JsonObject comm=QQAccount.comm(cookie);
        assertEquals("dummy-auth",comm.get("authst").getAsString());
        assertEquals(QQAccount.hash("dummy-auth",5381),comm.get("g_tk").getAsInt());
        assertEquals(comm.get("g_tk"),comm.get("g_tk_new_20200303"));
        assertEquals("0",QQAccount.uin("uin=not-a-number"));
        assertThrows(IllegalStateException.class,()->QQAccount.credentialCookies(new JsonObject(),2));
    }
    @Test void kuwoPasswordNeedsCaptchaAndEncodesCookieValues() throws Exception {
        MockHttp http=new MockHttp() {
            public Response request(String method,String url,String referer,String body,String type) {
                if(url.contains("captcha/getcode")) return captcha();
                assertTrue(url.startsWith("https://wapi.kuwo.cn/api/www/login/loginByKw"));
                JsonObject params=JsonParser.parseString(body).getAsJsonObject();
                assertEquals("test-user",params.get("uname").getAsString());
                assertEquals("test-password",params.get("password").getAsString());
                assertEquals("test-captcha-token",params.get("verifyCodeToken").getAsString());
                return text("{\"code\":200,\"data\":{\"cookies\":{\"userid\":\"123\",\"sid\":\"dummy-sid\",\"username\":\"测试用户\"}}}");
            }
        };
        KuwoLogin login=new KuwoLogin(http);
        assertThrows(IOException.class,()->login.password("test-user","test-password","ABCD"));
        assertTrue(login.captcha().bytes.length>8);
        String cookies=login.password("test-user","test-password","ABCD");
        assertTrue(cookies.contains("userid=123; sid=dummy-sid"));
        assertTrue(cookies.contains("username=%E6"));
        assertFalse(cookies.contains("test-password"));
    }
    @Test void kuwoSmsRequiresSameCaptchaAndPhoneSession() throws Exception {
        MockHttp http=new MockHttp() {
            public Response request(String method,String url,String referer,String body,String type) {
                if(url.contains("captcha/getcode")) return captcha();
                JsonObject params=JsonParser.parseString(body).getAsJsonObject();
                if(url.contains("mobileLoginCode")) {
                    assertEquals("test-captcha-token",params.get("verifyCodeToken").getAsString());
                    assertEquals("ABCD",params.get("verifyCode").getAsString());
                    return text("{\"code\":200,\"data\":{\"tm\":\"dummy-sms-time\"}}");
                }
                assertTrue(url.contains("loginByMobile"));
                assertEquals("dummy-sms-time",params.get("tm").getAsString());
                assertEquals("12345",params.get("smsCode").getAsString());
                return text("{\"code\":200,\"data\":{\"cookies\":{\"userid\":\"123\",\"sid\":\"dummy-sid\"}}}");
            }
        };
        KuwoLogin login=new KuwoLogin(http);login.captcha();login.sendCode("13800138000","ABCD");
        assertThrows(IOException.class,()->login.verify("13900139000","12345"));
        assertTrue(login.verify("13800138000","12345").contains("sid=dummy-sid"));
    }
    @Test void kuwoDoesNotSaveFailedOrIncompleteCredentials() {
        IOException error=assertThrows(IOException.class,()->KuwoLogin.data(JsonParser.parseString("{\"code\":-10001,\"msg\":\"do-not-display=secret\"}").getAsJsonObject()));
        assertTrue(error.getMessage().contains("密码不正确"));
        assertFalse(error.getMessage().contains("secret"));
        assertThrows(IOException.class,()->KuwoLogin.credentials(JsonParser.parseString("{\"cookies\":{\"userid\":\"123\"}}").getAsJsonObject()));
    }
    @Test void phoneParserAcceptsCountryCodeAndNeverConfusesOtpWithPassword() throws Exception {
        NativeLogin.PhoneInput input=NativeLogin.phone(new String[]{"login","qq","verify","+852","55555555","123456"},true);
        assertEquals("852",input.country);assertEquals("55555555",input.phone);assertEquals("123456",input.code);
        assertThrows(IOException.class,()->NativeLogin.phone(new String[]{"login","qq","verify","13800138000","password"},true));
        assertThrows(IOException.class,()->NativeLogin.phone(new String[]{"login","qq","sendcode","+86"},false));
    }
    @Test void loginTransportDoesNotAllowExternalOrInsecureCredentialDestinations() {
        for(String url:new String[]{"http://www.kuwo.cn/api","https://evil.example/","https://user:password@u.y.qq.com/","https://u.y.qq.com:444/","https://evil.qq.com/"}) {
            assertFalse(LoginHttp.allowed(URI.create(url)),url);
        }
        assertTrue(LoginHttp.allowed(URI.create("https://wapi.kuwo.cn/api/www/login/loginByKw")));
        assertEquals("last-code",LoginHttp.query("https://y.qq.com/?state=x&code=last-code","code"));
    }
    @Test void cookieScopeAcceptsQqParentDomainAndNeverSendsCookiesToKuwo() {
        java.net.HttpCookie qr=new java.net.HttpCookie("qrsig","dummy");
        qr.setDomain("ptlogin2.qq.com");
        assertTrue(LoginHttp.cookieScope(URI.create("https://ssl.ptlogin2.qq.com/ptqrlogin"),qr));
        assertFalse(LoginHttp.cookieScope(URI.create("https://www.kuwo.cn/"),qr));
        qr.setDomain(".qq.com");
        assertTrue(LoginHttp.cookieScope(URI.create("https://graph.qq.com/authorize"),qr));
        qr.setDomain(".com");
        assertFalse(LoginHttp.cookieScope(URI.create("https://u.y.qq.com/"),qr));
    }
    private static class MockHttp extends LoginHttp {
        final List<String> calls=new ArrayList<>();
        static Response text(String text){return new Response(200,text.getBytes(StandardCharsets.UTF_8),null);}
        static Response image(){return new Response(200,Base64.getDecoder().decode(PNG),null);}
        static Response captcha(){return text("{\"code\":200,\"data\":{\"token\":\"test-captcha-token\",\"img\":\"data:image/png;base64,"+PNG+"\"}}");}
    }
}
