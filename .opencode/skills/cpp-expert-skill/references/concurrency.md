# C++ Concurrency & Threading

## Core Principle

Prefer **message-passing via queues** over shared mutable state. When shared state is unavoidable, use the narrowest synchronization primitive.

## Synchronization Primitives

| Primitive | Use When |
|-----------|----------|
| `std::atomic<T>` | Single variable, lock-free (counters, flags, state enums) |
| `std::lock_guard` | Simple critical section (single mutex) |
| `std::scoped_lock` (C++17) | **Locking multiple mutexes** -- deadlock-free |
| `std::unique_lock` | Need condition_variable or conditional locking |
| `thread_local` | Per-thread caches -- no sync needed |
| Task queues | Cross-thread dispatch -- preferred over shared state |

### Locks -- RAII, Minimal Scope
```cpp
// Single mutex
{ std::lock_guard<std::mutex> lock(m_mutex); m_map.emplace(k, std::move(v)); }

// Multiple mutexes -- std::scoped_lock avoids deadlocks automatically
{ std::scoped_lock lock(m_mutexA, m_mutexB); TransferData(); }
```
**Keep critical sections small.** No I/O, no allocation, no external calls under lock.

**Never lock multiple mutexes with separate `lock_guard` calls** -- this is a classic deadlock source. Use `std::scoped_lock` instead.

### Atomics
```cpp
static std::atomic<uint64_t> s_counter(1);
static uint64_t GetNext() { return s_counter++; }

// CAS for conditional updates
while (current < value)
    if (s_counter.compare_exchange_weak(current, value)) break;
```

### Thread-Local Storage
```cpp
static Cache& Instance() noexcept {
    static thread_local Cache tl_instance;
    return tl_instance;
}
```

## Task Queue Pattern
```cpp
template <typename T>
void RunOnWriteQueue(const wchar_t* name, T&& func) {
    auto item = std::make_unique<LambdaTask>(QueueId::Write,
        MakeItem(name, std::forward<T>(func)));
    AddToQueue(std::move(item));
}
```
**Always name queue items** for diagnostics.

## Lambda Capture Rules -- CRITICAL

| Lambda Lifetime | Capture Style |
|----------------|---------------|
| Synchronous in same scope | `[&]` safe |
| Queued async / dispatched | **By-value or move-capture ONLY** |
| Stored as member | **By-value or move-capture ONLY** |
| Timer/delayed callback | **By-value capture** |

```cpp
// Good: By-value for cross-thread
[context = GetContext()]() { RestoreContext(context); };

// Good: Move-capture transfers ownership
[data = std::move(m_pending)]() mutable { Process(data); };

// Good: Reference capture for synchronous lambdas
auto pred = [&state]() { return state.Ready(); };
WaitUntil(pred);  // Executes immediately

// Bad: NEVER reference-capture in async lambdas
auto BAD = [&local]() { use(local); };
DispatchAsync(std::move(BAD));  // local is gone!
```

## `std::jthread` (C++20) -- RAII Thread

`std::jthread` automatically joins on destruction and supports cooperative cancellation via `std::stop_token`. Prefer it over `std::thread`, which requires manual `join()` and calls `std::terminate` if destroyed while joinable.

```cpp
// Bad: std::thread requires manual join -- easy to leak
std::thread worker([&] { Process(); });
// If exception thrown before join(), std::terminate!
worker.join();

// Good: std::jthread joins automatically on destruction (RAII)
std::jthread worker([](std::stop_token token) {
    while (!token.stop_requested()) {
        ProcessNextItem();
    }
});
// Destructor requests stop and joins -- safe even on exception
```

## Key Rules

1. **Prefer queues over shared state.**
2. **Always RAII for locks.** Never manual lock/unlock.
3. **`std::scoped_lock` for multiple mutexes** -- deadlock-free by design.
4. **`std::jthread` over `std::thread`** -- RAII join and cooperative cancellation.
5. **Keep critical sections minimal.**
6. **Never capture by reference in async lambdas.**
7. **Use `thread_local` for per-thread caches.**
8. **Use `std::atomic` for counters and flags.**
9. **Name all queue items** for diagnostics.
