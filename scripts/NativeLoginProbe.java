import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.zhenxin.zmusic.login.*;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.libs.gson.*;

/** 真实接口只验证二维码、等待状态、图形验证码及错误凭据；不发送有效手机号短信。 */
public class NativeLoginProbe {
    public static void main(String[] args) throws Exception {
        JsonObject report=new JsonObject();
        report.addProperty("version","2.14.0-CE.5");
        for(boolean wechat:new boolean[]{false,true}) {
            JsonObject row=new JsonObject();
            try {
                QQLogin login=new QQLogin();
                QQLogin.Image image=login.createQr(wechat);
                row.addProperty("imageMime",image.mime);row.addProperty("imageBytes",image.bytes.length);
                row.addProperty("state",login.checkQr().state);
                row.addProperty("passed",image.bytes.length>100);
            }catch(Exception error){row.addProperty("passed",false);row.addProperty("error",error.getMessage());}
            report.add(wechat?"qqWechatQr":"qqQr",row);
        }
        JsonObject kuwo=new JsonObject();
        try {
            KuwoLogin login=new KuwoLogin();
            QQLogin.Image image=login.captcha();
            kuwo.addProperty("imageMime",image.mime);kuwo.addProperty("imageBytes",image.bytes.length);
            kuwo.addProperty("captchaReceived",image.bytes.length>100);
            try {login.password("ZMusic-protocol-probe-no-account","invalid-password","wrong");kuwo.addProperty("invalidLoginRejected",false);}
            catch(java.io.IOException error){kuwo.addProperty("invalidLoginRejected",true);kuwo.addProperty("invalidLoginMessage",error.getMessage());}
        }catch(Exception error){kuwo.addProperty("error",error.getMessage());}
        report.add("kuwo",kuwo);
        JsonObject sms=new JsonObject();
        try {
            String security=new QQLogin().sendCode("not-a-phone","86");
            sms.addProperty("invalidPhoneUnexpectedlyAccepted",security.isEmpty());
            sms.addProperty("securityVerificationRequired",!security.isEmpty());
        }catch(Exception error){sms.addProperty("invalidPhoneMessage",error.getMessage());}
        report.add("qqPhoneInvalidInput",sms);
        JsonObject expired=new JsonObject();
        try {
            JsonObject req=new JsonObject();req.addProperty("module","music.UserInfo.userInfoServer");req.addProperty("method","GetLoginUserInfo");req.add("param",new JsonObject());
            JsonObject payload=new JsonObject();payload.add("comm",QQAccount.comm("qqmusic_uin=123; qqmusic_key=dummy-invalid-key"));payload.add("req_0",req);
            JsonObject response=new LoginHttp().request("POST","https://u.y.qq.com/cgi-bin/musicu.fcg","https://y.qq.com/",payload.toString(),"application/json").json();
            try {QQLogin.data(response);expired.addProperty("rejected",false);}
            catch(java.io.IOException error){expired.addProperty("rejected",true);expired.addProperty("message",error.getMessage());}
        }catch(Exception error){expired.addProperty("error",error.getMessage());}
        report.add("invalidQqCredential",expired);
        Files.write(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
