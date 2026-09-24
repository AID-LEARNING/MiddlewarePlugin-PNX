package dev.senseitarzan.example.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.Once;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.powernukkitx.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates async data loading when the player is initialized in the world.
 * Intercepts {@link SetLocalPlayerAsInitializedPacket}.
 */
@Once
public class PlayerInitMiddleware implements IMiddleware<SetLocalPlayerAsInitializedPacket> {

    /**
     * Constructs a new PlayerInitMiddleware instance.
     */
    public PlayerInitMiddleware() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "PlayerInitMiddleware";
    }

    /**
     * Asynchronously loads player data on SetLocalPlayerAsInitializedPacket before player fully spawns.
     *
     * @param context the middleware context containing the initialization packet
     * @return a CompletableFuture tracking completion
     */
    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<SetLocalPlayerAsInitializedPacket> context) {
        return CompletableFuture.runAsync(() -> {
            Player player = context.getPlayer();
            if (player != null) {
                System.out.println("[PlayerInitMiddleware] Asynchronously loading user profile for: " + player.getName());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
                System.out.println("[PlayerInitMiddleware] Profile loaded for: " + player.getName());
            }
        }, MiddlewareManager.getInstance().getVirtualExecutor());
    }
}
