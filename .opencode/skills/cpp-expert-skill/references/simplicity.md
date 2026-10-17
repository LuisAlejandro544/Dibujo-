# C++ Code Simplicity & "Good Taste"

These principles define the difference between code that works and code that is **good**. Inspired by Linus Torvalds' philosophy: the best code is the code that doesn't need to exist.

## The Simplicity Hierarchy

When solving a problem, prefer solutions in this order:
1. **Remove the need** -- Can we eliminate the problem instead of solving it?
2. **Use what exists** -- Is there a standard library or existing utility that does this?
3. **Write simple code** -- Straightforward, linear, obvious.
4. **Write clever code** -- Only when simple isn't possible, and ONLY with clear comments explaining why.

## Good Taste: Eliminate Special Cases

The hallmark of good code is treating edge cases as part of the general case.

```cpp
// Bad taste -- special-casing the first/last element
void RemoveFromList(Node* entry)
{
    Node* prev = nullptr;
    Node* curr = head;
    while (curr != entry)
    {
        prev = curr;
        curr = curr->next;
    }
    if (prev)
        prev->next = curr->next;
    else
        head = curr->next;  // Special case: removing head
}

// Good taste -- no special cases
void RemoveFromList(Node* entry)
{
    Node** indirect = &head;
    while (*indirect != entry)
        indirect = &(*indirect)->next;
    *indirect = entry->next;  // Works for ALL positions including head
}
```

The good version uses an **indirect pointer** to eliminate the special case entirely. This is "good taste" -- finding the abstraction that makes special cases vanish.

## Write Code for the Reader

You write code once. It gets read hundreds of times. Optimize for the reader.

```cpp
// Bad: Clever but unreadable
return (n & (n - 1)) == 0 && n > 0;

// Good: Clear intent
bool IsPowerOfTwo(unsigned n)
{
    return n > 0 && (n & (n - 1)) == 0;
}
```

### Function Length
- **Ideal**: 5-15 lines. If it's longer, it's probably doing too much.
- **Maximum**: ~40 lines before you MUST split.
- **Exception**: Switch statements or dispatch tables can be longer.
- Each function should do **one thing** and do it well.

### Nesting Depth
- **Maximum 3 levels** of indentation. If deeper, refactor.
- Use early returns to flatten control flow.

```cpp
// Bad: Too deep
void Process(Request* req)
{
    if (req)
    {
        if (req->IsValid())
        {
            if (auto conn = GetConnection())
            {
                if (conn->IsReady())
                {
                    conn->Send(req);
                }
            }
        }
    }
}

// Good: Flat -- early returns
void Process(Request* req)
{
    if (!req || !req->IsValid())
        return;

    auto conn = GetConnection();
    if (!conn || !conn->IsReady())
        return;

    conn->Send(req);
}
```

## Minimize State

Every variable is a potential source of bugs. Minimize what you track.

```cpp
// Bad: Unnecessary state
bool found = false;
int foundIndex = -1;
for (int i = 0; i < items.size(); i++)
{
    if (items[i].Matches(target))
    {
        found = true;
        foundIndex = i;
        break;
    }
}
if (found) Use(items[foundIndex]);

// Good: Minimal state
auto it = std::find_if(items.begin(), items.end(),
    [&](const auto& item) { return item.Matches(target); });
if (it != items.end()) Use(*it);
```

## Don't Over-Abstract

Abstraction has a cost: indirection. Every layer makes the code harder to understand, debug, and change.

```cpp
// Bad: Over-abstracted -- AbstractSingletonProxyFactoryBean syndrome
auto factory = ServiceLocator::GetInstance()
    ->GetFactory<IConnectionFactory>()
    ->CreateFactory(config);
auto conn = factory->Create();

// Good: Direct -- do what you mean
auto conn = CreateConnection(config);
```

### When Abstraction IS Justified
- When it **removes duplication** across 3+ call sites.
- When it **hides genuine complexity** (not just wrapping a function call).
- When it **enables testing** (interface for dependency injection).
- When the abstraction is **stable** and won't change frequently.

## Comments: WHY, Not WHAT

```cpp
// Bad: Useless -- restates the code
count++;  // Increment count

// Bad: Useless -- describes the obvious
// This function returns true if the user is authenticated
bool IsAuthenticated();

// Good: Useful -- explains WHY
// We retry 3 times because the auth service occasionally returns 503
// during deployments (see incident #4521)
constexpr int c_maxRetries = 3;

// Good: Useful -- explains a non-obvious constraint
// Must be called before Initialize() because the config subsystem
// reads this value during startup and caches it permanently
void SetLogLevel(Level level);
```

## Naming Is Design

Good names eliminate the need for comments.

```cpp
// Bad: Needs a comment to explain
int d;  // elapsed time in days

// Good: Self-documenting
int elapsedDays;

// Bad: Vague
void Process(Data* data);

// Good: Specific
void ValidateAndStoreUserProfile(UserProfile* pProfile);
```

### Name Length Rule
- **Loop variables**: 1 character (`i`, `j`, `it`)
- **Local variables**: 1-2 words (`count`, `nextItem`)
- **Member variables**: 2-3 words (`m_activeConnections`, `m_retryCount`)
- **Functions**: verb + noun, as long as needed for clarity
- **Classes**: noun or noun phrase, as long as needed

## Value Semantics by Default

Types that behave like values -- copyable, comparable, storable in containers without pointer indirection -- are simpler to reason about, test, and compose. Make value semantics your default design choice.

```cpp
// Good: Value types -- no ownership questions, no lifetime issues
struct Point { double x, y; };
struct DateRange { Date start, end; };
struct Money { int64_t cents; Currency currency; };

// Values compose naturally
std::vector<Point> polygon;          // No pointer management
auto center = Midpoint(p1, p2);      // Clear semantics
```

### When to Choose Reference Semantics
- Polymorphism (interface + implementations) -- use `unique_ptr<IBase>`
- Objects with identity (connections, threads, sessions)
- Very large objects where copying is prohibitively expensive

**Test**: If you can meaningfully compare two instances for equality by their contents, it should probably be a value type.

## Delete Code Ruthlessly

Dead code is worse than no code. It misleads readers, causes merge conflicts, and rots.

- If code is commented out -- **delete it**. Git has history.
- If a function is unused -- **delete it**.
- If a feature is behind a permanently-off flag -- **delete it**.
- If a TODO is older than 6 months and nobody's done it -- **delete it**.

## Key Rules

1. **Eliminate special cases** -- find the abstraction that handles everything uniformly.
2. **Value semantics by default** -- types that behave like values are simpler to reason about.
3. **Write for the reader** -- optimize for comprehension, not keystrokes.
4. **Functions: 5-15 lines, one purpose.** Max nesting depth: 3.
5. **Early returns** -- flatten control flow, don't nest.
6. **Minimize state** -- every variable is a liability.
7. **Don't over-abstract** -- abstraction must earn its indirection cost.
8. **Comments explain WHY, not WHAT.**
9. **Names are design** -- if the name is good, the comment is unnecessary.
10. **Delete dead code** -- commented-out code, unused functions, stale TODOs.
