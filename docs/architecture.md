# Architecture Documentation

## Overview

This project implements a cross-platform unzip application using Clean Architecture principles with MVVM pattern, targeting primarily Windows but supporting Linux and macOS. The application follows macOS-inspired UX patterns for seamless archive extraction.

## Clean Architecture Layers

### 1. Domain Layer (Business Logic)
- **Entities**: Core business objects (Archive, ExtractionResult, etc.)
- **Use Cases**: Business rules and application logic
- **Repository Interfaces**: Abstract contracts for data access

### 2. Data Layer
- **Repository Implementations**: Platform-specific data access
- **Data Sources**: File system, 7zip integration, OS services
- **Models**: Data transfer objects

### 3. Presentation Layer
- **ViewModels**: State management with Kotlin Flow
- **UI**: Native platform interfaces (when needed)
- **Mappers**: Convert between domain and presentation models

## MVVM with Kotlin Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   View/UI       │◄──►│   ViewModel     │◄──►│   Repository    │
│                 │    │                 │    │                 │
│ - File Dialog   │    │ - State Flow    │    │ - File Ops      │
│ - Progress UI   │    │ - Commands      │    │ - 7zip Calls    │
│ - Notifications │    │ - Error Handle  │    │ - OS Integration│
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Platform Abstraction

### Common Interface
```kotlin
interface FileSystemRepository {
    suspend fun extractArchive(archivePath: String): Flow<ExtractionProgress>
    suspend fun moveToTrash(filePath: String): Result<Unit>
    suspend fun getArchiveContents(archivePath: String): List<ArchiveEntry>
}
```

### Platform-Specific Implementations
- **Windows**: Win32 API for trash, Registry for file associations
- **macOS**: NSFileManager, Launch Services
- **Linux**: XDG standards, desktop integration

## Core Use Cases

1. **ExtractArchiveUseCase**
   - Analyzes archive contents
   - Determines extraction strategy (single file vs directory)
   - Coordinates extraction process

2. **ManageFileAssociationsUseCase**
   - Registers/unregisters file type associations
   - Handles double-click events

3. **TrashManagementUseCase**
   - Moves files to platform-appropriate trash location
   - Handles trash operation failures

## Data Flow

```
File Double-Click → OS Handler → Application Entry Point
                                        ↓
                                 Extract Command
                                        ↓
                               ExtractArchiveUseCase
                                        ↓
                           ┌─────────────┴─────────────┐
                           ↓                           ↓
                  Analyze Contents              Extract Files
                           ↓                           ↓
                  Determine Strategy           Update Progress
                           ↓                           ↓
                  Execute Extraction           Notify UI
                           ↓                           ↓
                    Move to Trash              Complete
```

## Technology Stack

- **Kotlin Multiplatform**: Cross-platform business logic
- **Kotlin Coroutines**: Async operations
- **Kotlin Flow**: Reactive state management
- **7zip**: Archive handling (via executable or library)
- **Platform APIs**: File system, trash, associations

## Testing Strategy

- **Unit Tests**: Domain layer (use cases, entities)
- **Integration Tests**: Repository implementations
- **Platform Tests**: OS-specific functionality
- **E2E Tests**: Complete extraction workflows

## File Structure

```
src/
├── commonMain/kotlin/
│   ├── domain/
│   │   ├── entities/
│   │   ├── usecases/
│   │   └── repositories/
│   ├── data/
│   │   ├── repositories/
│   │   └── models/
│   └── presentation/
│       └── viewmodels/
├── windowsMain/kotlin/
│   └── data/repositories/
├── linuxMain/kotlin/
│   └── data/repositories/
└── macosMain/kotlin/
    └── data/repositories/
```