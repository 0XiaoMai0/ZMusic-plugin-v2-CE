package me.zhenxin.zmusic.nms;
import com.google.gson.Gson;
import org.bukkit.entity.Player;
/** 隔离 1.8 ActionBar 适配，编译时不引用内部类，兼容不同 1.8 修订版本。 */
public final class ActionBar_1_8_R3 implements ActionBar {
    @Override
    public void sendActionBar(Object player, String message) {
        try {
            Player target = (Player) player;
            Object handle = target.getClass().getMethod("getHandle").invoke(target);
            String prefix = handle.getClass().getPackage().getName() + ".";
            Class<?> component = Class.forName(prefix + "IChatBaseComponent");
            Class<?> serializer = Class.forName(prefix + "IChatBaseComponent$ChatSerializer");
            Object text = serializer.getMethod("a", String.class)
                    .invoke(null, "{\"text\":" + new Gson().toJson(message) + "}");
            Object packet = Class.forName(prefix + "PacketPlayOutChat")
                    .getConstructor(component, byte.class).newInstance(text, (byte) 2);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            connection.getClass().getMethod("sendPacket", Class.forName(prefix + "Packet"))
                    .invoke(connection, packet);
        } catch (ReflectiveOperationException ignored) {
            ((Player) player).sendMessage(message);
        }
    }
}
