# C++ Style Guide

Universal C++ naming, formatting, and organization conventions.

## Naming Conventions

### Classes & Structs
- **PascalCase** for all class and struct names.
- Interfaces use `I` prefix: `IStorageAccess`, `IObserver`.
- Base classes use `Base` suffix: `DispatcherBase`, `UpdateSinkBase`.
- Test classes use `_Test` suffix: `ScopeGuard_Test`.

### Functions & Methods
- **PascalCase** for all public functions and methods.
- Factory functions: `Create` or `Make` prefix.
- Getters: `Get` prefix or property-style.
- Boolean getters: `Is`/`Has`/`Can` prefix.

### Variables

| Category | Convention | Examples |
|----------|-----------|----------|
| Member variables | `m_` prefix + camelCase | `m_data`, `m_spThread`, `m_lockObject` |
| Static members | `s_` prefix | `s_counter`, `s_initialized` |
| Constants | `c_` prefix + camelCase | `c_maxRetries`, `c_defaultTimeout` |
| Smart pointers | `sp` prefix | `spTarget`, `spThread`, `spTask` |
| Raw pointers (params) | `p` prefix | `pTarget`, `pHost`, `pData` |
| Thread-local | `tl_` prefix | `tl_instance`, `tl_cache` |
| Global | `g_` prefix | `g_configValues` |

### Enums
- Always `enum class`, never plain `enum`.
- PascalCase for names and values.
- Specify underlying type when size matters.

### Template Parameters
- `T` prefix for type params: `TResult`, `TError`.
- Non-type template parameters: camelCase (`size`, `maxCount`).
- C++20 Concept names: PascalCase adjectives (`Sortable`, `Serializable`, `Hashable`).

### Namespaces
- PascalCase. Compact declarations.

## Code Organization

### Header Files
- Always `#pragma once`.
- Include ordering:
  1. Local project includes (`"LocalHeader.h"`)
  2. External includes (`<Threading/IDispatcher.h>`)
  3. Forward declarations grouped by namespace
- Use forward declarations aggressively to minimize includes.

### Interface Convention
Interfaces are `struct` with all pure virtual methods:

```cpp
struct IStorage : public IRefCounted
{
    virtual ~IStorage() noexcept = default;
    virtual void QueueAction(ActionPtr&& spAction) = 0;
    virtual RequestID QueueRead(std::unique_ptr<ReadTask>&&) = 0;
};
```

### Comments
- Comments explain **WHY**, not **WHAT** -- the code explains what.
- Do NOT over-comment. Only comment non-obvious logic.
- Use `//` for inline comments.
