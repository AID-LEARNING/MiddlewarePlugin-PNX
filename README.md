# MiddlewarePlugin (Java 25 LTS for PowerNukkitX)

Portage officiel en **Java 25 LTS** du projet [MiddlewarePlugin](https://github.com/AID-LEARNING/MiddlewarePlugin) pour **PowerNukkitX (PNX)**.

Ce plugin introduit un système de **Middleware Asynchrone** permettant d'intercepter et d'exécuter des vérifications ou des chargements de données (base de données, Redis, API REST, etc.) sur n'importe quel paquet Bedrock ciblé avant qu'il ne soit traité par le serveur.

---

## 🚀 Fonctionnalités Clés & Modernisation Java 25

- **Optimisé pour Java 25 LTS & PNX** :
  - Compilé avec la cible bytecode **Java 25 (version 69)**.
  - **Scoped Values (`ScopedValue`)** : Propagation de contexte immuable et sans fuite mémoire (`MiddlewareContext.current()`) à travers les appels asynchrones.
  - **Named Virtual Threads** : Pool de threads virtuels nommés (`middleware-worker-#`) via `Thread.ofVirtual()` pour une observabilité et un débogage optimaux.
  - **Switch Expressions & Pattern Matching** pour l'évaluation fluide des modes d'exécution.
  - **Unnamed Variables (`_`)** pour un code concis et conforme aux standards modernes.
- **Ciblage Précis de Paquets (Pas de hook global)** :
  - Choisissez précisément le ou les paquets Bedrock à intercepter (ex. `CommandRequestPacket`, `InteractPacket`, `LoginPacket`, `SetLocalPlayerAsInitializedPacket`, etc.).
  - **Déduction automatique du paquet par le type générique `<T>`** : pas besoin d'écrire `getPacketClass()`.
  - **Seuls les handlers des paquets enregistrés sont hookés** dans PowerNukkitX, éliminant tout surcoût sur les paquets non utilisés.
- **Middlewares Individuels par `NetworkSession`** :
  - Possibilité d'attacher un middleware spécifiquement à la session réseau d'un joueur, avec nettoyage automatique par Garbage Collector (`WeakIdentityHashMap`).
- **Filtrage par NetworkHandler** :
  - Ciblez directement un `PacketHandler` PNX (ex. `@TargetNetworkHandler(LoginHandler.class)`). Seuls les paquets gérés par ce handler seront hookés et interceptés.
- **Mode d'exécution : `ONCE` ou `ON`** :
  - **`ON`** (par défaut) : Le middleware s'exécute **à chaque réception** du paquet durant la session du joueur.
  - **`ONCE`** : Le middleware s'exécute **une seule fois** par session/connexion joueur. Dès qu'il a été exécuté avec succès, les réceptions suivantes du même paquet par ce joueur ignoreront ce middleware.
- **Asynchrone & Non-bloquant** :
  - Utilise les `CompletableFuture` et les **Virtual Threads** de Java pour ne jamais bloquer le thread réseau (Netty) ni le tick principal du serveur.

---

## 🛠️ Modes d'Exécution : `ONCE` vs `ON`

### 1. Mode `ONCE` (Exécution unique par session joueur)

Idéal pour initialiser des données, vérifier un droit au premier clic/action, ou charger un profil une seule fois :

```java
package my.plugin.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.Once;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;

import java.util.concurrent.CompletableFuture;

@Once // <-- S'exécutera une seule fois par session joueur !
public class FirstInteractMiddleware implements IMiddleware<InteractPacket> {

    @Override
    public String getName() {
        return "FirstInteract";
    }

    @Override
    public Class<InteractPacket> getPacketClass() {
        return InteractPacket.class;
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<InteractPacket> context) {
        System.out.println("Premier clic du joueur dans cette session !");
        return CompletableFuture.completedFuture(null);
    }
}
```

---

### 2. Mode `ON` (Exécution continue à chaque paquet)

Idéal pour filtrer des commandes, vérifier la syntaxe d'un chat, ou valider chaque interaction :

```java
package my.plugin.middleware;

import dev.senseitarzan.middleware.api.IMiddleware;
import dev.senseitarzan.middleware.api.MiddlewareContext;
import dev.senseitarzan.middleware.api.On;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;

import java.util.concurrent.CompletableFuture;

@On // <-- S'exécutera à CHAQUE commande reçue
public class CommandFilterMiddleware implements IMiddleware<CommandRequestPacket> {

    @Override
    public String getName() {
        return "CommandFilter";
    }

    @Override
    public Class<CommandRequestPacket> getPacketClass() {
        return CommandRequestPacket.class;
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> context) {
        String command = context.getPacket().getCommand();
        if (command.startsWith("/op")) {
            context.disconnect("Commande non autorisée !");
            throw new IllegalStateException("Unauthorized command");
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

---

## 📦 Enregistrement d'un Middleware

### Enregistrement standard
```java
// Utilise le mode défini dans la classe (@Once, @On, ou défaut ON)
MiddlewareManager.getInstance().addMiddleware(new MyMiddleware());
```

### Enregistrement forcé en mode `ONCE` ou `ON`
Vous pouvez forcer dynamiquement le mode sans modifier la classe :
```java
// Forcer l'exécution une seule fois par session :
MiddlewareManager.getInstance().registerOnce(new MyMiddleware());

// Forcer l'exécution continue :
MiddlewareManager.getInstance().registerOn(new MyMiddleware());
```

---

## 🎯 Cibler plusieurs paquets ou un NetworkHandler

### Cibler plusieurs paquets avec `@TargetPackets` :
```java
@TargetPackets({InteractPacket.class, PlayerActionPacket.class})
@Once
public class ActionCheckMiddleware implements IMiddleware<BedrockPacket> {
    // S'exécutera uniquement sur InteractPacket et PlayerActionPacket
}
```

### Cibler par NetworkHandler avec `@TargetNetworkHandler` :
```java
@TargetNetworkHandler(LoginHandler.class)
public class LoginAuthMiddleware implements IMiddleware<BedrockPacket> {
    // S'exécutera sur les paquets gérés par LoginHandler (ici LoginPacket)
}
```

---

## 🎯 Ordre des Priorités (`MiddlewarePriority`)

Les middlewares sont exécutés séquentiellement selon leur priorité :
1. `MiddlewarePriority.LOWEST` (0)
2. `MiddlewarePriority.LOW` (1)
3. `MiddlewarePriority.NORMAL` (2) *(défaut)*
4. `MiddlewarePriority.HIGH` (3)
5. `MiddlewarePriority.HIGHEST` (4)
6. `MiddlewarePriority.MONITOR` (5)

---

## 🌐 Middlewares Individuels par `NetworkSession`

Vous pouvez attacher des middlewares **spécifiquement à une session réseau (joueur)** plutôt que globalement à tout le serveur.
Le middleware attaché ne s'exécutera que pour ce joueur spécifique !

### 1. Attacher depuis un événement ou une référence Joueur
```java
// Récupérer la NetworkSession du joueur
NetworkSession session = NetworkSession.from(player);
// Ou depuis un PlayerSessionHolder : NetworkSession.from(sessionHolder);

// Ajouter un middleware uniquement pour ce joueur :
session.addMiddleware(new MyPlayerSpecificMiddleware());

// Ou forcer en mode ONCE (une seule fois pour ce joueur) :
session.registerOnce(new FirstTimePromptMiddleware());

// Ou forcer en mode ON (à chaque paquet de ce joueur) :
session.registerOn(new PlayerActionFilterMiddleware());
```

### 2. Attacher dynamiquement au sein d'un Middleware (`MiddlewareContext`)
Vous pouvez attacher un middleware suivant directement pendant l'interception d'un paquet précédent (ex. lors du `LoginPacket`) :

```java
@Once
public class PreLoginMiddleware implements IMiddleware<LoginPacket> {

    @Override
    public String getName() {
        return "PreLogin";
    }

    @Override
    public CompletableFuture<Void> handle(MiddlewareContext<LoginPacket> context) {
        // Attacher dynamiquement un middleware pour la suite de cette session :
        context.addSessionMiddleware(new IMiddleware<CommandRequestPacket>() {
            @Override
            public String getName() { return "PostLoginCommandLock"; }

            @Override
            public CompletableFuture<Void> handle(MiddlewareContext<CommandRequestPacket> ctx) {
                // Ce middleware ne s'exécutera QUE pour ce joueur !
                return CompletableFuture.completedFuture(null);
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
```

### 3. Gestion automatique du cycle de vie
- **Nettoyage automatique sans fuite de mémoire** : `NetworkSession` et le suivi `ONCE` utilisent des structures à clés faibles basées sur l'identité (`WeakIdentityHashMap`), garantissant que dès que le joueur quitte le serveur, toutes ses données de session sont automatiquement libérées par le Garbage Collector.
- **Fusion fluide des priorités** : Si des middlewares globaux et des middlewares par session sont enregistrés, ils sont fusionnés et exécutés dans le respect strict des priorités (`LOWEST` -> `MONITOR`).

---

## 📄 Licence

Ce projet est sous licence **MIT** au nom de **SenseiTarzan**. Consultez le fichier [LICENSE](file:///home/gcaptari/IdeaProjects/TestAsync/LICENSE) pour plus d'informations.

---

## 🤖 Référence & Utilisation de l'IA (Reference & AI Assistance)

- **Projet de référence** : Ce projet est basé et adapté à partir du projet original [AID-LEARNING/MiddlewarePlugin](https://github.com/AID-LEARNING/MiddlewarePlugin) développé par **SenseiTarzan** pour PocketMine-MP (PHP).
- **Assistance et amélioration par IA** : Des modèles d'intelligence artificielle avancés ont été utilisés comme pairs programmeurs pour effectuer le portage architectural de PHP vers **Java 25 LTS**, concevoir l'interception chirurgicale sur PowerNukkitX, optimiser la gestion asynchrone avec les **Virtual Threads** et **Scoped Values**, intégrer l'auto-déduction des types génériques et la gestion fine par `NetworkSession` sans fuite de mémoire.

