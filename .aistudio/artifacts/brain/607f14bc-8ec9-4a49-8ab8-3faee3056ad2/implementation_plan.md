# BeamDrop: Clean Architecture Refactoring, PC Web Lobby Integration, & Comprehensive Documentation

Comprehensive blueprint to refactor **BeamDrop** into a modular, highly reusable Clean Architecture codebase, eliminate Jetpack Compose UI lag and frame drops, integrate seamless real-time PC Web client detection in the lobby, build an in-app interactive Guide Screen, and provide exhaustive architectural and user documentation.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed through interactive clarification and define the exact scope of this refactoring:

- **Confirmed Decision 1 (Architecture & Reusability)**: Adopt a full **Clean Architecture** model, decomposing the monolithic `MainViewModel` (850 lines) and `MainScreen` (930 lines) into distinct layers:
  - **Data Layer**: Repositories for MediaStore, App Management, Storage Explorer, and Scoped Storage NIO file writing.
  - **Domain Layer**: Clean transfer coordinators, topology managers, and discovery use cases.
  - **Presentation Layer**: Modular, stable Composables with `@Immutable` / `@Stable` UI states to eradicate frame drops and unnecessary recompositions.
- **Confirmed Decision 2 (Documentation Strategy)**: Dual-level documentation:
  - **In-App Interactive Guide Screen**: Accessible via a prominent TopAppBar action, featuring visual tabs: *User Quickstart*, *PC Web Hub (Browser)*, *Swarm P2P Mode*, and *Under-The-Hood Architecture*.
  - **Root `README.md`**: Exhaustive Markdown reference covering system requirements, architectural diagrams, protocol specifications, chunking mechanics, and developer guides.
- **Confirmed Decision 3 (PC Web Client Lobby Experience)**: A dedicated **Real-time Active Web Sessions Card** displayed directly in the Lobby screen. The embedded HTTP server starts seamlessly in passive standby mode, automatically detecting any PC, Mac, or Linux browser hitting the local IP and reflecting connected clients instantly with real-time status and quick file beam actions.

---

## 1. Overview & Core Concept

**BeamDrop** is an ultra-fast, 100% offline peer-to-peer file transfer system engineered for zero-trust, internet-free environments. It bridges modern mobile devices (Android) and any standard desktop computer (Windows, macOS, Linux) without requiring external installations on the computer.

### Key Capabilities
1. **Zero-Setup Local Web Hub**: Any PC on the same Wi-Fi or connected to the phone's Hotspot opens `http://<ip>:8080` in Chrome/Edge/Firefox to transfer files at maximum LAN speeds.
2. **Simple-to-Swarm Dynamic Topology**: Transitions seamlessly from standard single-stream HTTP transfers to multi-peer BitTorrent-like Swarm distribution when multiple peers join.
3. **Resilient Scoped Storage & Auto-Resume**: Uses Android 11+ MediaStore NIO `FileChannel` random access writing with byte-offset HTTP resuming across sudden network drops.
4. **Bluetooth Offline APK Sharing**: Built-in direct extractor to send the BeamDrop APK via Bluetooth to nearby users without internet or app stores.

---

## 2. User Experience & Visual Design

### 2.1 Lobby & Active Web Client Card
- **Real-time Web Sessions Card**: Whenever a browser connects to `http://<ip>:8080`, the phone displays a sleek, elevated M3 card displaying:
  - Device badge (e.g., `Windows PC`, `MacBook`, `Linux Client`) with corresponding platform icon.
  - Client IP and connection status (Active / Idle ping).
  - Quick action to beam selected files directly to the PC or view incoming files.
- **Auto-Standby Server**: The embedded server runs in lightweight standby on app launch, meaning users never have to manually guess or toggle "Receive" to make their PC connect.

### 2.2 Recomposition & Performance Optimizations
- **Frame Drop Elimination**:
  - Replace raw StateFlow collections that trigger full-screen recompositions with fine-grained derived states and `@Immutable` state classes.
  - Decouple transfer progress tick updates (which emit at high frequency) from the main Lobby screen into an isolated `TransferModal` overlay.
  - Use `key` identifiers in all `LazyColumn` items and wrap lambda event listeners with `remember` to prevent unnecessary component redraws.

### 2.3 In-App Guide Screen (`GuideScreen.kt`)
- Integrated modern M3 Tabbed Guide:
  1. **Quick Start**: Phone-to-phone transfer steps, Wi-Fi Hotspot pairing, QR code scanning.
  2. **PC & Web Sharing**: How to connect from any desktop browser, drag-and-drop web UI, zero software installation.
  3. **Swarm Multi-Peer**: How the BitTorrent-style chunk distribution accelerates multi-recipient transfers.
  4. **Codebase & Architecture**: Technical map detailing each module, protocol, and design pattern.

---

## 3. Key Architectural Decisions & Modularization Plan

