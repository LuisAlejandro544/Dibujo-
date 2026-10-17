# C++ Error Handling

## Error Strategy Hierarchy

| Mechanism | When to Use |
|-----------|------------|
| Crash assertions | Invariant violations = bugs. System is in broken state. |
| Exceptions | Recoverable failures at API boundaries -- I/O, network, bad input |
| `std::expected<T, E>` (C++23) | Operation that can fail with diagnostic info |
| `std::optional<T>` | Values that may legitimately be absent |
| `[[nodiscard]]` returns | Functions whose return value must not be ignored |

## Assertions

### Production Crash-Assertions
For conditions that **must** be true -- crash with diagnostics if violated:

```cpp
VerifyElseCrash(ptr != nullptr, DiagTag(0x1e291714));

VerifyElseCrash(Implies(allowedTargets > 0, pTarget != nullptr),
    DiagTag(0x1e3cc08b), { "id", id }, { "targets", count });

~Pool() noexcept { VerifyElseCrash(m_active == 0, DiagTag(0x1e1451c3)); }
```

### Debug Assertions
"Shouldn't happen, but we can survive":

```cpp
try { path = GetDirectory(); }
catch (const Exception&) { Assert(false, tag); }
return path;  // Empty on failure -- acceptable
```

## Exception Handling

### Catch-Log-Rethrow (Standard Boundary Pattern)
```cpp
try { ProcessTask(std::move(data)); }
catch (const AppException& ex) { Log(ex.Tag(), ex.Details()); throw; }
catch (const std::bad_alloc&) { throw; }  // Always rethrow OOM
catch (const std::exception& ex) { Log(ex.what()); throw; }
catch (...) { Log("unknown"); throw; }
```

### Anti-Patterns
```cpp
catch (...) { }                     // Bad: NEVER swallow silently
catch (std::exception ex) { }      // Bad: Slices! Use const ref
try { return c.at(k); } catch { }  // Bad: Don't use exceptions for control flow
```

## Exception Safety Guarantees

Every function provides one of three guarantees (Sutter). Know which one you're providing:

| Guarantee | Promise | How to Achieve |
|-----------|---------|----------------|
| **Nothrow** | Never throws. Mark `noexcept`. | Destructors, move operations, swap, cleanup |
| **Strong** | If it throws, state is unchanged (commit-or-rollback). | Copy-then-swap idiom |
| **Basic** | If it throws, no leaks and invariants hold, but state may have changed. | RAII for all resources |

```cpp
// Nothrow: destructors, moves, swap
~Connection() noexcept;
Connection(Connection&& other) noexcept;
void swap(Connection& a, Connection& b) noexcept;

// Strong: copy-then-swap for commit-or-rollback
Widget& Widget::operator=(const Widget& other)
{
    Widget temp(other);   // Copy (may throw -- but hasn't changed *this yet)
    swap(*this, temp);    // Swap (noexcept -- commits the change)
    return *this;         // temp's destructor cleans up old state
}
```

**Rule**: Every function has a guarantee. If you haven't thought about which one, it's probably basic at best.

## `noexcept` Guidance

Mark functions `noexcept` when they genuinely cannot throw:

- **Always**: Destructors, move constructors, move assignment, swap
- **Consider**: Simple getters, comparison operators, hash functions
- **Never**: Functions that allocate, do I/O, or call unknown code

**Critical performance implication**: `std::vector` will only move elements during reallocation if the move constructor is `noexcept`. Without it, vector silently falls back to copying for exception safety.

```cpp
// Without noexcept: vector copies during reallocation (slow)
MyType(MyType&& other);

// With noexcept: vector moves during reallocation (fast)
MyType(MyType&& other) noexcept;
```

## `std::expected<T, E>` (C++23)

For operations that can fail with diagnostic information, prefer `std::expected` over custom error types or out-parameters:

```cpp
// Good: Caller gets value or error with full context
[[nodiscard]] std::expected<Config, ParseError> ParseConfig(std::string_view input);

auto result = ParseConfig(text);
if (result) {
    UseConfig(*result);
} else {
    Log(result.error().message);
}
```

For C++17/20 codebases without `std::expected`, use a project-local `Expected<T, E>` or a library like `tl::expected`.

## Error Path Design (Linus Philosophy)

Error paths should be **as clean as success paths**. Flat, not nested:

```cpp
// Bad -- deeply nested
if (auto c = Connect()) {
    if (auto s = c->Open()) {
        if (auto d = s->Read()) { return Transform(d); }}}

// Good -- early returns, flat
auto c = Connect();   if (!c) return Error("connect");
auto s = c->Open();   if (!s) return Error("open");
auto d = s->Read();   if (!d) return Error("read");
return Transform(d);
```

## Key Rules

1. **Crash-assert for invariants**, exceptions for recoverable, `std::expected` for failures with info, optional for absence.
2. **Know your exception safety guarantee** -- nothrow, strong, or basic. Every function has one.
3. **Mark `noexcept`** on destructors, move operations, and swap -- required for correctness and performance.
4. **Include diagnostic tags** in every assert -- make crashes investigable.
5. **Catch by `const ref`**, in order of specificity.
6. **Always rethrow after logging** at boundaries.
7. **Error paths should be flat** -- early returns, not deep nesting.
8. **Don't guard impossible scenarios** -- trust control flow guarantees.
9. **Destructors: `noexcept`** with crash-asserts for cleanup validation.
