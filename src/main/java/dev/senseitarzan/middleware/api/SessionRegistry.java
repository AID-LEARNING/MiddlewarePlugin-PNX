package dev.senseitarzan.middleware.api;

import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.powernukkitx.Player;
import org.powernukkitx.network.process.NetworkPacketHandler;
import org.powernukkitx.network.process.PlayerSessionHolder;

import java.lang.reflect.Field;

/**
 * Registry mapping PlayerSessionHolder, BedrockServerSession and Player to {@link NetworkSession} instances.
 */
public final class SessionRegistry {

    private static final WeakIdentityHashMap<PlayerSessionHolder, NetworkSession> HOLDER_MAP = new WeakIdentityHashMap<>();
    private static final WeakIdentityHashMap<BedrockServerSession, NetworkSession> BEDROCK_MAP = new WeakIdentityHashMap<>();

    private static volatile Field sessionField = null;
    private static volatile boolean sessionFieldLookupFailed = false;

    private SessionRegistry() {}

    /**
     * Resolves an existing {@link NetworkSession} or creates a new one for the given {@link PlayerSessionHolder}.
     *
     * @param holder the PowerNukkitX player session holder
     * @return the associated NetworkSession instance
     */
    @NotNull
    public static synchronized NetworkSession getOrCreate(@NotNull PlayerSessionHolder holder) {
        NetworkSession existing = HOLDER_MAP.get(holder);
        if (existing != null) {
            return existing;
        }
        BedrockServerSession bedrockSession = holder.getSession();
        if (bedrockSession != null) {
            existing = BEDROCK_MAP.get(bedrockSession);
            if (existing != null) {
                HOLDER_MAP.put(holder, existing);
                return existing;
            }
        }
        NetworkSession session = new NetworkSessionImpl(holder, bedrockSession);
        HOLDER_MAP.put(holder, session);
        if (bedrockSession != null) {
            BEDROCK_MAP.put(bedrockSession, session);
        }
        return session;
    }

    /**
     * Resolves an existing {@link NetworkSession} or creates a new one for the given {@link BedrockServerSession}.
     *
     * @param bedrockSession the Bedrock server session
     * @return the associated NetworkSession instance
     */
    @NotNull
    public static synchronized NetworkSession getOrCreate(@NotNull BedrockServerSession bedrockSession) {
        NetworkSession existing = BEDROCK_MAP.get(bedrockSession);
        if (existing != null) {
            return existing;
        }
        PlayerSessionHolder holder = extractHolder(bedrockSession);
        if (holder != null) {
            return getOrCreate(holder);
        }
        NetworkSession session = new NetworkSessionImpl(null, bedrockSession);
        BEDROCK_MAP.put(bedrockSession, session);
        return session;
    }

    /**
     * Resolves an existing {@link NetworkSession} or creates a new one for the given {@link Player}.
     *
     * @param player the PowerNukkitX player
     * @return the associated NetworkSession instance
     * @throws IllegalArgumentException if the player does not have an active network session
     */
    @NotNull
    public static NetworkSession getOrCreate(@NotNull Player player) {
        BedrockServerSession bedrockSession = player.getSession();
        if (bedrockSession != null) {
            return getOrCreate(bedrockSession);
        }
        throw new IllegalArgumentException("Player does not have an active BedrockServerSession!");
    }

    /**
     * Attempts to extract the {@link PlayerSessionHolder} from a {@link BedrockServerSession} using reflection.
     *
     * @param bedrockSession the Bedrock server session
     * @return the PlayerSessionHolder or null if unavailable
     */
    @Nullable
    private static PlayerSessionHolder extractHolder(BedrockServerSession bedrockSession) {
        if (bedrockSession == null) return null;
        Object packetHandler = bedrockSession.getPacketHandler();
        if (packetHandler instanceof NetworkPacketHandler) {
            try {
                if (sessionField == null && !sessionFieldLookupFailed) {
                    Field f = NetworkPacketHandler.class.getDeclaredField("session");
                    f.setAccessible(true);
                    sessionField = f;
                }
                if (sessionField != null) {
                    return (PlayerSessionHolder) sessionField.get(packetHandler);
                }
            } catch (Throwable _) {
                sessionFieldLookupFailed = true;
            }
        }
        return null;
    }

    /**
     * Clears all cached session mappings.
     */
    public static synchronized void clear() {
        HOLDER_MAP.clear();
        BEDROCK_MAP.clear();
    }
}
