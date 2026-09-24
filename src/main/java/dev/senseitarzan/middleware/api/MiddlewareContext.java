package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.DisconnectFailReason;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.jetbrains.annotations.Nullable;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PlayerSessionHolder;
import org.powernukkitx.network.process.SessionState;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;

/**
 * Context provided to an {@link IMiddleware} during packet interception.
 *
 * @param <T> The Bedrock packet type
 */
public class MiddlewareContext<T extends BedrockPacket> {

    /**
     * Java 25 ScopedValue allowing immutable and safe context propagation
     * across virtual threads without ThreadLocal overhead or memory leaks.
     */
    public static final ScopedValue<MiddlewareContext<?>> CURRENT = ScopedValue.newInstance();

    /**
     * Resolves the currently active context from ScopedValue.
     *
     * @return The currently active {@link MiddlewareContext} in this execution scope, if bound.
     */
    public static Optional<MiddlewareContext<?>> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    private final T packet;
    private final PlayerSessionHolder sessionHolder;
    private final BedrockServerSession session;
    private final Server server;
    private final PacketHandler<?> handler;
    private final NetworkSession networkSession;

    /**
     * Constructs a new MiddlewareContext without a specific packet handler.
     *
     * @param packet the Bedrock packet being intercepted
     * @param sessionHolder the PowerNukkitX player session holder
     * @param server the PowerNukkitX Server instance
     */
    public MiddlewareContext(T packet, PlayerSessionHolder sessionHolder, Server server) {
        this(packet, sessionHolder, server, null);
    }

    /**
     * Constructs a new MiddlewareContext with an optional packet handler.
     *
     * @param packet the Bedrock packet being intercepted
     * @param sessionHolder the PowerNukkitX player session holder
     * @param server the PowerNukkitX Server instance
     * @param handler the original packet handler or null
     */
    public MiddlewareContext(T packet, PlayerSessionHolder sessionHolder, Server server, @Nullable PacketHandler<?> handler) {
        this.packet = packet;
        this.sessionHolder = sessionHolder;
        this.session = sessionHolder != null ? sessionHolder.getSession() : null;
        this.server = server;
        this.handler = handler;
        if (sessionHolder != null) {
            this.networkSession = NetworkSession.from(sessionHolder);
        } else if (this.session != null) {
            this.networkSession = NetworkSession.from(this.session);
        } else {
            this.networkSession = null;
        }
    }

    /**
     * Returns the Bedrock packet being intercepted.
     *
     * @return The Bedrock packet being intercepted.
     */
    public T getPacket() {
        return packet;
    }

    /**
     * Returns the PowerNukkitX PlayerSessionHolder.
     *
     * @return The PowerNukkitX PlayerSessionHolder.
     */
    public PlayerSessionHolder getSessionHolder() {
        return sessionHolder;
    }

    /**
     * Returns the underlying BedrockServerSession.
     *
     * @return The underlying BedrockServerSession, or null if unavailable.
     */
    @Nullable
    public BedrockServerSession getSession() {
        return session;
    }

    /**
     * Returns the PowerNukkitX Server instance.
     *
     * @return The Server instance.
     */
    public Server getServer() {
        return server;
    }

    /**
     * Returns the PacketHandler / NetworkHandler responsible for this packet.
     *
     * @return The PacketHandler / NetworkHandler responsible for this packet (e.g. LoginHandler, TextHandler...)
     */
    @Nullable
    public PacketHandler<?> getHandler() {
        return handler;
    }

    /**
     * Alias for {@link #getHandler()} representing the NetworkHandler in PowerNukkitX.
     *
     * @return the network handler instance or null
     */
    @Nullable
    public PacketHandler<?> getNetworkHandler() {
        return handler;
    }

    /**
     * Returns the BedrockPacketHandler set on the session.
     *
     * @return The BedrockPacketHandler set on the session (typically NetworkPacketHandler).
     */
    @Nullable
    public BedrockPacketHandler getNetworkPacketHandler() {
        return session != null ? session.getPacketHandler() : null;
    }

    /**
     * Returns the current SessionState of the player session.
     *
     * @return The current SessionState of the player session (e.g. INITIAL, LOGIN, RESOURCE_PACK, CHUNKS...)
     */
    @Nullable
    public SessionState getSessionState() {
        return sessionHolder != null ? sessionHolder.getState() : null;
    }

    /**
     * Returns the player associated with this session, if created.
     * Note: For LoginPacket, the player does not exist yet (returns null).
     * For SetLocalPlayerAsInitializedPacket, the player exists.
     *
     * @return Player or null
     */
    @Nullable
    public Player getPlayer() {
        return sessionHolder != null ? sessionHolder.getPlayer() : null;
    }

    /**
     * Checks if the player instance is currently non-null.
     *
     * @return true if the player instance is currently non-null.
     */
    public boolean hasPlayer() {
        return getPlayer() != null;
    }

    /**
     * Returns the remote socket address of the client.
     *
     * @return The remote socket address of the client.
     */
    @Nullable
    public InetSocketAddress getAddress() {
        if (session != null) {
            SocketAddress addr = session.getSocketAddress();
            if (addr instanceof InetSocketAddress inet) {
                return inet;
            }
        }
        return null;
    }

    /**
     * Disconnects or kicks the client immediately with a reason.
     *
     * @param reason kick or disconnect reason message
     */
    public void disconnect(String reason) {
        Player player = getPlayer();
        if (player != null) {
            player.kick(reason);
        } else if (sessionHolder != null) {
            sessionHolder.disconnect(DisconnectFailReason.KICKED, reason);
        } else if (session != null) {
            session.close(reason);
        }
    }

    /**
     * Returns the NetworkSession representing this client's network session.
     *
     * @return The {@link NetworkSession} representing this client's network session, or null if unavailable.
     */
    @Nullable
    public NetworkSession getNetworkSession() {
        return networkSession;
    }

    /**
     * Adds a middleware specifically to this client's network session.
     *
     * @param middleware The middleware to add to this session.
     * @param <U> The packet type.
     */
    public <U extends BedrockPacket> void addSessionMiddleware(IMiddleware<U> middleware) {
        if (networkSession != null) {
            networkSession.addMiddleware(middleware);
        }
    }

    /**
     * Registers a middleware in ONCE mode specifically to this client's network session.
     *
     * @param middleware The middleware to register.
     * @param <U> The packet type.
     */
    public <U extends BedrockPacket> void registerSessionOnce(IMiddleware<U> middleware) {
        if (networkSession != null) {
            networkSession.registerOnce(middleware);
        }
    }

    /**
     * Registers a middleware in ON mode specifically to this client's network session.
     *
     * @param middleware The middleware to register.
     * @param <U> The packet type.
     */
    public <U extends BedrockPacket> void registerSessionOn(IMiddleware<U> middleware) {
        if (networkSession != null) {
            networkSession.registerOn(middleware);
        }
    }
}
