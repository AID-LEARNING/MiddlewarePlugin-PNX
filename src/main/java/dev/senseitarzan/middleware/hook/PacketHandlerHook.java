package dev.senseitarzan.middleware.hook;

import dev.senseitarzan.middleware.MiddlewarePlugin;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PacketHandlerRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages reflection hooks into PowerNukkitX's PacketHandlerRegistry for targeted packets only.
 */
public class PacketHandlerHook {

    private static Map<Class<? extends BedrockPacket>, PacketHandler> internalMap;
    private static final Map<Class<? extends BedrockPacket>, PacketHandler> originalHandlers = new ConcurrentHashMap<>();

    /**
     * Utility class; private constructor prevents instantiation.
     */
    private PacketHandlerHook() {
    }

    /**
     * Initializes the reflection handle to PowerNukkitX's internal {@code PacketHandlerRegistry.MAP}.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void init() {
        if (internalMap != null) return;
        try {
            Field mapField = PacketHandlerRegistry.class.getDeclaredField("MAP");
            mapField.setAccessible(true);
            internalMap = (Map<Class<? extends BedrockPacket>, PacketHandler>) mapField.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access PacketHandlerRegistry.MAP", e);
        }
    }

    /**
     * Finds all packet classes registered in PacketHandlerRegistry that are handled by the given handler class.
     *
     * @param handlerClass the target packet handler class
     * @return list of matching Bedrock packet classes
     */
    public static List<Class<? extends BedrockPacket>> getPacketClassesForHandler(Class<? extends PacketHandler> handlerClass) {
        init();
        List<Class<? extends BedrockPacket>> result = new ArrayList<>();
        for (Map.Entry<Class<? extends BedrockPacket>, PacketHandler> entry : internalMap.entrySet()) {
            PacketHandler handler = entry.getValue();
            if (handler instanceof MiddlewarePacketHandler<?> wrapper) {
                handler = wrapper.getOriginalHandler();
            }
            if (handler != null && handlerClass.isInstance(handler)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Hooks specifically the given packet class into PowerNukkitX.
     *
     * @param packetClass the target packet class
     * @param <T> the packet type
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T extends BedrockPacket> void hook(Class<T> packetClass) {
        init();
        PacketHandler current = internalMap.get(packetClass);
        if (current == null) {
            return;
        }
        if (current instanceof MiddlewarePacketHandler) {
            return;
        }

        originalHandlers.put(packetClass, current);
        MiddlewarePacketHandler<T> wrapper = new MiddlewarePacketHandler<>(packetClass, (PacketHandler<T>) current);
        internalMap.put(packetClass, wrapper);

        MiddlewarePlugin plugin = MiddlewarePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().debug("Hooked packet handler for: " + packetClass.getSimpleName());
        }
    }

    /**
     * Unhooks all previously hooked packet handlers, restoring their original instances.
     */
    public static synchronized void unhookAll() {
        if (internalMap == null) return;
        for (Map.Entry<Class<? extends BedrockPacket>, PacketHandler> entry : originalHandlers.entrySet()) {
            if (entry.getValue() != null) {
                internalMap.put(entry.getKey(), entry.getValue());
            } else {
                internalMap.remove(entry.getKey());
            }
        }
        originalHandlers.clear();
    }
}
