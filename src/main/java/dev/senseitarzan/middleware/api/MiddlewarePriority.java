package dev.senseitarzan.middleware.api;

/**
 * Defines execution priorities for middlewares, executed in ascending order:
 * {@link #LOWEST} &rarr; {@link #LOW} &rarr; {@link #NORMAL} &rarr; {@link #HIGH} &rarr; {@link #HIGHEST} &rarr; {@link #MONITOR}.
 */
public enum MiddlewarePriority {
    /**
     * Executed first before all other middlewares.
     */
    LOWEST(0),

    /**
     * Executed with low priority, after LOWEST.
     */
    LOW(1),

    /**
     * Default priority for middlewares.
     */
    NORMAL(2),

    /**
     * Executed with high priority, after NORMAL.
     */
    HIGH(3),

    /**
     * Executed with highest priority before MONITOR.
     */
    HIGHEST(4),

    /**
     * Executed last, ideal for monitoring and auditing.
     */
    MONITOR(5);

    private final int slot;

    /**
     * Constructs a middleware priority level.
     *
     * @param slot the internal ordinal index slot
     */
    MiddlewarePriority(int slot) {
        this.slot = slot;
    }

    /**
     * Returns the integer slot associated with this priority level.
     *
     * @return the ordinal slot index
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Cached array of all priority values in ordinal order.
     */
    public static final MiddlewarePriority[] ALL = values();
}
