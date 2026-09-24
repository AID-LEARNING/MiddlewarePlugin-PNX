package dev.senseitarzan.example;

import dev.senseitarzan.example.middleware.CommandFilterMiddleware;
import dev.senseitarzan.example.middleware.PlayerInitMiddleware;
import dev.senseitarzan.example.middleware.PlayerLoginMiddleware;
import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.NetworkSession;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerJoinEvent;
import org.powernukkitx.plugin.PluginBase;

import java.util.concurrent.CompletableFuture;

/**
 * Example PowerNukkitX plugin demonstrating how to use MiddlewarePlugin.
 */
public class ExamplePlugin extends PluginBase implements Listener {

    /**
     * Constructs a new ExamplePlugin instance.
     */
    public ExamplePlugin() {
    }

    /**
     * Called when the plugin is enabled.
     * Registers global middlewares and registers event listeners.
     */
    @Override
    public void onEnable() {
        MiddlewareManager manager = MiddlewareManager.getInstance();

        manager.addMiddleware(new PlayerLoginMiddleware());
        manager.addMiddleware(new PlayerInitMiddleware());
        manager.addMiddleware(new CommandFilterMiddleware());

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("ExamplePlugin successfully registered global and session middlewares!");
    }

    /**
     * Listens for player join events to register individual session-scoped middlewares.
     *
     * @param event the player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        NetworkSession session = NetworkSession.from(event.getPlayer());

        session.registerOnce(new IMiddleware<InteractPacket>() {
            @Override
            public String getName() {
                return "FirstClickTutorial";
            }

            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<InteractPacket> context) {
                if (context.hasPlayer()) {
                    context.getPlayer().sendMessage("§a[Tutorial] You interacted with the world for the first time!");
                }
                return CompletableFuture.completedFuture(null);
            }
        });
    }
}
