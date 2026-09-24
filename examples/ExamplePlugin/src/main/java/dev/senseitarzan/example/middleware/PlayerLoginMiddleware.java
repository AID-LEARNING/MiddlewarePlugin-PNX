package dev.senseitarzan.example.middleware;

import dev.senseitarzan.middleware.api.AttributeMiddlewarePriority;
import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.MiddlewarePriority;
import dev.senseitarzan.middleware.api.Once;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;

import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates pre-login asynchronous verification on {@link LoginPacket}.
 * Runs once per player connection session.
 *
 * Notice: getPacketClass() is NOT implemented manually! It is automatically inferred from {@code IMiddleware<LoginPacket>}.
 */
@Once
@AttributeMiddlewarePriority(MiddlewarePriority.LOWEST)
public class PlayerLoginMiddleware implements IMiddleware<LoginPacket> {

    /**
     * Constructs a new PlayerLoginMiddleware instance.
     */
    public PlayerLoginMiddleware() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "PlayerLoginMiddleware";
    }

    /**
     * Asynchronously validates the connection on LoginPacket using virtual threads.
     * Registers a session-scoped middleware for subsequent packets upon approval.
     *
     * @param context the middleware context containing the LoginPacket
     * @return a CompletableFuture tracking completion
     */
    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
        return CompletableFuture.runAsync(() -> {
            var address = context.getAddress();
            System.out.println("[PlayerLoginMiddleware] Checking connection from: " + address);

            boolean isBanned = false;
            if (isBanned) {
                context.disconnect("You are banned from this server!");
                throw new IllegalStateException("Player is banned");
            }

            context.registerSessionOnce(new IMiddleware<CommandRequestPacket>() {
                @Override
                public String getName() {
                    return "FirstPlayerCommandLogger";
                }

                @Override
                public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> cmdContext) {
                    System.out.println("[SessionMiddleware] Player sent their first command: "
                            + cmdContext.getPacket().getCommand());
                    return CompletableFuture.completedFuture(null);
                }
            });

        }, MiddlewareManager.getInstance().getVirtualExecutor());
    }
}
