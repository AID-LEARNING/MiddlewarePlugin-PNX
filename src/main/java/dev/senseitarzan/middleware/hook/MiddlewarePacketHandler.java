package dev.senseitarzan.middleware.hook;

import dev.senseitarzan.middleware.MiddlewarePlugin;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.NetworkSession;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.powernukkitx.Server;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.PlayerSessionHolder;

import java.util.concurrent.CompletionException;

/**
 * Decorates a PowerNukkitX {@link PacketHandler} to execute registered middlewares before
 * delegating to the server's default handler.
 *
 * @param <T> The Bedrock packet type
 */
public class MiddlewarePacketHandler<T extends BedrockPacket> implements PacketHandler<T> {

    private final Class<T> packetClass;
    private final PacketHandler<T> originalHandler;

    /**
     * Constructs a new MiddlewarePacketHandler wrapper.
     *
     * @param packetClass the target packet class intercepted by this handler
     * @param originalHandler the original PowerNukkitX packet handler being decorated
     */
    public MiddlewarePacketHandler(Class<T> packetClass, PacketHandler<T> originalHandler) {
        this.packetClass = packetClass;
        this.originalHandler = originalHandler;
    }

    /**
     * Returns the underlying original PowerNukkitX packet handler.
     *
     * @return the original packet handler instance
     */
    public PacketHandler<T> getOriginalHandler() {
        return originalHandler;
    }

    /**
     * Returns the Bedrock packet class intercepted by this handler.
     *
     * @return the packet class
     */
    public Class<T> getPacketClass() {
        return packetClass;
    }

    /**
     * Indicates whether this handler should run on the Netty network thread.
     * Delegates to the original handler's preference.
     *
     * @return {@code true} if the original handler runs on the network thread
     */
    @Override
    public boolean runsOnNetworkThread() {
        return originalHandler != null && originalHandler.runsOnNetworkThread();
    }

    /**
     * Intercepts the packet, executes the middleware pipeline asynchronously,
     * and delegates to the original handler upon successful completion.
     *
     * @param packet the received Bedrock packet
     * @param holder the player session holder
     * @param server the PowerNukkitX server instance
     */
    @Override
    public void handle(T packet, PlayerSessionHolder holder, Server server) {
        MiddlewareManager manager = MiddlewareManager.getInstance();
        NetworkSession session = holder != null
                ? NetworkSession.from(holder)
                : null;

        boolean hasGlobal = manager.hasMiddlewares(packetClass);
        boolean hasSession = session != null && session.hasMiddlewares(packetClass);

        if (!hasGlobal && !hasSession) {
            if (originalHandler != null) {
                originalHandler.handle(packet, holder, server);
            }
            return;
        }

        MiddlewareContext<T> context = new MiddlewareContext<>(packet, holder, server, originalHandler);

        manager.executePipeline(context)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        Throwable root = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                        String message = root != null && root.getMessage() != null && !root.getMessage().isBlank()
                                ? root.getMessage()
                                : "Error in middleware execution";

                        MiddlewarePlugin plugin = MiddlewarePlugin.getInstance();
                        if (plugin != null) {
                            plugin.getLogger().error(
                                    "Middleware execution failed for " + holder.getSession().getSocketAddress() + ": " + message,
                                    root
                            );
                        }
                        context.disconnect(message);
                        return;
                    }

                    if (holder.isDisconnected() || (holder.getSession() != null
                            && holder.getSession().getPeer() != null
                            && !holder.getSession().getPeer().getChannel().isActive())) {
                        return;
                    }

                    if (originalHandler != null) {
                        if (holder.getSession() != null
                                && holder.getSession().getPeer() != null
                                && holder.getSession().getPeer().getChannel() != null) {
                            holder.getSession().getPeer().getChannel().eventLoop().execute(() -> {
                                try {
                                    originalHandler.handle(packet, holder, server);
                                } catch (Throwable ex) {
                                    MiddlewarePlugin plugin = MiddlewarePlugin.getInstance();
                                    if (plugin != null) {
                                        plugin.getLogger().error(
                                                "Error delegating packet " + packetClass.getSimpleName() + " to original handler", ex);
                                    }
                                    context.disconnect("Error handling " + packetClass.getSimpleName());
                                }
                            });
                        } else {
                            try {
                                originalHandler.handle(packet, holder, server);
                            } catch (Throwable ex) {
                                MiddlewarePlugin plugin = MiddlewarePlugin.getInstance();
                                if (plugin != null) {
                                    plugin.getLogger().error(
                                            "Error delegating packet " + packetClass.getSimpleName() + " to original handler", ex);
                                }
                                context.disconnect("Error handling " + packetClass.getSimpleName());
                            }
                        }
                    }
                });
    }
}