### Decision 1: Monolith Breakdown into Clean Architecture
- **Problem**: `MainViewModel.kt` is ~850 lines handling discovery, server socket lifecycle, batch jobs, MediaStore queries, Hotspot tethering, and Bluetooth sharing.
- **Solution**:
  - `data/repository/MediaRepository.kt`: Encapsulates MediaStore image/video/audio retrieval and app package extraction.
  - `domain/usecase/TransferManager.kt`: Coordinates transfers, session states, and foreground service synchronization.
  - `domain/usecase/LobbyDiscoveryCoordinator.kt`: Combines mDNS (NSD) discovery and Web HTTP client heartbeat tracking.
  - `viewmodel/MainViewModel.kt`: Lean coordinator strictly exposing UI state to Composables.

### Decision 2: UI Modularity
- **Problem**: `MainScreen.kt` is ~930 lines containing cards, dialogs, lists, and header logic in one massive file.
- **Solution**:
  - `ui/lobby/LobbyScreen.kt`: The main home screen container.
  - `ui/lobby/components/ActiveWebClientCard.kt`: Real-time PC Web presence widget.
  - `ui/lobby/components/NetworkStatusPill.kt`: Network IP and Hotspot indicator.
  - `ui/lobby/components/DiscoveredPeerCard.kt`: Individual Android / PC peer row.
  - `ui/guide/GuideScreen.kt`: The comprehensive interactive documentation screen.

---

## 4. Technical Architecture & Component Hierarchy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
├────────────────────────────────┬───────────────────────────────────────┤
│          LobbyScreen           │             GuideScreen               │
│  ┌──────────────────────────┐  │  ┌─────────────────────────────────┐  │
│  │    NetworkStatusPill     │  │  │   Quickstart / User Guide       │  │
│  ├──────────────────────────┤  │  ├─────────────────────────────────┤  │
│  │   ActiveWebClientCard    │  │  │   PC Web Hub Walkthrough        │  │
│  ├──────────────────────────┤  │  ├─────────────────────────────────┤  │
│  │   DiscoveredPeerCards    │  │  │   Swarm Topology Explained      │  │
│  └──────────────────────────┘  │  ├─────────────────────────────────┤  │
│                                │  │   Architecture & Code Reference │  │
│                                │  └─────────────────────────────────┘  │
└────────────────────────────────┴───────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Presentation / ViewModel Layer                    │
│                             MainViewModel                              │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          Domain / Use Case Layer                       │
│  ┌─────────────────────────────┐     ┌──────────────────────────────┐  │
│  │  LobbyDiscoveryCoordinator  │     │   TransferManager / Session  │  │
│  └──────────────┬──────────────┘     └──────────────┬───────────────┘  │
└─────────────────┼───────────────────────────────────┼──────────────────┘
                  │                                   │
                  ▼                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Data & Network Infrastructure                   │
│  ┌───────────────────────────┐     ┌────────────────────────────────┐  │
│  │     EmbeddedFileServer    │     │      P2pSenderClient &         │  │
│  │  - Web UI Generator      │     │      HybridTransferCoordinator │  │
│  │  - Client Ping Tracking   │     │      - Dynamic ChunkManager    │  │
│  │  - Resumable Streaming    │     │      - Scoped Storage FileChan │  │
│  ├───────────────────────────┤     ├────────────────────────────────┤  │
│  │    NsdDiscoveryManager    │     │       MediaRepository          │  │
│  │    & HotspotManager       │     │       & FileExplorerHelper     │  │
│  └───────────────────────────┘     └────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Implementation Steps (To Execute Post-Approval)

1. **Extract Repositories & Utilities**:
   - Create `data/repository/MediaRepository.kt` to handle MediaStore and App inventory queries.
   - Separate Lobby peer state management and PC client heartbeat registry into `domain/LobbyDiscoveryCoordinator.kt`.
2. **Enable Seamless PC Standby & Web Presence**:
   - Configure `EmbeddedFileServer` to launch in lightweight standby mode upon app startup.
   - Refine client detection logic in `EmbeddedFileServer` to track real-time browser sessions and report them immediately to the lobby.
3. **Compose Performance & UI Modularization**:
   - Break `MainScreen.kt` down into modular components with `@Immutable` data holders to stop frame drops.
   - Create `ActiveWebClientCard.kt` to clearly present connected PC browsers in the lobby.
4. **Build In-App Guide Screen (`GuideScreen.kt`)**:
   - Implement an interactive, beautifully styled Material 3 documentation screen with tabbed sections and visual explanations.
   - Add a direct Guide/Help button on the top app bar.
5. **Generate Comprehensive Root `README.md`**:
   - Produce a complete, developer- and user-ready `README.md` documenting architecture, components, networking protocols, user guide, and security model.
6. **Compile & Verify**:
   - Run `compile_applet` to guarantee error-free compilation and flawless runtime stability.
