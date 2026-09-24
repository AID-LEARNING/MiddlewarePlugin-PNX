package dev.senseitarzan.middleware.api;

import dev.senseitarzan.middleware.hook.PacketHandlerHook;
import dev.senseitarzan.middleware.manager.DelegatingMiddleware;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.jetbrains.annotations.Nullable;
import org.powernukkitx.Player;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PlayerSessionHolder;
import org.powernukkitx.network.process.SessionState;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link NetworkSession}.
 */
public class NetworkSessionImpl implements NetworkSession {

    private final WeakReference<PlayerSessionHolder> holderRef;
    private final WeakReference<BedrockServerSession> bedrockRef;

    private final Map<Class<? extends BedrockPacket>, Map<MiddlewarePriority, List<IMiddleware<?>>>> middlewares = new ConcurrentHashMap<>();
    private final Map<Class<? extends BedrockPacket>, List<IMiddleware<?>>> sortedCache = new ConcurrentHashMap<>();
    private final Set<IMiddleware<?>> executedMiddlewares = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a new NetworkSessionImpl wrapping the given holder and Bedrock session.
     *
     * @param holder the PlayerSessionHolder or null
     * @param bedrockSession the BedrockServerSession or null
     */
    public NetworkSessionImpl(@Nullable PlayerSessionHolder holder, @Nullable BedrockServerSession bedrockSession) {
        this.holderRef = new WeakReference<>(holder);
        this.bedrockRef = new WeakReference<>(bedrockSession != null ? bedrockSession : (holder != null ? holder.getSession() : null));
    }

    @Override
    @Nullable
    public PlayerSessionHolder getHolder() {
        return holderRef.get();
    }

    @Override
    @Nullable
    public BedrockServerSession getBedrockSession() {
        BedrockServerSession session = bedrockRef.get();
        if (session == null) {
            PlayerSessionHolder holder = getHolder();
            if (holder != null) {
                return holder.getSession();
            }
        }
        return session;
    }

    @Override
    @Nullable
    public Player getPlayer() {
        PlayerSessionHolder holder = getHolder();
        return holder != null ? holder.getPlayer() : null;
    }

    @Override
    @Nullable
    public SessionState getState() {
        PlayerSessionHolder holder = getHolder();
        return holder != null ? holder.getState() : null;
    }

    @Override
    @Nullable
    public InetSocketAddress getAddress() {
        BedrockServerSession session = getBedrockSession();
        if (session != null) {
            SocketAddress addr = session.getSocketAddress();
            if (addr instanceof InetSocketAddress inet) {
                return inet;
            }
        }
        return null;
    }

    @Override
    public boolean isConnected() {
        PlayerSessionHolder holder = getHolder();
        if (holder != null && holder.isDisconnected()) {
            return false;
        }
        BedrockServerSession session = getBedrockSession();
        if (session != null && !session.isConnected()) {
            return false;
        }
        return true;
    }

    @Override
    public void disconnect(String reason) {
        PlayerSessionHolder holder = getHolder();
        if (holder != null) {
            holder.setDisconnected(true);
        }
        BedrockServerSession session = getBedrockSession();
        if (session != null && session.isConnected()) {
            session.disconnect(reason);
        }
    }

    @Override
    public synchronized <T extends BedrockPacket> NetworkSession addMiddleware(IMiddleware<T> middleware) {
        Collection<Class<? extends BedrockPacket>> targetPackets = middleware.getTargetPackets();
        Class<? extends PacketHandler> targetHandler = middleware.getTargetHandlerClass();

        if (targetPackets.isEmpty() && targetHandler != null) {
            targetPackets = PacketHandlerHook.getPacketClassesForHandler(targetHandler);
        }

        if (targetPackets.isEmpty()) {
            throw new IllegalArgumentException("Middleware '" + middleware.getName()
                    + "' must specify at least one target packet class or a valid target NetworkHandler!");
        }

        MiddlewarePriority priority = middleware.getPriority();
        for (Class<? extends BedrockPacket> packetClass : targetPackets) {
            middlewares.computeIfAbsent(packetClass, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(priority, k -> new CopyOnWriteArrayList<>())
                    .add(middleware);
            PacketHandlerHook.hook(packetClass);
        }

        sortedCache.clear();
        return this;
    }

    @Override
    public <T extends BedrockPacket> NetworkSession registerOnce(IMiddleware<T> middleware) {
        return addMiddleware(new DelegatingMiddleware<>(middleware, MiddlewareExecutionMode.ONCE));
    }

    @Override
    public <T extends BedrockPacket> NetworkSession registerOn(IMiddleware<T> middleware) {
        return addMiddleware(new DelegatingMiddleware<>(middleware, MiddlewareExecutionMode.ON));
    }

    @Override
    public synchronized void removeMiddleware(IMiddleware<?> middleware) {
        Collection<Class<? extends BedrockPacket>> targetPackets = middleware.getTargetPackets();
        if (targetPackets.isEmpty() && middleware.getTargetHandlerClass() != null) {
            targetPackets = PacketHandlerHook.getPacketClassesForHandler(middleware.getTargetHandlerClass());
        }

        for (Class<? extends BedrockPacket> packetClass : targetPackets) {
            Map<MiddlewarePriority, List<IMiddleware<?>>> priorityMap = middlewares.get(packetClass);
            if (priorityMap != null) {
                List<IMiddleware<?>> list = priorityMap.get(middleware.getPriority());
                if (list != null) {
                    list.remove(middleware);
                }
            }
        }
        sortedCache.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BedrockPacket> List<IMiddleware<T>> getMiddlewaresForPacket(Class<T> packetClass) {
        List<IMiddleware<?>> cached = sortedCache.computeIfAbsent(packetClass, this::computeSortedList);
        return (List<IMiddleware<T>>) (List<?>) cached;
    }

    @Override
    public boolean hasMiddlewares(Class<? extends BedrockPacket> packetClass) {
        Map<MiddlewarePriority, List<IMiddleware<?>>> map = middlewares.get(packetClass);
        return map != null && map.values().stream().anyMatch(l -> !l.isEmpty());
    }

    @Override
    public synchronized void clearMiddlewares() {
        middlewares.clear();
        sortedCache.clear();
        executedMiddlewares.clear();
    }

    @Override
    public boolean isExecuted(IMiddleware<?> middleware) {
        return executedMiddlewares.contains(middleware)
                || (middleware instanceof DelegatingMiddleware<?> del && executedMiddlewares.contains(del.getDelegate()));
    }

    @Override
    public void markExecuted(IMiddleware<?> middleware) {
        executedMiddlewares.add(middleware);
        if (middleware instanceof DelegatingMiddleware<?> del) {
            executedMiddlewares.add(del.getDelegate());
        }
    }

    /**
     * Flattens and sorts the registered middlewares for a packet class across all priorities.
     *
     * @param packetClass the target packet class
     * @return unmodifiable list of middlewares sorted by priority
     */
    private List<IMiddleware<?>> computeSortedList(Class<? extends BedrockPacket> packetClass) {
        Map<MiddlewarePriority, List<IMiddleware<?>>> priorityMap = middlewares.get(packetClass);
        if (priorityMap == null || priorityMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<IMiddleware<?>> result = new ArrayList<>();
        for (MiddlewarePriority priority : MiddlewarePriority.ALL) {
            List<IMiddleware<?>> list = priorityMap.get(priority);
            if (list != null) {
                result.addAll(list);
            }
        }
        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }
}
