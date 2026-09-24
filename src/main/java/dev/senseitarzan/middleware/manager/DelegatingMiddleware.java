package dev.senseitarzan.middleware.manager;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.MiddlewareExecutionMode;
import dev.senseitarzan.middleware.api.MiddlewarePriority;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.powernukkitx.network.process.PacketHandler;
import org.powernukkitx.network.process.SessionState;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Wrapper middleware used for programmatically applying execution mode overrides (e.g. registerOnce, registerOn).
 *
 * @param <T> The Bedrock packet type
 */
public class DelegatingMiddleware<T extends BedrockPacket> implements IMiddleware<T> {

    private final IMiddleware<T> delegate;
    private final MiddlewareExecutionMode executionMode;

    /**
     * Constructs a new DelegatingMiddleware wrapping the specified delegate with an overridden execution mode.
     *
     * @param delegate the underlying middleware to execute
     * @param executionMode the overridden execution mode (ONCE, ON, etc.)
     */
    public DelegatingMiddleware(IMiddleware<T> delegate, MiddlewareExecutionMode executionMode) {
        this.delegate = delegate;
        this.executionMode = executionMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<T> getPacketClass() {
        return delegate.getPacketClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends BedrockPacket>> getTargetPackets() {
        return delegate.getTargetPackets();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends PacketHandler> getTargetHandlerClass() {
        return delegate.getTargetHandlerClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SessionState getTargetSessionState() {
        return delegate.getTargetSessionState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiddlewareExecutionMode getExecutionMode() {
        return executionMode != null ? executionMode : delegate.getExecutionMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MiddlewarePriority getPriority() {
        return delegate.getPriority();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(MiddlewareContext<?> context) {
        return delegate.matches(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<T> context) {
        return delegate.handle(context);
    }

    /**
     * Returns the underlying original delegate middleware instance.
     *
     * @return The underlying original delegate middleware instance.
     */
    public IMiddleware<T> getDelegate() {
        return delegate;
    }
}
