package dev.senseitarzan.middleware.api;

/**
 * Defines the execution mode of a middleware:
 * - {@link #ON}: Executes every time the targeted packet is received (standard recurring mode).
 * - {@link #ONCE}: Executes only once per player session/connection.
 */
public enum MiddlewareExecutionMode {
    /**
     * Executes every time the packet is received for this session.
     */
    ON,

    /**
     * Executes only ONCE per player session/connection.
     * Subsequent occurrences of the packet in the same session will skip this middleware.
     */
    ONCE
}
