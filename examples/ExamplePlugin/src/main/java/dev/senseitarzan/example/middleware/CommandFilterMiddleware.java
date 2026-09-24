package dev.senseitarzan.example.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.On;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates recurring packet interception in 'ON' mode.
 * Intercepts {@link CommandRequestPacket} each time a player executes a command.
 */
@On
public class CommandFilterMiddleware implements IMiddleware<CommandRequestPacket> {

    /**
     * Constructs a new CommandFilterMiddleware instance.
     */
    public CommandFilterMiddleware() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "CommandFilterMiddleware";
    }

    /**
     * Filters command execution by inspecting the command string.
     * Blocks forbidden commands and kicks offending clients.
     *
     * @param context the middleware context containing the CommandRequestPacket
     * @return a CompletableFuture tracking completion
     */
    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
        String command = context.getPacket().getCommand();
        if (command.startsWith("/forbidden")) {
            System.out.println("[CommandFilterMiddleware] Blocked forbidden command from " + context.getAddress());
            context.disconnect("You cannot use this command!");
            throw new IllegalStateException("Command forbidden");
        }
        return CompletableFuture.completedFuture(null);
    }
}
