# ⚡ MiddlewarePlugin (Java 25 LTS for PowerNukkitX)

Official port and modernization in **Java 25 LTS** of [MiddlewarePlugin](https://github.com/AID-LEARNING/MiddlewarePlugin) for **PowerNukkitX (PNX)**.

This plugin introduces a high-performance **Asynchronous Middleware Framework** that allows intercepting and executing non-blocking tasks (database lookups, Redis caching, REST API calls, permissions, authentication) on any targeted Bedrock packet before the server processes it.

---

## 🚀 Key Highlights & Java 25 Modernization

- **Built for Java 25 LTS & PowerNukkitX**:
  - Compiled targeting bytecode **Java 25 (class major version 69)**.
  - **Scoped Values (`ScopedValue`)**: Immutable and leak-free context propagation (`MiddlewareContext.current()`) across asynchronous virtual threads without ThreadLocal overhead.
  - **Named Virtual Threads**: Managed virtual thread pool (`middleware-worker-#`) via `Thread.ofVirtual()` for optimal observability and debugging.
  - **Modern Switch Expressions & Pattern Matching** for fast, clean evaluation of execution modes.
  - **Unnamed Variables (`_`)** following modern Java standards.
- **Surgical Packet Interception (Zero Global Hook Overhead)**:
  - Choose precisely which Bedrock packet classes to intercept (e.g. `LoginPacket`, `CommandRequestPacket`, `InteractPacket`, `SetLocalPlayerAsInitializedPacket`, etc.).
  - **Automatic Generic Type Inference**: Automatically infers the target packet type from `IMiddleware<T>`—no need to manually write `getPacketClass()`.
  - **Only targeted packet handlers are hooked** in `PacketHandlerRegistry.MAP`; non-intercepted packets run at 100% native server speed with zero overhead.
- **Individual Middlewares per `NetworkSession`**:
  - Attach middlewares dynamically to a single player's network connection, with automatic garbage collection cleanup via `WeakIdentityHashMap`.
- **Filtering by NetworkHandler**:
  - Target a specific PowerNukkitX `PacketHandler` (e.g. `@TargetNetworkHandler(LoginHandler.class)`). Only packets handled by that handler are hooked.
- **Execution Modes: `ONCE` vs `ON`**:
  - **`ON`** (default): Middleware runs **on every occurrence** of the packet for that player.
  - **`ONCE`**: Middleware runs **only once** per player session/connection. Once resolved, subsequent occurrences of that packet in the same session will bypass it.
- **Asynchronous & Non-Blocking**:
  - Powered by `CompletableFuture` and Java **Virtual Threads** so that neither the Netty network thread nor the main server tick loop is ever blocked.

---

## 🛠️ Execution Modes: `ONCE` vs `ON`

### 1. `ONCE` Mode (Single execution per player session)

Ideal for one-time initialization, first-interaction verification, or asynchronous profile loading:

```java
package my.plugin.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.Once;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;

import java.util.concurrent.CompletableFuture;

@Once // <-- Runs only once per player session!
public class FirstInteractMiddleware implements IMiddleware<InteractPacket> {

    @Override
    public String getName() {
        return "FirstInteract";
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<InteractPacket> context) {
        if (context.hasPlayer()) {
            context.getPlayer().sendMessage("Welcome! You interacted with the world for the first time.");
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

---

### 2. `ON` Mode (Recurring execution on every packet)

Ideal for command firewalls, chat syntax validation, or action verification:

```java
package my.plugin.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.On;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

import java.util.concurrent.CompletableFuture;

@On // <-- Runs on EVERY command packet received
public class CommandFilterMiddleware implements IMiddleware<CommandRequestPacket> {

    @Override
    public String getName() {
        return "CommandFilter";
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
        String command = context.getPacket().getCommand();
        if (command.startsWith("/forbidden")) {
            context.disconnect("You are not allowed to use this command!");
            throw new IllegalStateException("Forbidden command");
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

---

## 📦 Registering Middlewares

### Standard Registration
```java
// Uses the mode defined on the class (@Once, @On, or default ON)
MiddlewareManager.getInstance().addMiddleware(new MyMiddleware());
```

### Overriding Execution Mode Dynamically
You can override the execution mode programmatically without changing the class annotations:
```java
// Force ONCE mode (runs once per player session):
MiddlewareManager.getInstance().registerOnce(new MyMiddleware());

// Force ON mode (runs recurringly on every packet):
MiddlewareManager.getInstance().registerOn(new MyMiddleware());
```

---

## 🎯 Multi-Packet Targeting & NetworkHandlers

### Target Multiple Packets with `@TargetPackets`:
```java
@TargetPackets({InteractPacket.class, PlayerActionPacket.class})
@Once
public class ActionCheckMiddleware implements IMiddleware<BedrockPacket> {
    // Intercepts both InteractPacket and PlayerActionPacket
}
```

### Target by NetworkHandler with `@TargetNetworkHandler`:
```java
@TargetNetworkHandler(LoginHandler.class)
public class LoginAuthMiddleware implements IMiddleware<BedrockPacket> {
    // Intercepts packets handled by LoginHandler (such as LoginPacket)
}
```

---

## 🎯 Priority Ordering (`MiddlewarePriority`)

Middlewares are executed sequentially in strict order of priority:
1. `MiddlewarePriority.LOWEST` (0)
2. `MiddlewarePriority.LOW` (1)
3. `MiddlewarePriority.NORMAL` (2) *(default)*
4. `MiddlewarePriority.HIGH` (3)
5. `MiddlewarePriority.HIGHEST` (4)
6. `MiddlewarePriority.MONITOR` (5)

```java
@Priority(MiddlewarePriority.LOWEST)
public class PreLoginAuditMiddleware implements IMiddleware<LoginPacket> {
    // Executed first before all other middlewares
}
```

---

## 🌐 Individual Middlewares per `NetworkSession`

You can attach middlewares **specifically to a player's individual network connection** rather than globally to the entire server.

### 1. Attaching from an Event or Player Reference
```java
// Resolve the player's NetworkSession
NetworkSession session = NetworkSession.from(player);
// Or from a PlayerSessionHolder: NetworkSession.from(sessionHolder);

// Register a middleware exclusively for this player:
session.addMiddleware(new MyPlayerSpecificMiddleware());

// Force ONCE mode for this player:
session.registerOnce(new FirstTimePromptMiddleware());

// Force ON mode for this player:
session.registerOn(new PlayerActionFilterMiddleware());
```

### 2. Dynamically Attaching inside a Middleware (`MiddlewareContext`)
You can register subsequent middlewares directly during a previous packet's interception (e.g. during `LoginPacket`):

```java
@Once
public class PreLoginMiddleware implements IMiddleware<LoginPacket> {

    @Override
    public String getName() {
        return "PreLogin";
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
        // Dynamically attach a session middleware for subsequent packets:
        context.addSessionMiddleware(new IMiddleware<CommandRequestPacket>() {
            @Override
            public String getName() { return "PostLoginCommandLock"; }

            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> ctx) {
                // This middleware will ONLY execute for this specific player!
                return CompletableFuture.completedFuture(null);
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
```

### 3. Automatic Lifecycle & Memory Management
- **Zero Memory Leaks**: `NetworkSession` and `ONCE` tracking utilize identity-based weak key structures (`WeakIdentityHashMap`). When a player disconnects, all associated session records are automatically reclaimed by the Garbage Collector.
- **Priority Merging**: Global and session-specific middlewares are merged in $O(N + M)$ while preserving priority ordering (`LOWEST` &rarr; `MONITOR`).

---

## 🔍 Architecture & Diagrams

Interactive visualization and detailed architecture documentation are included in the repository:
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**: Full architecture breakdown with sequence diagrams and flowcharts.
- **[docs/architecture.html](docs/architecture.html)**: Interactive web dashboard displaying execution flow, comparison tables, and styling.

---

## 📄 License

This project is licensed under the **MIT License** in the name of **SenseiTarzan**. See the [LICENSE](LICENSE) file for details.

---

## 🤖 Reference & AI Assistance

- **Original Reference Project**: This project is based on and adapted from the original [AID-LEARNING/MiddlewarePlugin](https://github.com/AID-LEARNING/MiddlewarePlugin) created by **SenseiTarzan** for PocketMine-MP (PHP).
- **AI-Assisted Port & Modernization**: Advanced AI models were utilized as pair programmers to port the architecture from PHP to **Java 25 LTS**, design the targeted decorator hooks for PowerNukkitX, optimize asynchronous execution with **Virtual Threads** and **Scoped Values**, implement reflection-based generic type deduction, and engineer leak-free session pipelines via `WeakIdentityHashMap`.
