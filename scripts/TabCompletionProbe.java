import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.sun.tools.attach.VirtualMachine;

/** 在实际测试服主线程调用已注册的指令；模拟发送者不会进入世界，也不修改真实玩家权限。 */
public final class TabCompletionProbe {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
    }

    public static void agentmain(String output, Instrumentation instrumentation) throws Exception {
        for (Class<?> bukkit : instrumentation.getAllLoadedClasses()) {
            if (!bukkit.getName().equals("org.bukkit.Bukkit")) continue;
            ClassLoader loader = bukkit.getClassLoader();
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = loader.loadClass("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class).invoke(manager, "ZMusic");
            Object scheduler = bukkit.getMethod("getScheduler").invoke(null);
            loader.loadClass("org.bukkit.scheduler.BukkitScheduler").getMethod("runTask", loader.loadClass("org.bukkit.plugin.Plugin"), Runnable.class)
                    .invoke(scheduler, plugin, (Runnable) () -> inspect(bukkit, plugin, output));
            return;
        }
        throw new IllegalStateException("Bukkit test server not found");
    }

    private static void inspect(Class<?> bukkit, Object plugin, String output) {
        List<String> rows = new ArrayList<>();
        String error = null;
        String version = null;
        try {
            ClassLoader loader = bukkit.getClassLoader();
            Object description = loader.loadClass("org.bukkit.plugin.Plugin").getMethod("getDescription").invoke(plugin);
            version = loader.loadClass("org.bukkit.plugin.PluginDescriptionFile").getMethod("getVersion").invoke(description).toString();
            if (!version.equals("2.14.0-CE.5")) throw new AssertionError("Wrong plugin version: " + version);
            Class<?> playerType = loader.loadClass("org.bukkit.entity.Player");
            Class<?> senderType = loader.loadClass("org.bukkit.command.CommandSender");
            var complete = loader.loadClass("org.bukkit.command.Command").getMethod("tabComplete", senderType, String.class, String[].class);
            Object console = bukkit.getMethod("getConsoleSender").invoke(null);
            Object user = fakePlayer(playerType, false), admin = fakePlayer(playerType, true);
            for (String alias : List.of("zm", "zmusic", "music")) {
                Object command = bukkit.getMethod("getPluginCommand", String.class).invoke(null, alias);
                if (command == null) throw new AssertionError("Missing alias " + alias);
                for (String root : List.of("play", "music", "search")) {
                    check(rows, complete.invoke(command, user, alias, new String[]{root, ""}), alias + "/" + root,
                            List.of("163", "netease", "qq", "kugou", "kuwo", "bilibili"), List.of());
                }
                check(rows, complete.invoke(command, user, alias, new String[]{""}), alias + "/user-roots",
                        List.of("play", "music", "search", "playlist", "help"), List.of("login", "diagnose", "playAll", "web"));
                check(rows, complete.invoke(command, admin, alias, new String[]{""}), alias + "/admin-roots",
                        List.of("login", "diagnose", "notice", "playAll", "stopAll"), List.of("web"));
                check(rows, complete.invoke(command, console, alias, new String[]{""}), alias + "/console-roots",
                        List.of("login", "diagnose", "help", "playAll"), List.of("play", "music", "search", "playlist", "notice"));
                check(rows, complete.invoke(command, admin, alias, new String[]{"login", "qq", "qr", ""}), alias + "/qq-qr-types", List.of("qq", "wechat"), List.of("password"));
                for (String provider : List.of("qq", "kugou", "kuwo", "bilibili", "netease", "163")) {
                    check(rows, complete.invoke(command, admin, alias, new String[]{"login", provider, ""}), alias + "/login/" + provider,
                            provider.equals("qq") ? List.of("qr", "sendcode", "verify", "phone", "raw", "status", "cancel") : provider.equals("kuwo") ? List.of("captcha", "password", "sendcode", "verify", "phone", "raw", "status", "cancel") : List.of("raw", "status"), provider.equals("qq") ? List.of("password") : provider.equals("kuwo") ? List.of("qr") : List.of("qr", "phone"));
                }
                check(rows, complete.invoke(command, user, alias, new String[]{"playlist", "global", "163", ""}), alias + "/global-user",
                        List.of("play", "list", "show"), List.of("import", "update", "playall"));
                check(rows, complete.invoke(command, admin, alias, new String[]{"playlist", "global", "163", ""}), alias + "/global-admin",
                        List.of("play", "list", "show", "import", "update", "playall"), List.of());
                check(rows, complete.invoke(command, user, alias, new String[]{"playlist", "TYPE", "L"}), alias + "/type-prefix",
                        List.of("loop"), List.of("normal", "random"));
                check(rows, complete.invoke(command, admin, alias, new String[]{"login", "kugou", "raw", ""}), alias + "/no-cookie-suggestions",
                        List.of(), List.of("raw", "status"));
                check(rows, complete.invoke(command, user, alias, new String[]{"stop", ""}), alias + "/no-extra-arguments", List.of(), List.of("stop"));
            }
        } catch (Throwable failure) { error = failure.toString(); }
        String json = "{\n  \"server\": \"26.2\",\n  \"version\": " + quote(version)
                + ",\n  \"method\": \"registered Bukkit command on server thread, simulated player permissions\",\n  \"checks\": " + rows.size()
                + ",\n  \"passed\": " + (error == null) + ",\n  \"error\": " + quote(error) + ",\n  \"results\": [\n    " + String.join(",\n    ", rows) + "\n  ]\n}\n";
        try { Files.writeString(Path.of(output), json, StandardCharsets.UTF_8); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static Object fakePlayer(Class<?> type, boolean admin) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName": return "ZMusicTabProbe";
                case "getUniqueId": return UUID.fromString("665e5e25-631d-4187-a52b-d01b52b5d745");
                case "hasPermission": return admin || args[0].equals("zmusic.use");
                case "isOnline": return true;
                case "isOp": return admin;
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "ZMusicTabProbe";
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static void check(List<String> rows, Object actual, String label, List<String> expected, List<String> excluded) {
        List<?> values = (List<?>) actual;
        if (!values.containsAll(expected)) throw new AssertionError(label + " missing " + expected + ": " + values);
        for (String item : excluded) if (values.contains(item)) throw new AssertionError(label + " unexpected " + item);
        if (expected.isEmpty() && !values.isEmpty()) throw new AssertionError(label + " should be empty: " + values);
        List<String> escaped = new ArrayList<>();
        for (Object value : values) escaped.add(quote(value.toString()));
        rows.add("{\"case\":" + quote(label) + ",\"suggestions\":[" + String.join(",", escaped) + "]}");
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
