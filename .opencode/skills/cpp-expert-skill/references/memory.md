# C++ Memory Management & Ownership

## The Ownership Rule

Every heap-allocated object must have **exactly one clear owner** at all times. Ownership is expressed through types, not comments.

## Smart Pointer Selection

| Pointer Type | Ownership | Use When |
|-------------|-----------|----------|
| `std::unique_ptr<T>` | Exclusive | Default choice. Single owner; transfer via `std::move` |
| `std::shared_ptr<T>` | Shared | Multiple owners with shared lifetime. Use sparingly. |
| `RefPtr<T>` (custom) | Shared, COM-style | Object implements `IRefCounted` interface |
| Raw pointer (`T*`) | **Non-owning** | Observer/borrowed reference; never manages lifetime |

### std::unique_ptr -- Default Choice

```cpp
auto spTask = std::make_unique<LoadTask>(type, handle);
queue.Push(std::move(spTask));  // Transfer ownership
```

### Raw Pointers -- Non-Owning Only

```cpp
void OnChanged(IHost* pHost, const INode* pNode) const;  // Borrowed

class Reactor {
    DataStore* m_pStore;   // Non-owning -- lifetime managed externally
};
```

## RAII -- Everything

### Scope Guards
```cpp
auto guard = GetScopeGuard([]() { Cleanup(); });

BasicScopeGuard SetContextForScope(const Data& data) noexcept
{
    Data old = GetContext();
    SetContext(data);
    return BasicScopeGuard([old]() { SetContext(old); });
}
```

### Locks -- Always RAII
```cpp
// Good: RAII
{ std::lock_guard<std::mutex> lock(m_mutex); m_map.emplace(k, std::move(v)); }

// Bad: NEVER manual lock/unlock
m_mutex.lock(); DoWork(); m_mutex.unlock();
```

## Rule of Zero / Rule of Five

### Rule of Zero (Preferred)
If your class only contains RAII members (smart pointers, standard containers, value types), **declare no special members**. Let the compiler generate them correctly.

```cpp
// Good: Rule of Zero -- compiler generates correct copy/move/destructor
class UserSession {
    std::string m_name;
    std::unique_ptr<Connection> m_spConn;
    std::vector<Token> m_tokens;
    // No destructor, no copy/move constructors, no assignment operators
};
```

### Rule of Five
If you must define **any** of the five special members (destructor, copy constructor, copy assignment, move constructor, move assignment), you must define **all five** -- or explicitly `= delete` the ones you don't want.

```cpp
class Buffer {
    void* m_pData;
    size_t m_size;
public:
    ~Buffer() noexcept;
    Buffer(const Buffer& other);                    // Deep copy
    Buffer& operator=(const Buffer& other);         // Copy-assign
    Buffer(Buffer&& other) noexcept;                // Move
    Buffer& operator=(Buffer&& other) noexcept;     // Move-assign
};
```

**Always prefer Rule of Zero.** If you find yourself writing a destructor, ask whether wrapping the resource in a RAII type (e.g., `unique_ptr` with custom deleter) would eliminate the need.

## Move Semantics

```cpp
queue.Push(std::move(task));                        // Transfer ownership
[data = std::move(m_pending)]() mutable { ... };    // Move into capture

template <typename T>
void Dispatch(const wchar_t* name, T&& func) {
    auto item = MakeItem(name, std::forward<T>(func));  // Perfect forward
    AddToQueue(std::move(item));
}
```

**Critical**: Mark move constructors and move assignment `noexcept`. Without `noexcept`, `std::vector` will **copy** instead of move during reallocation -- a silent performance regression.

```cpp
// Good: noexcept move enables vector optimization
MyType(MyType&& other) noexcept;
MyType& operator=(MyType&& other) noexcept;
```

## Non-Owning Views

### `std::span<T>` (C++20) -- Contiguous Range View
`std::span` replaces `(T* ptr, size_t len)` parameter pairs with a safe, non-owning view over contiguous memory.

```cpp
// Bad: Raw pointer + size -- easy to mismatch
void Process(const float* data, size_t count);

// Good: span is safe, accepts vector, array, C-array
void Process(std::span<const float> data);

std::vector<float> v = {1.0f, 2.0f, 3.0f};
Process(v);                         // Works
std::array<float, 3> a = {1, 2, 3};
Process(a);                         // Works
```

### `gsl::not_null<T*>` -- Non-Nullable Pointer
Express "this pointer must not be null" in the type system, not in comments or runtime checks.

```cpp
// Bad: Relies on documentation or runtime assert
void Attach(INode* pNode);  // Must not be null

// Good: Compiler and type system enforce non-null
void Attach(gsl::not_null<INode*> pNode);
```

### `gsl::owner<T*>` -- Annotating Legacy Owning Pointers
When refactoring legacy code that cannot yet use `unique_ptr`, annotate owning raw pointers with `gsl::owner` to express intent and enable static analysis.

```cpp
gsl::owner<Widget*> pWidget = new Widget();  // Explicit: this raw pointer owns
```

## Key Rules

1. **Default to `unique_ptr`** -- only `shared_ptr` when sharing is required.
2. **Raw pointers are non-owning** -- they borrow, never manage lifetime.
3. **RAII for everything** -- locks, handles, state restoration, cleanup.
4. **Rule of Zero** -- prefer compiler-generated special members. Rule of Five if you must.
5. **Mark move operations `noexcept`** -- required for `std::vector` optimization.
6. **Use `std::span`** for non-owning views over contiguous ranges.
7. **Use `sp` prefix** for smart pointers, `p` for raw pointer parameters.
8. **Transfer with `std::move`** -- never copy a `unique_ptr`.
9. **Scope guards for paired operations** -- set/restore, open/close, push/pop.
