package dev.senseitarzan.middleware;

import dev.senseitarzan.middleware.hook.PacketHandlerHook;
import dev.senseitarzan.middleware.manager.MiddlewareManager;
import org.powernukkitx.plugin.PluginBase;

/**
 * Main plugin class for MiddlewarePlugin on PowerNukkitX.
 * Initializes the packet hooks and manages the lifecycle of registered middlewares.
 */
public class MiddlewarePlugin extends PluginBase {

    private static MiddlewarePlugin instance;

    /**
     * Constructs a new MiddlewarePlugin instance.
     */
    public MiddlewarePlugin() {
    }

    /**
     * Called when the plugin is loaded by the server.
     */
    @Override
    public void onLoad() {
        instance = this;
    }

    /**
     * Called when the plugin is enabled.
     * Initializes packet handler reflection hooks and prepares the middleware manager.
     */
    @Override
    public void onEnable() {
        instance = this;
        PacketHandlerHook.init();
        MiddlewareManager.getInstance().init();
        getLogger().info("MiddlewarePlugin enabled successfully!");
    }

    /**
     * Called when the plugin is disabled.
     * Restores all original packet handlers and clears registered middlewares.
     */
    @Override
    public void onDisable() {
        PacketHandlerHook.unhookAll();
        MiddlewareManager.getInstance().clear();
        getLogger().info("MiddlewarePlugin disabled.");
    }

    /**
     * Returns the active singleton instance of MiddlewarePlugin.
     *
     * @return The active singleton instance of {@link MiddlewarePlugin}.
     */
    public static MiddlewarePlugin getInstance() {
        return instance;
    }
}
