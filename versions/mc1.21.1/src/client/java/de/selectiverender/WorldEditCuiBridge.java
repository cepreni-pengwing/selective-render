package de.selectiverender;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Optional CUI APIs use their own stable, non-Minecraft names, so no mapped types leak here. */
public final class WorldEditCuiBridge {
    private WorldEditCuiBridge() { }

    public static String packetText(Object packet) throws ReflectiveOperationException {
        try {
            return (String) packet.getClass().getMethod("text").invoke(packet);
        } catch (NoSuchMethodException ignored) {
            if ((boolean) packet.getClass().getMethod("multi").invoke(packet)) return "+";
            String type = (String) packet.getClass().getMethod("eventType").invoke(packet);
            List<?> args = (List<?>) packet.getClass().getMethod("args").invoke(packet);
            return type + (args.isEmpty() ? "" : "|" + String.join("|", args.stream().map(Object::toString).toList()));
        }
    }

    public static boolean subscribe(Consumer<String> receiver) throws ReflectiveOperationException {
        Class<?> handler;
        try {
            handler = Class.forName("org.enginehub.worldeditcui.protocol.CUIPacketHandler");
        } catch (ClassNotFoundException absent) {
            return false;
        }
        Object instance = handler.getMethod("instance").invoke(null);
        handler.getMethod("registerClientboundHandler", BiConsumer.class).invoke(instance,
                (BiConsumer<Object, Object>) (packet, context) -> {
                    try {
                        receiver.accept(packetText(packet));
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        receiver.accept(null);
                    }
                });
        return true;
    }

    public static Object hello() throws ReflectiveOperationException {
        try {
            Class<?> packet = Class.forName("org.enginehub.worldeditcui.protocol.CUIPacket");
            return packet.getConstructor(String.class, String[].class)
                    .newInstance("v", new String[]{"3"});
        } catch (ClassNotFoundException absent) {
            try {
                return Class.forName("com.sk89q.worldedit.fabric.net.handler.WECUIPacketHandler$CuiPacket")
                        .getConstructor(String.class).newInstance("v|3");
            } catch (ClassNotFoundException alsoAbsent) {
                return null;
            }
        }
    }

    public static void observe(Object event) {
        try {
            Class<?> type = event.getClass();
            if ((boolean) type.getMethod("isMulti").invoke(event)) return;
            String name = (String) type.getMethod("getType").invoke(event);
            Object params = type.getMethod("getParams").invoke(event);
            List<?> args = params instanceof String[] array ? Arrays.asList(array) : (List<?>) params;
            WorldEditClient.receive(name + (args.isEmpty() ? "" : "|" +
                    String.join("|", args.stream().map(Object::toString).toList())));
        } catch (ReflectiveOperationException | RuntimeException error) {
            WorldEditClient.receive(null);
        }
    }
}
