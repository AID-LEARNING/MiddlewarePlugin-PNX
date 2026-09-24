# Architecture & Cycle d'Exécution des Middlewares (Java 25 LTS)

Ce document décrit en détail le cycle de vie, le mécanisme d'interception et l'ordonnancement asynchrone des middlewares au sein de **MiddlewarePlugin**.

---

## 📊 Diagramme de Séquence

```mermaid
sequenceDiagram
    autonumber
    actor Client as Joueur (Bedrock Client)
    participant Netty as Thread Netty (EventLoop)
    participant MPH as MiddlewarePacketHandler
    participant MM as MiddlewareManager
    participant Mid as Vos Middlewares (Virtual Threads)
    participant Orig as OriginalHandler (PowerNukkitX)

    Client->>Netty: Paquet Bedrock entrant (ex: LoginPacket, InteractPacket)
    Netty->>MPH: handle(packet, holder, server)
    MPH->>MM: executePipeline(context)
    
    Note over MM: 1. Récupère Globaux + NetworkSession<br/>2. Fusionne par Priorité (LOWEST -> MONITOR)<br/>3. Filtre les modes @Once
    
    loop Pour chaque middleware (en async)
        MM->>Mid: ScopedValue.where(CURRENT, context).call(middleware::handle)
        Mid-->>MM: CompletableFuture résolue
    end
    
    alt Rejet ou Déconnexion
        MM-->>MPH: CompletableFuture échouée (disconnect)
        MPH->>Netty: Session déconnectée / Paquet jeté
        Note over Orig: OriginalHandler N'EST PAS APPELÉ
    else Succès de la chaîne
        MM-->>MPH: CompletableFuture terminée
        MPH->>Netty: runOnNetworkThread()
        Netty->>Orig: originalHandler.handle(packet, holder, server)
    end
```

---

## 🔄 Flux de Décision (Flowchart)

```mermaid
flowchart TD
    A["Arrivée du Paquet Bedrock (Netty)"] --> B{"Ce type de paquet est-il ciblé ?"}
    
    B -- "Non" --> Z["originalHandler.handle(...)<br/>(Vitesse native 100%, 0 overhead)"]
    B -- "Oui" --> C["MiddlewarePacketHandler.handle(...)"]
    
    C --> D["Création de MiddlewareContext"]
    D --> E["Fusion Globaux + Session (O(N+M))<br/>Ordonnés par MiddlewarePriority (0 à 5)"]
    
    E --> F{"Y a-t-il des middlewares éligibles ?"}
    F -- "Non (ex: @Once déjà exécuté)" --> Z
    F -- "Oui" --> G["Début de la chaîne CompletableFuture (thenCompose)"]
    
    G --> H["Liaison Java 25 ScopedValue (CURRENT)"]
    H --> I["Exécution de middleware.handle(context)<br/>sur Virtual Threads (middleware-worker-#)"]
    
    I --> J{"Le middleware a-t-il réussi ?"}
    J -- "Non / Kick / Exception" --> K["Paquet jeté & Session fermée<br/>originalHandler JAMAIS exécuté"]
    J -- "Oui" --> L{"Reste-t-il des middlewares ?"}
    
    L -- "Oui" --> G
    L -- "Non" --> M["Retour sur Netty EventLoop<br/>(runOnNetworkThread)"]
    M --> Z
```

---

## 🔍 Les 5 Phases d'Exécution

### 1. Interception Non-bloquante (`MiddlewarePacketHandler`)
- PowerNukkitX transmet le paquet à `MiddlewarePacketHandler` (qui remplace l'original dans `PacketHandlerRegistry.MAP`).
- Si aucun middleware global ou par session n'est actif pour ce paquet, la requête est immédiatement déléguée à l'`originalHandler`.

### 2. Fusion et Tri par Priorités (`MiddlewareManager`)
- Les middlewares globaux et ceux de la `NetworkSession` du joueur sont fusionnés en $O(N + M)$.
- Respect strict des 6 priorités :
  $$\text{LOWEST (0)} \longrightarrow \text{LOW (1)} \longrightarrow \text{NORMAL (2)} \longrightarrow \text{HIGH (3)} \longrightarrow \text{HIGHEST (4)} \longrightarrow \text{MONITOR (5)}$$
- Les filtres `SessionState`, `NetworkHandler` et le mode `@Once` sont appliqués via des switch expressions modernes.

### 3. Chaînage Asynchrone & Contexte Java 25
- Les middlewares sont enchaînés avec `chain.thenCompose(...)`.
- `ScopedValue.where(MiddlewareContext.CURRENT, context)` injecte le contexte de manière immuable et sans fuite mémoire, permettant d'appeler `MiddlewareContext.current()` à tout moment.

### 4. Exécution I/O sur Threads Virtuels Nommés
- Les opérations longues (SQL, Redis, API REST) sont déléguées à `virtualExecutor` (`middleware-worker-#`).
- Le serveur ne subit **aucun lag de tick** ni ralentissement réseau.

### 5. Décision Finale
- **En cas de rejet / déconnexion** : le paquet est jeté, le handler original de PowerNukkitX n'est **jamais appelé**.
- **En cas de succès** : nous rebasculons sur l'EventLoop Netty (`runOnNetworkThread`) et appelons `originalHandler.handle(...)`.

---

> [!NOTE]
> Ce projet s'inspire directement du plugin de référence [AID-LEARNING/MiddlewarePlugin](https://github.com/AID-LEARNING/MiddlewarePlugin) créé par **SenseiTarzan**. Il a été converti, restructuré et enrichi pour Java 25 LTS & PowerNukkitX avec l'assistance d'outils d'intelligence artificielle avancés.

