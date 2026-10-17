# C++ Testing Patterns

## Test Structure

### Naming: `Method_Condition_ExpectedOutcome`
```cpp
TEST_METHOD(Connect_WithInvalidHost_ReturnsError)
TEST_METHOD(ScopeGuard_OnException_RunsCleanup)
```

### Arrange-Act-Assert
```cpp
TESTCLASSEX(RefPtr_Test, UnitTestBase)
{
    TEST_METHOD(MakeRefPtr_ReturnsObjectWithSoleOwnership)
    {
        // Arrange
        // Act
        auto sp = make_refptr<Base>();
        // Assert
        TestAssert::AreEqual(1, sp->m_cRef, L"sole ownership");
    }
};
```

## Test Data Patterns

### Builder/Modifier/Inspector Triple
```cpp
ObjectID id;
{ StoreBuilder s; s.AddObject(&id).SetState(Error); }    // Build
{ StoreModifier s; s.GetObject(id).SetState(Normal); }    // Modify
{ StoreInspector s; Assert(s.GetObject(id).IsNormal()); } // Inspect
```

### Test Data Builders with Defaults
```cpp
static Args MakeArgs(
    std::wstring_view name = L"Test",
    const std::optional<std::wstring_view>& email = L"t@t.com",
    const std::optional<GroupID>& group = std::nullopt);
```

### Mock Objects
```cpp
class MockTask : public ITask {
    TaskQueueId GetQueueId() const override { return m_category; }
    TaskId GetTaskId() const override { return TaskId::Mock; }
};
```

### Feature Flag Overrides
```cpp
auto scope = EnableFeatureForScope(FeatureId::NewBehavior);
auto scope2 = OverrideConfigForScope(FeatureId::MaxRetries, 5);
```

## Test Code Standards

| Allowed in Tests | Don't Over-Engineer |
|---------------------|----------------------|
| Simpler error handling | No RAII wrappers for test helpers |
| Magic numbers for test values | No configurable test utilities |
| Less defensive coding | No abstraction layers in tests |
| Direct member access | No gold-plating infrastructure |

## Key Rules

1. **One act per test.** Separate AAA clearly.
2. **Descriptive names**: `Method_Condition_Outcome`.
3. **Builder/Modifier/Inspector** for stateful tests.
4. **Scope-based feature overrides** -- never mutate global state permanently.
5. **Descriptive assertion messages.**
6. **Don't over-engineer test code.**
