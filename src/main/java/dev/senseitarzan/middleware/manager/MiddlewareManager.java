package dev.senseitarzan.middleware.manager;

import dev.senseitarzan.middleware.MiddlewarePlugin;
import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.MiddlewareExecutionMode;
import dev.senseitarzan.middleware.api.MiddlewarePriority;
import dev.senseitarzan.middleware.api.NetworkSession;
import dev.senseitarzan.middleware.api.SessionRegistry;
import dev.senseitarzan.middleware.api.WeakIdentityHashMap;
import dev.senseitarzan.middleware.hook.PacketHandlerHook;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.powernukkitx.Player;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PlayerSessionHolder;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Central manager for registering, organizing, and executing middlewares on targeted packets.
 */
public class MiddlewareManager {

    private static final MiddlewareManager INSTANCE = new MiddlewareManager();

    /**
     * Map of PacketClass -> (Priority -> List of IMiddleware)
     */
    private final Map<Class<? extends BedrockPacket>, Map<MiddlewarePriority, List<IMiddleware<?>>>> middlewares = new ConcurrentHashMap<>();

    /**
     * Cache of PacketClass -> Flattened List of IMiddleware sorted by priority (LOWEST -> MONITOR)
     */
    private final Map<Class<? extends BedrockPacket>, List<IMiddleware<?>>> sortedCache = new ConcurrentHashMap<>();

    /**
     * Tracks middlewares executed for a specific session in 'ONCE' mode.
     * Uses WeakIdentityHashMap to ensure mutating session fields (e.g. packetCounter) don't cause lookup failures.
     */
    private final WeakIdentityHashMap<PlayerSessionHolder, Set<IMiddleware<?>>> sessionExecutedMiddlewares =
            new WeakIdentityHashMap<>();

    /**
     * Dedicated named virtual thread executor for asynchronous middleware tasks
     */
    private final ThreadFactory virtualThreadFactory = Thread.ofVirtual()
            .name("middleware-worker-", 1)
            .factory();

