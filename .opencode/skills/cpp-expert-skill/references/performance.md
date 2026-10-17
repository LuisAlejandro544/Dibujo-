# C++ Performance Discipline

Performance in C++ means understanding the machine. Not premature optimization -- **informed design decisions** that avoid needlessly slow patterns from the start.

## The Zero-Overhead Principle (Stroustrup)

> "What you don't use, you don't pay for. What you do use, you couldn't hand-code any better."

This principle justifies using `constexpr`, templates, `std::array`, and inline functions -- they compile down to the same code you'd write by hand, with better safety. Choose abstractions that the compiler can eliminate entirely.

## Rule Zero: Measure First

Never optimize based on intuition. Profile, identify the hotspot, optimize it, measure again.

```cpp
// Bad: "I think this is slow, let me optimize it"
// Good: "Profile shows 40% of time in ParseJson -- let me optimize ParseJson"
```

**But**: Don't use "measure first" as an excuse to write needlessly slow code. There's a difference between premature optimization and **choosing the right data structure**.

## Data-Oriented Design

The CPU doesn't care about your class hierarchy. It cares about **cache lines**.

### Prefer Contiguous Memory

```cpp
// Bad: Pointer-chasing -- every access is a cache miss
std::list<Item> items;
std::map<Key, Value> lookup;

// Good: Cache-friendly -- contiguous memory
std::vector<Item> items;
std::unordered_map<Key, Value> lookup;  // Or flat_map for small N
```

### Hot/Cold Field Splitting

If you iterate over objects but only touch some fields, separate hot and cold data:

```cpp
// Bad: Cold data pollutes cache line during hot loop
struct Entity
{
    Vec3 position;         // Hot -- used every frame
    Vec3 velocity;         // Hot -- used every frame
    std::string name;      // Cold -- used rarely
    std::string texture;   // Cold -- used rarely
    uint64_t createdAt;    // Cold -- used rarely
};

// Good: Hot data packed together
struct EntityTransform { Vec3 position; Vec3 velocity; };
struct EntityMetadata { std::string name; std::string texture; uint64_t createdAt; };

std::vector<EntityTransform> transforms;  // Iterate this in physics
std::vector<EntityMetadata> metadata;     // Access only when needed
```

### Array of Structs vs Struct of Arrays

For tight loops over many objects, SoA can be dramatically faster:

```cpp
// AoS -- one cache miss per particle for each field access
struct Particle { float x, y, z, vx, vy, vz; };
std::vector<Particle> particles;

// SoA -- sequential memory access for each field
struct Particles {
    std::vector<float> x, y, z;
    std::vector<float> vx, vy, vz;
};
```

## Allocation Discipline

### Avoid Allocations in Hot Paths

```cpp
// Bad: Allocates every call
std::string Format(int value)
{
    return std::to_string(value);  // Allocates
}

// Good: Pre-allocate or use stack buffer
void Format(int value, char* buffer, size_t bufferSize);

// Good: Reserve when size is known
std::vector<Item> results;
results.reserve(expectedCount);  // One allocation, not N
```

### Small Buffer Optimization

Use types that avoid heap allocation for small sizes:
- `std::string` (most implementations have SSO for ~15-22 chars)
- `std::array` for fixed-size collections
- Stack-allocated buffers for temporary formatting

### Move, Don't Copy

```cpp
// Bad: Copies the entire vector
std::vector<Item> GetItems() { return m_items; }

// Good: Move when the source is no longer needed
std::vector<Item> TakeItems() { return std::move(m_items); }

// Good: Return by value -- compiler applies NRVO
std::vector<Item> BuildItems()
{
    std::vector<Item> result;
    // ... populate ...
    return result;  // NRVO: no copy, no move
}
```

### `noexcept` Move Is Critical for Performance

`std::vector` will only move elements during reallocation if the move constructor is `noexcept`. Without it, vector silently falls back to **copying** for exception safety. This is one of the most common silent performance regressions.

```cpp
// Bad: vector will COPY during reallocation
Widget(Widget&& other);

// Good: vector will MOVE during reallocation
Widget(Widget&& other) noexcept;
```

**Rule**: Always mark move constructors and move assignment operators `noexcept`.

## Prefer Algorithms Over Raw Loops

Standard algorithms are more expressive, less error-prone (no off-by-one), and often better optimized.

