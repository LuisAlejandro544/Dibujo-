# C++ API Design Principles

Design APIs as if every user is a busy developer who will read the header for 30 seconds, try to use it, and curse you when it doesn't work as expected.

## Principle 1: Make Interfaces Hard to Misuse

The compiler is your best documentation. Design types so that incorrect usage **doesn't compile**.

```cpp
// Bad: Easy to misuse -- what's true/false? What's 0?
Connection Connect(const char* host, int port, bool secure, bool verify, int timeout);
Connect("example.com", 443, true, false, 0);  // What do these bools mean?

// Good: Hard to misuse -- named types enforce correctness
enum class Security { None, TLS };
enum class CertVerification { Skip, Verify };
struct Timeout { std::chrono::seconds value; };

Connection Connect(std::string_view host, uint16_t port,
    Security security, CertVerification verify, Timeout timeout);
Connect("example.com", 443, Security::TLS, CertVerification::Verify, Timeout{30s});
```

### Strong Types for Domain Values

```cpp
// Bad: Primitive obsession -- easy to swap arguments
void Transfer(uint64_t fromAccount, uint64_t toAccount, double amount);
Transfer(destId, srcId, 100.0);  // Compiles! Bug!

// Good: Strong types prevent swaps
struct AccountID { uint64_t value; };
struct Amount { double value; };
void Transfer(AccountID from, AccountID to, Amount amount);
```

### Use `enum class` to Replace Bool Parameters

```cpp
// Bad: Unclear at call site
widget.SetVisible(true, false);

// Good: Self-documenting
enum class Visible { No, Yes };
enum class Animated { No, Yes };
widget.SetVisible(Visible::Yes, Animated::No);
```

## Principle 2: Minimal Surface Area

Expose the **minimum** needed. Everything public is a commitment.

```cpp
// Bad: Too much exposed -- users depend on internals
class ConnectionPool
{
public:
    std::vector<Connection>& GetConnections();  // Exposes internal container
    void SetMaxSize(int n);
    void SetMinSize(int n);
    void SetTimeout(int ms);
    void SetRetryPolicy(RetryPolicy p);
    // 15 more setters...
};

// Good: Minimal -- configuration via constructor, simple public API
class ConnectionPool
{
public:
    explicit ConnectionPool(const PoolConfig& config);
    Connection Acquire();
    void Release(Connection conn);
    size_t ActiveCount() const;
};
```

### Make Members Private by Default

Only make something public when there's a concrete need. If you're not sure, it's private.

### Prefer Free Functions Over Methods

If a function doesn't need access to private members, make it a free function in the namespace:

```cpp
// Bad: Unnecessary method -- doesn't need private access
class Date {
public:
    int DaysBetween(const Date& other) const;
};

// Good: Free function -- reduces coupling
int DaysBetween(const Date& a, const Date& b);
```

## Principle 3: Ownership Is Part of the API

The signature must communicate who owns what.

```cpp
// Caller creates, function takes ownership
void AddTask(std::unique_ptr<ITask> spTask);

// Function creates, caller owns
std::unique_ptr<ITask> CreateTask(TaskType type);

// Shared ownership (rare -- justify it)
void RegisterCallback(std::shared_ptr<ICallback> spCallback);

// Non-owning reference -- caller guarantees lifetime
void Process(const IData& data);
void Observe(IData* pData);  // nullable non-owning
```

**Rule**: If the ownership semantics aren't obvious from the signature, the API is wrong.

## Principle 4: Const Correctness

`const` communicates intent and prevents bugs. Use it everywhere it applies.

```cpp
class DataStore
{
public:
    // Mutating operations -- no const
    void Insert(Key key, Value value);
    bool Remove(Key key);

    // Non-mutating operations -- const
    const Value* Find(Key key) const;
    size_t Size() const;
    bool IsEmpty() const;
    bool Contains(Key key) const;
};

// Parameters: const ref for read-only access
void Print(const std::string& message);

// Prefer std::span over const vector& for contiguous range parameters (C++20)
// Accepts vector, array, C-array -- doesn't force caller to use a specific container
void Process(std::span<const Item> items);  // Better than const std::vector<Item>&
```

## Principle 5: Predictable Lifecycle

Objects should have clear construction, usage, and destruction phases.

```cpp
// Good: RAII -- ready to use after construction
class FileHandle
{
public:
    explicit FileHandle(std::string_view path);  // Opens file
    ~FileHandle();                                // Closes file
    // No Init() needed, no Close() needed
};

// Bad: Two-phase initialization -- easy to misuse
class FileHandle
{
public:
    FileHandle();                    // Half-constructed
    bool Init(const char* path);    // Actually initializes
    void Close();                    // Must remember to call
};
```

### Constructor Rules
- **Fail in the constructor** (throw) rather than requiring a separate `Init()`.
- Use `explicit` for single-argument constructors to prevent implicit conversions.
- If an object is expensive to construct, provide a factory function.

## Principle 6: Value Semantics When Possible

Prefer value types over pointer/reference types. Values are simpler to reason about.

```cpp
// Good: Value semantics -- no ownership questions
struct Point { double x, y; };
struct Color { uint8_t r, g, b, a; };
struct TimeSpan { std::chrono::milliseconds value; };

Point Midpoint(Point a, Point b);  // Simple, copyable, no lifetime issues
```

### When to Use Pointer/Reference Semantics
- Polymorphism (interface + implementations)
- Large objects that are expensive to copy
- Objects with identity (connections, handles, threads)

## Principle 7: Error Reporting Is Part of the API

How a function reports errors is a design decision, not an afterthought.

```cpp
// Good: Clear error semantics -- caller knows what to expect
[[nodiscard]] std::optional<Value> Find(Key key) const;       // Absence is normal
[[nodiscard]] std::expected<Value, Error> Parse(string_view s); // Failure carries info (C++23)
void Insert(Key key, Value value);                             // Throws on failure
```

### Use `[[nodiscard]]` for Functions Where Ignoring the Return Is a Bug

```cpp
[[nodiscard]] bool TryAcquire();
[[nodiscard]] ErrorCode Initialize();
[[nodiscard]] std::unique_ptr<T> Create();
```

## Principle 8: Symmetry and Consistency

APIs should be predictable. If `Open` exists, `Close` should exist. If `Begin` exists, `End` should exist.

```cpp
// Good: Symmetric pairs
void Lock() / void Unlock();
void Subscribe() / void Unsubscribe();
Iterator Begin() / Iterator End();

// Good: Consistent naming within a class
size_t Count() const;     // Not Size() in one place and Count() in another
bool IsEmpty() const;      // Not IsEmpty() here and HasItems() there
```

## Key Rules

1. **Make misuse a compile error** -- strong types, enum class, explicit constructors.
2. **Minimal surface area** -- expose only what's needed; private by default.
3. **Ownership in the signature** -- `unique_ptr` = transfer, raw ptr = borrow, `const&` = read.
4. **`const` everything** that doesn't need to mutate.
5. **`std::span`** for contiguous range parameters -- don't force a specific container on callers.
6. **RAII lifecycle** -- ready after construction, clean up in destructor.
7. **Value semantics when possible** -- simpler to reason about than pointers.
8. **`std::expected`** for operations that can fail with diagnostic info (C++23).
9. **`[[nodiscard]]`** for functions where ignoring the return is a bug.
10. **Symmetry** -- consistent naming, predictable pairs.