    private final ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);

    private MiddlewareManager() {
    }

    /**
     * Returns the singleton instance of {@link MiddlewareManager}.
     *
     * @return the singleton instance
     */
    public static MiddlewareManager getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a dedicated ExecutorService backed by Java Virtual Threads.
     * Ideal for non-blocking I/O operations (database queries, HTTP requests).
     *
     * @return the virtual thread executor service
     */
    public ExecutorService getVirtualExecutor() {
        return virtualExecutor;
    }

    /**
     * Registers a middleware for its targeted packet(s) or NetworkHandler.
     * Only the specific targeted packet handlers are hooked into PowerNukkitX.
     *
     * @param middleware the middleware instance
     * @param <T> the packet type
     */
    @SuppressWarnings("unchecked")
    public synchronized <T extends BedrockPacket> void addMiddleware(IMiddleware<T> middleware) {
        MiddlewarePriority priority = middleware.getPriority();
        MiddlewareExecutionMode mode = middleware.getExecutionMode();

        Collection<Class<? extends BedrockPacket>> targetPackets = middleware.getTargetPackets();
        Class<? extends PacketHandler> targetHandler = middleware.getTargetHandlerClass();

        if (targetPackets.isEmpty() && targetHandler != null) {
            targetPackets = PacketHandlerHook.getPacketClassesForHandler(targetHandler);
        }

        if (targetPackets.isEmpty()) {
            throw new IllegalArgumentException("Middleware '" + middleware.getName()
                    + "' must specify at least one target packet class or a valid target NetworkHandler!");
        }

        MiddlewarePlugin plugin = MiddlewarePlugin.getInstance();
        if (plugin != null) {
            String targetDesc = targetPackets.stream().map(Class::getSimpleName).collect(Collectors.joining(", "));
            if (targetHandler != null) {
                targetDesc += " [NetworkHandler: " + targetHandler.getSimpleName() + "]";
            }
            plugin.getLogger().info("Adding Middleware: " + middleware.getName()
                    + " (" + middleware.getClass().getName() + ") for ["
                    + targetDesc + "] [Mode: " + mode + "] with priority " + priority);
        }

        for (Class<? extends BedrockPacket> packetClass : targetPackets) {
            middlewares.computeIfAbsent(packetClass, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(priority, k -> new CopyOnWriteArrayList<>())
                    .add(middleware);
            PacketHandlerHook.hook(packetClass);
        }

        invalidateAllCache();
    }

    /**
     * Helper to register a middleware in 'ONCE' mode (runs once per player session).
     *
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOnce(IMiddleware<T> middleware) {
        addMiddleware(new DelegatingMiddleware<>(middleware, MiddlewareExecutionMode.ONCE));
    }

    /**
     * Helper to register a middleware in 'ON' mode (runs every time the packet is received).
     *
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOn(IMiddleware<T> middleware) {
        addMiddleware(new DelegatingMiddleware<>(middleware, MiddlewareExecutionMode.ON));
    }

    /**
     * Removes a registered middleware.
     *
     * @param middleware the middleware to remove
     */
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
        invalidateAllCache();
    }

    /**
     * Re-hooks all targeted packet types currently registered.
     */
    public synchronized void init() {
        for (Class<? extends BedrockPacket> packetClass : middlewares.keySet()) {
            PacketHandlerHook.hook(packetClass);
        }
    }

    /**
     * Checks if any middlewares are registered for the given packet class.
     *
     * @param packetClass the packet class to check
     * @return {@code true} if middlewares are registered for this packet
     */
    public boolean hasMiddlewares(Class<? extends BedrockPacket> packetClass) {
        Map<MiddlewarePriority, List<IMiddleware<?>>> priorityMap = middlewares.get(packetClass);
        return priorityMap != null && priorityMap.values().stream().anyMatch(l -> !l.isEmpty());
    }

    /**
     * Retrieves the sorted list of middlewares for a specific packet class.
     *
     * @param packetClass the target packet class
     * @param <T> the packet type
     * @return list of middlewares sorted by priority
     */
    @SuppressWarnings("unchecked")
    public <T extends BedrockPacket> List<IMiddleware<T>> getMiddlewaresForPacket(Class<T> packetClass) {
        List<IMiddleware<?>> cached = sortedCache.computeIfAbsent(packetClass, this::computeSortedList);
        return (List<IMiddleware<T>>) (List<?>) cached;
    }

    private List<IMiddleware<?>> computeSortedList(Class<? extends BedrockPacket> packetClass) {
        Map<MiddlewarePriority, List<IMiddleware<?>>> packetPriorityMap = middlewares.get(packetClass);
        if (packetPriorityMap == null || packetPriorityMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<IMiddleware<?>> result = new ArrayList<>();
        for (MiddlewarePriority priority : MiddlewarePriority.ALL) {
            List<IMiddleware<?>> list = packetPriorityMap.get(priority);
            if (list != null) {
                result.addAll(list);
            }
        }

        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    /**
     * Clears the flattened priority cache for all packets.
     */
    private void invalidateAllCache() {
        sortedCache.clear();
    }

    /**
     * Sequentially executes all matching middlewares for this context.
     * Evaluates global and session-specific middlewares merged in priority order.
     * Respects packet matching, NetworkHandler, SessionState, and handles 'ONCE' vs 'ON' modes.
     *
     * @param <T> the packet type
     * @param context the middleware context
     * @return a CompletableFuture tracking pipeline execution
     */
    @SuppressWarnings("unchecked")
    public <T extends BedrockPacket> CompletableFuture<Void> executePipeline(MiddlewareContext<T> context) {
        Class<T> packetClass = (Class<T>) context.getPacket().getClass();
        List<IMiddleware<T>> globalPipeline = getMiddlewaresForPacket(packetClass);
        NetworkSession session = context.getNetworkSession();
        List<IMiddleware<T>> sessionPipeline = session != null ? session.getMiddlewaresForPacket(packetClass) : Collections.emptyList();

        if (globalPipeline.isEmpty() && sessionPipeline.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<IMiddleware<T>> pipeline = mergePipelines(globalPipeline, sessionPipeline);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (IMiddleware<T> middleware : pipeline) {
            if (!middleware.matches(context)) {
                continue;
            }

            boolean skip = switch (middleware.getExecutionMode()) {
                case ONCE -> (session != null && session.isExecuted(middleware))
                        || (context.getSessionHolder() != null && isExecutedForSession(context.getSessionHolder(), middleware));
                case ON -> false;
                case null, default -> false;
            };
            if (skip) {
                continue;
            }

            chain = chain.thenCompose(_ -> {
                if (context.getSessionHolder() != null && context.getSessionHolder().isDisconnected()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Session was disconnected"));
                }
                try {
                    CompletableFuture<Void> promise = ScopedValue.where(MiddlewareContext.CURRENT, context)
                            .call(() -> middleware.handle(context));
                    CompletableFuture<Void> safePromise = promise != null ? promise : CompletableFuture.completedFuture(null);
                    return safePromise.thenRun(() -> {
                        if (middleware.getExecutionMode() == MiddlewareExecutionMode.ONCE) {
                            if (session != null) {
                                session.markExecuted(middleware);
                            }
                            if (context.getSessionHolder() != null) {
                                markExecutedForSession(context.getSessionHolder(), middleware);
                            }
                        }
                    });
                } catch (Throwable t) {
                    return CompletableFuture.failedFuture(t);
                }
            });
        }
        return chain;
    }

    /**
     * Merges two priority-sorted middleware lists in $O(N + M)$ preserving priority ordering.
     */
    private <T extends BedrockPacket> List<IMiddleware<T>> mergePipelines(
            List<IMiddleware<T>> global,
            List<IMiddleware<T>> session) {
        if (session.isEmpty()) return global;
        if (global.isEmpty()) return session;

        List<IMiddleware<T>> merged = new ArrayList<>(global.size() + session.size());
        int i = 0, j = 0;
        while (i < global.size() && j < session.size()) {
            IMiddleware<T> gm = global.get(i);
            IMiddleware<T> sm = session.get(j);
            if (gm.getPriority().ordinal() <= sm.getPriority().ordinal()) {
                merged.add(gm);
                i++;
            } else {
                merged.add(sm);
                j++;
            }
        }
        while (i < global.size()) merged.add(global.get(i++));
        while (j < session.size()) merged.add(session.get(j++));
        return Collections.unmodifiableList(merged);
    }

    /**
     * Attaches a middleware specifically to the given {@link NetworkSession}.
     *
     * @param session the network session
     * @param middleware the middleware to attach
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void addMiddleware(NetworkSession session, IMiddleware<T> middleware) {
        session.addMiddleware(middleware);
    }

    /**
     * Attaches a middleware specifically to the session of the given {@link PlayerSessionHolder}.
     *
     * @param holder the player session holder
     * @param middleware the middleware to attach
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void addMiddleware(PlayerSessionHolder holder, IMiddleware<T> middleware) {
        NetworkSession.from(holder).addMiddleware(middleware);
    }

    /**
     * Attaches a middleware specifically to the given {@link BedrockServerSession}.
     *
     * @param session the Bedrock server session
     * @param middleware the middleware to attach
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void addMiddleware(BedrockServerSession session, IMiddleware<T> middleware) {
        NetworkSession.from(session).addMiddleware(middleware);
    }

    /**
     * Attaches a middleware specifically to the session of the given {@link Player}.
     *
     * @param player the player
     * @param middleware the middleware to attach
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void addMiddleware(Player player, IMiddleware<T> middleware) {
        NetworkSession.from(player).addMiddleware(middleware);
    }

    /**
     * Registers a middleware in ONCE mode for the specified {@link NetworkSession}.
     *
     * @param session the network session
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOnce(NetworkSession session, IMiddleware<T> middleware) {
        session.registerOnce(middleware);
    }

    /**
     * Registers a middleware in ONCE mode for the specified {@link PlayerSessionHolder}.
     *
     * @param holder the player session holder
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOnce(PlayerSessionHolder holder, IMiddleware<T> middleware) {
        NetworkSession.from(holder).registerOnce(middleware);
    }

    /**
     * Registers a middleware in ONCE mode for the specified {@link BedrockServerSession}.
     *
     * @param session the Bedrock server session
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOnce(BedrockServerSession session, IMiddleware<T> middleware) {
        NetworkSession.from(session).registerOnce(middleware);
    }

    /**
     * Registers a middleware in ONCE mode for the specified {@link Player}.
     *
     * @param player the player
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOnce(Player player, IMiddleware<T> middleware) {
        NetworkSession.from(player).registerOnce(middleware);
    }

    /**
     * Registers a middleware in ON mode for the specified {@link NetworkSession}.
     *
     * @param session the network session
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOn(NetworkSession session, IMiddleware<T> middleware) {
        session.registerOn(middleware);
    }

    /**
     * Registers a middleware in ON mode for the specified {@link PlayerSessionHolder}.
     *
     * @param holder the player session holder
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOn(PlayerSessionHolder holder, IMiddleware<T> middleware) {
        NetworkSession.from(holder).registerOn(middleware);
    }

    /**
     * Registers a middleware in ON mode for the specified {@link BedrockServerSession}.
     *
     * @param session the Bedrock server session
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOn(BedrockServerSession session, IMiddleware<T> middleware) {
        NetworkSession.from(session).registerOn(middleware);
    }

    /**
     * Registers a middleware in ON mode for the specified {@link Player}.
     *
     * @param player the player
     * @param middleware the middleware to register
     * @param <T> the packet type
     */
    public <T extends BedrockPacket> void registerOn(Player player, IMiddleware<T> middleware) {
        NetworkSession.from(player).registerOn(middleware);
    }

    /**
     * Removes a session-specific middleware from the specified {@link NetworkSession}.
     *
     * @param session the network session
     * @param middleware the middleware to remove
     */
    public void removeMiddleware(NetworkSession session, IMiddleware<?> middleware) {
        session.removeMiddleware(middleware);
    }

    /**
     * Resolves the {@link NetworkSession} for the given {@link PlayerSessionHolder}.
     *
     * @param holder the player session holder
     * @return the associated NetworkSession
     */
    public NetworkSession getSession(PlayerSessionHolder holder) {
        return NetworkSession.from(holder);
    }

    /**
     * Resolves the {@link NetworkSession} for the given {@link BedrockServerSession}.
     *
     * @param session the Bedrock server session
     * @return the associated NetworkSession
     */
    public NetworkSession getSession(BedrockServerSession session) {
        return NetworkSession.from(session);
    }

    /**
     * Resolves the {@link NetworkSession} for the given {@link Player}.
     *
     * @param player the player
     * @return the associated NetworkSession
     */
    public NetworkSession getSession(Player player) {
        return NetworkSession.from(player);
    }

    /**
     * Checks if a middleware has already executed for the given session holder in ONCE mode.
     *
     * @param holder the player session holder
     * @param middleware the middleware to check
     * @return {@code true} if already executed for this session
     */
    public boolean isExecutedForSession(PlayerSessionHolder holder, IMiddleware<?> middleware) {
        if (holder == null) return false;
        Set<IMiddleware<?>> set = sessionExecutedMiddlewares.get(holder);
        return set != null && (set.contains(middleware) || (middleware instanceof DelegatingMiddleware<?> del && set.contains(del.getDelegate())));
    }

    /**
     * Marks a middleware as executed for the given session holder in ONCE mode.
     *
     * @param holder the player session holder
     * @param middleware the middleware to mark
     */
    public void markExecutedForSession(PlayerSessionHolder holder, IMiddleware<?> middleware) {
        if (holder == null) return;
        Set<IMiddleware<?>> set = sessionExecutedMiddlewares.computeIfAbsent(holder, k -> ConcurrentHashMap.newKeySet());
        set.add(middleware);
        if (middleware instanceof DelegatingMiddleware<?> del) {
            set.add(del.getDelegate());
        }
    }

    /**
     * Clears all registered middlewares, execution tracking records, and session caches.
     */
    public synchronized void clear() {
        middlewares.clear();
        sortedCache.clear();
        sessionExecutedMiddlewares.clear();
        SessionRegistry.clear();
    }
}
