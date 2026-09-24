package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.powernukkitx.Player;
import org.powernukkitx.network.process.PlayerSessionHolder;
import org.powernukkitx.network.process.SessionState;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Represents a network session (player connection) in PowerNukkitX.
 * Allows attaching and executing middlewares individually per player session.
 */
public interface NetworkSession {

    /**
     * Resolves or wraps a {@link NetworkSession} from a {@link PlayerSessionHolder}.
     *
     * @param holder the player session holder
     * @return the resolved NetworkSession instance
     */
    @NotNull
    static NetworkSession from(@NotNull PlayerSessionHolder holder) {
        return SessionRegistry.getOrCreate(holder);
    }

    /**
     * Resolves or wraps a {@link NetworkSession} from a {@link BedrockServerSession}.
     *
     * @param session the bedrock server session
     * @return the resolved NetworkSession instance
     */
    @NotNull
    static NetworkSession from(@NotNull BedrockServerSession session) {
        return SessionRegistry.getOrCreate(session);
    }

    /**
     * Resolves or wraps a {@link NetworkSession} from a {@link Player}.
     *
     * @param player the player
     * @return the resolved NetworkSession instance
     */
    @NotNull
    static NetworkSession from(@NotNull Player player) {
        return SessionRegistry.getOrCreate(player);
    }

    /**
     * Resolves or wraps a {@link NetworkSession} from a {@link MiddlewareContext}.
     *
     * @param context the middleware context
     * @return the resolved NetworkSession instance or null
     */
    @Nullable
    static NetworkSession from(@NotNull MiddlewareContext<?> context) {
        return context.getNetworkSession();
    }

    /**
     * Returns the underlying PowerNukkitX PlayerSessionHolder.
     *
     * @return The underlying PowerNukkitX PlayerSessionHolder (if available).
     */
    @Nullable
    PlayerSessionHolder getHolder();

    /**
     * Returns the underlying BedrockServerSession.
     *
     * @return The underlying BedrockServerSession (if available).
     */
    @Nullable
    BedrockServerSession getBedrockSession();

    /**
     * Returns the Player associated with this session.
     *
     * @return The Player associated with this session (if logged in / spawned).
     */
    @Nullable
    Player getPlayer();

    /**
     * Returns the current session state.
     *
     * @return The current SessionState (INITIAL, LOGIN, RESOURCE_PACK, SPAWNED, etc.).
     */
    @Nullable
    SessionState getState();

    /**
     * Returns the remote network socket address.
     *
     * @return The remote network socket address.
     */
    @Nullable
    InetSocketAddress getAddress();

    /**
     * Indicates whether the session is currently connected.
     *
     * @return true if the session is currently connected and active.
     */
    boolean isConnected();

    /**
     * Disconnects this network session with a specified reason.
     *
     * @param reason The disconnect reason message.
     */
    void disconnect(String reason);

    /**
     * Adds a middleware specifically for this session.
     * The targeted packet is automatically inferred or read from annotations.
     *
     * @param middleware the middleware to add
     * @param <T> the packet type
     * @return this session for chaining
     */
    <T extends BedrockPacket> NetworkSession addMiddleware(IMiddleware<T> middleware);

    /**
     * Adds a middleware specifically for this session in 'ONCE' mode
     * (runs once for this session, then is ignored).
     *
     * @param middleware the middleware to register
     * @param <T> the packet type
     * @return this session for chaining
     */
    <T extends BedrockPacket> NetworkSession registerOnce(IMiddleware<T> middleware);

    /**
     * Adds a middleware specifically for this session in 'ON' mode
     * (runs on every occurrence of the targeted packet for this session).
     *
     * @param middleware the middleware to register
     * @param <T> the packet type
     * @return this session for chaining
     */
    <T extends BedrockPacket> NetworkSession registerOn(IMiddleware<T> middleware);

    /**
     * Removes a session-specific middleware.
     *
     * @param middleware the middleware to remove
     */
    void removeMiddleware(IMiddleware<?> middleware);

    /**
     * Returns all session-specific middlewares targeting the given packet class,
     * ordered by priority (LOWEST -> MONITOR).
     *
     * @param <T> the packet type
     * @param packetClass the target packet class
     * @return the list of matching middlewares
     */
    <T extends BedrockPacket> List<IMiddleware<T>> getMiddlewaresForPacket(Class<T> packetClass);

    /**
     * Checks if any session-specific middlewares are registered for the given packet class.
     *
     * @param packetClass the packet class to check
     * @return {@code true} if middlewares are registered
     */
    boolean hasMiddlewares(Class<? extends BedrockPacket> packetClass);

    /**
     * Clears all session-specific middlewares registered on this session.
     */
    void clearMiddlewares();

    /**
     * Checks if a middleware has already executed for this session.
     *
     * @param middleware the middleware to check
     * @return {@code true} if already executed for this session
     */
    boolean isExecuted(IMiddleware<?> middleware);

    /**
     * Marks a middleware as executed for this session.
     *
     * @param middleware the middleware to mark as executed
     */
    void markExecuted(IMiddleware<?> middleware);
}