```cpp
// Bad: Raw loop
int total = 0;
for (size_t i = 0; i < items.size(); ++i)
    total += items[i].Score();

// Good: Algorithm
int total = std::accumulate(items.begin(), items.end(), 0,
    [](int sum, const auto& item) { return sum + item.Score(); });

// Better: C++20 ranges with projection
auto highScorers = items | std::views::filter([](const auto& i) { return i.Score() > 100; });
```

## Algorithmic Awareness

### Choose the Right Container

| Need | Container | Why |
|------|-----------|-----|
| Sequential access | `std::vector` | Cache-friendly, O(1) amortized push_back |
| Key lookup | `std::unordered_map` | O(1) average lookup |
| Sorted iteration + lookup | `std::map` or sorted `vector` | O(log N) lookup with order |
| Small fixed set | `std::array` + linear search | Faster than hash for N < ~30 |
| FIFO queue | `std::deque` or ring buffer | O(1) push/pop both ends |

**Rule**: `std::vector` is the right default. Switch only when you have a reason.

### Avoid Unnecessary Work

```cpp
// Bad: Recomputes every call
bool HasPermission(User& user, Resource& res)
{
    auto perms = FetchAllPermissions(user);  // Expensive
    return perms.contains(res.Id());
}

// Good: Cache the result
bool HasPermission(User& user, Resource& res)
{
    if (auto cached = m_permCache.Find(user.Id()))
        return cached->contains(res.Id());

    auto perms = FetchAllPermissions(user);
    m_permCache.Insert(user.Id(), perms);
    return perms.contains(res.Id());
}
```

## String Performance

Strings are one of the biggest performance sinks in C++ codebases.

```cpp
// Bad: Unnecessary copies
void Process(std::string name);          // Copies on every call
std::string result = a + b + c + d;      // Multiple allocations

// Good: Avoid copies
void Process(std::string_view name);     // Zero-copy view
std::string result;
result.reserve(a.size() + b.size() + c.size() + d.size());
result += a; result += b; result += c; result += d;  // One allocation
```

## Branch Prediction

### Put the Common Case First

```cpp
// Good: Common case (success) is the fall-through
if (likely_condition)
{
    DoCommonThing();
}
else
{
    HandleRareError();
}
```

### Prefer Lookup Tables Over Long if/else Chains

```cpp
// Bad: Branch-heavy
const char* ToString(Color c)
{
    if (c == Color::Red) return "red";
    else if (c == Color::Green) return "green";
    else if (c == Color::Blue) return "blue";
    // ...
}

// Good: Table lookup -- no branches
constexpr std::array c_colorNames = { "red", "green", "blue" };
const char* ToString(Color c) { return c_colorNames[static_cast<int>(c)]; }
```

## Compile-Time Computation

Move work from runtime to compile time whenever possible:

```cpp
// Bad: Runtime computation
const int TABLE_SIZE = CalculateOptimalSize();

// Good: Compile-time computation
constexpr int c_tableSize = CalculateOptimalSize();  // Computed at compile time
static constexpr std::array c_lookupTable = BuildTable();  // Built at compile time
```

## Virtual Function Awareness

Virtual dispatch has a cost: indirect branch + potential cache miss on vtable.

```cpp
// Bad: Virtual call in tight loop
for (auto& item : millions_of_items)
    item->Process();  // Virtual dispatch per item

// Good: Sort by type, batch-process
// Or use CRTP for static polymorphism
template <typename Derived>
class Base {
    void Process() { static_cast<Derived*>(this)->ProcessImpl(); }
};
```

**But**: Don't avoid virtual functions in normal code. Only consider alternatives in measured hot paths.

## Key Rules

1. **Zero-overhead abstractions** -- use abstractions the compiler can eliminate entirely.
2. **Measure first** -- but don't write needlessly slow code while waiting to measure.
3. **Think about data layout** -- contiguous > pointer-chasing. `vector` is the default.
4. **Avoid allocations in hot paths** -- reserve, pre-allocate, use stack buffers.
5. **Move, don't copy** -- especially containers, strings, and unique_ptrs.
6. **Mark moves `noexcept`** -- required for `std::vector` optimization.
7. **Prefer algorithms over raw loops** -- `std::ranges`, `std::transform`, `std::accumulate`.
8. **Pass by `string_view`** -- avoid string copies in function parameters.
9. **`constexpr` for compile-time work** -- tables, constants, computations.
10. **Right container for the job** -- don't use `map` when `unordered_map` or `vector` is better.
11. **Common case first** -- structure branches for the hot path.
12. **Virtual functions are fine** -- except in measured hot loops.
