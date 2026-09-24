package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.SessionState;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface representing a middleware that intercepts Bedrock packets asynchronously.
 * The targeted packet type is automatically inferred from the generic type parameter {@code <T>}.
 *
 * @param <T> The Bedrock packet type
 */
public interface IMiddleware<T extends BedrockPacket> {

    /**
     * Returns the unique human-readable name of this middleware.
     *
     * @return The unique human-readable name of this middleware.
     */
    String getName();

    /**
     * The Bedrock packet class to intercept.
     * By default, this is automatically inferred from the generic parameter {@code <T extends BedrockPacket>}!
     * Can still be explicitly overridden if desired.
     *
     * @return the packet class to intercept
     */
    default Class<T> getPacketClass() {
        return GenericTypeResolver.resolvePacketClass(getClass());
    }

    /**
     * Alias for {@link #getPacketClass()} matching PocketMine-MP's onDetectPacket().
     *
     * @return the packet class to intercept
     */
    default Class<T> onDetectPacket() {
        return getPacketClass();
    }

    /**
     * Target Bedrock packet classes to intercept.
     * Allows attaching a single middleware to one or multiple specific packets.
     *
     * @return collection of target packet classes
     */
    default Collection<Class<? extends BedrockPacket>> getTargetPackets() {
        TargetPackets targetPackets = getClass().getAnnotation(TargetPackets.class);
        if (targetPackets != null && targetPackets.value().length > 0) {
            return List.of(targetPackets.value());
        }
        TargetPacket targetPacket = getClass().getAnnotation(TargetPacket.class);
        if (targetPacket != null) {
            return List.of(targetPacket.value());
        }
        Class<T> single = getPacketClass();
        if (single != null && !BedrockPacket.class.equals(single)) {
            return List.of(single);
        }
        return Collections.emptyList();
    }

    /**
     * The target PacketHandler / NetworkHandler class (e.g. {@code LoginHandler.class},
     * {@code TextHandler.class}, {@code CommandRequestHandler.class}).
     * Return {@code null} to accept any handler.
     *
     * @return the target packet handler class, or null
     */
    default Class<? extends PacketHandler> getTargetHandlerClass() {
        TargetNetworkHandler targetNetwork = getClass().getAnnotation(TargetNetworkHandler.class);
        if (targetNetwork != null) {
            return targetNetwork.value();
        }
        TargetHandler target = getClass().getAnnotation(TargetHandler.class);
        if (target != null) {
            return target.value();
        }
        return null;
    }

    /**
     * Alias for {@link #getTargetHandlerClass()}.
     *
     * @return the target packet handler class, or null
     */
    default Class<? extends PacketHandler> getNetworkHandlerClass() {
        return getTargetHandlerClass();
    }

    /**
     * Optional filter by SessionState (e.g. {@code SessionState.LOGIN},
     * {@code SessionState.RESOURCE_PACK}).
     * Return {@code null} to accept any session state.
     *
     * @return the target session state, or null
     */
    default SessionState getTargetSessionState() {
        TargetSessionState target = getClass().getAnnotation(TargetSessionState.class);
        return target != null ? target.value() : null;
    }

    /**
     * Resolves the execution mode of this middleware:
     * - {@link MiddlewareExecutionMode#ON}: runs every time the packet is received (default).
     * - {@link MiddlewareExecutionMode#ONCE}: runs only once per player session/connection.
     *
     * @return the execution mode
     */
    default MiddlewareExecutionMode getExecutionMode() {
        if (getClass().isAnnotationPresent(Once.class)) {
            return MiddlewareExecutionMode.ONCE;
        }
        if (getClass().isAnnotationPresent(On.class)) {
            return MiddlewareExecutionMode.ON;
        }
        Execution execution = getClass().getAnnotation(Execution.class);
        if (execution != null) {
            return execution.value();
        }
        if (SetLocalPlayerAsInitializedPacket.class.equals(getPacketClass())) {
            return MiddlewareExecutionMode.ONCE;
        }
        return MiddlewareExecutionMode.ON;
    }

    /**
     * Checks if this middleware executes only once per session.
     *
     * @return true if this middleware executes only once per session.
     */
    default boolean isOnce() {
        return getExecutionMode() == MiddlewareExecutionMode.ONCE;
    }

    /**
     * Evaluates whether this middleware should execute for the given context.
     * Checks target packet types, target handler, and session state.
     *
     * @param context the middleware execution context
     * @return {@code true} if all filters match
     */
    default boolean matches(MiddlewareContext<?> context) {
        Collection<Class<? extends BedrockPacket>> packets = getTargetPackets();
        if (!packets.isEmpty()) {
            boolean matchesPacket = false;
            for (Class<? extends BedrockPacket> p : packets) {
                if (p.isInstance(context.getPacket())) {
                    matchesPacket = true;
                    break;
                }
            }
            if (!matchesPacket) {
                return false;
            }
        }

        Class<? extends PacketHandler> targetHandler = getTargetHandlerClass();
        if (targetHandler != null) {
            PacketHandler<?> handler = context.getHandler();
            if (handler == null || !targetHandler.isInstance(handler)) {
                return false;
            }
        }

        SessionState targetState = getTargetSessionState();
        if (targetState != null) {
            if (context.getSessionState() != targetState) {
                return false;
            }
        }

        return true;
    }

    /**
     * Executes this middleware asynchronously.
     * Return a completed future (e.g. {@code CompletableFuture.completedFuture(null)}) when finished.
     * To abort the connection, complete exceptionally or call {@link MiddlewareContext#disconnect(String)}.
     *
     * @param context the middleware execution context
     * @return a CompletableFuture tracking completion
     */
    CompletableFuture<Void> handle(MiddlewareContext<T> context);

    /**
     * Alias for {@link #handle(MiddlewareContext)} matching PocketMine-MP's getPromise().
     *
     * @param context the middleware execution context
     * @return a CompletableFuture tracking completion
     */
    default CompletableFuture<Void> getPromise(MiddlewareContext<T> context) {
        return handle(context);
    }

    /**
     * Resolves the priority of this middleware.
     *
     * @return the resolved priority
     */
    default MiddlewarePriority getPriority() {
        AttributeMiddlewarePriority attr = getClass().getAnnotation(AttributeMiddlewarePriority.class);
        if (attr != null) {
            return attr.value();
        }
        Priority priority = getClass().getAnnotation(Priority.class);
        if (priority != null) {
            return priority.value();
        }
        return MiddlewarePriority.NORMAL;
    }
}
