---
name: cpp-expert-skill
description: Write, review, or improve C++ code following best practices for memory management, concurrency, performance, error handling, API design, and modern C++ idioms (C++17/20/23). Use when building new C++ features, refactoring existing code, reviewing code quality, or adopting modern C++ patterns. Aligned with the C++ Core Guidelines (Stroustrup & Sutter).
---

# C++ Expert Skill

## Operating Rules

- Follow the [C++ Core Guidelines](https://isocpp.github.io/CppCoreGuidelines/) (Stroustrup & Sutter) as the authoritative reference
- Consult `references/style.md` at the start of every task for naming and formatting conventions
- Prefer modern C++ (C++17/20/23) idioms over legacy patterns
- Focus on type safety, resource safety, and performance; present optimizations as suggestions unless they address correctness
- Zero-overhead principle: choose abstractions the compiler can eliminate entirely
- Default to `std::unique_ptr` for ownership; raw pointers are non-owning
- RAII for all resource management — locks, handles, state restoration
- Rule of Zero: prefer compiler-generated special members; Rule of Five if you must define any
- Encourage simplicity: remove the need > use existing > write simple > write clever
- Use `enum class` exclusively, never plain `enum`
- Error paths should be flat — early returns, not deep nesting

## Task Workflow

### Review existing C++ code
- Read the code under review and identify which topics apply
- Run the Topic Router below for each relevant topic
- Check for common issues: raw owning pointers, manual lock/unlock, reference captures in async lambdas
- Validate naming conventions against `references/style.md`

### Improve existing C++ code
- Audit current implementation against the Topic Router topics
- Replace legacy patterns with modern C++ equivalents
- Flatten deeply nested control flow with early returns
- Extract long functions (>40 lines) into focused sub-functions
- Suggest performance improvements as optional optimizations (see `references/performance.md`)

### Implement new C++ feature
- Design ownership and data flow first: identify who owns what
- Choose the right data structures (see `references/performance.md` container selection)
- Apply RAII for all resource management
- Design clean APIs with strong types and minimal surface area (see `references/api-design.md`)
- Write tests using the Arrange-Act-Assert pattern (see `references/testing.md`)

### Topic Router

Consult the reference file for each topic relevant to the current task:

| Topic | Reference |
|-------|-----------|
| Naming & formatting | `references/style.md` |
| Memory & ownership | `references/memory.md` |
| Concurrency & threading | `references/concurrency.md` |
| Error handling | `references/error-handling.md` |
| Performance | `references/performance.md` |
| Design patterns | `references/patterns.md` |
| API design | `references/api-design.md` |
| Testing | `references/testing.md` |
| Simplicity & taste | `references/simplicity.md` |

## Correctness Checklist

These are hard rules -- violations are always bugs:

- [ ] Every heap-allocated object has exactly one clear owner
- [ ] `std::unique_ptr` for exclusive ownership; raw pointers are non-owning only
- [ ] Rule of Zero: no custom destructor/copy/move unless managing a raw resource; Rule of Five if any are defined
- [ ] RAII for all locks — never manual `lock()`/`unlock()`; `std::scoped_lock` for multiple mutexes
- [ ] No reference captures (`[&]`) in async/queued/stored lambdas
- [ ] Move constructors and move assignment are `noexcept` (required for `std::vector` optimization)
- [ ] `enum class` used exclusively, never plain `enum`
- [ ] Exceptions caught by `const` reference, never by value
- [ ] Destructors are `noexcept`
- [ ] `#pragma once` in all header files
- [ ] `explicit` on single-argument constructors
- [ ] `[[nodiscard]]` on functions where ignoring the return is a bug

## References

- `references/style.md` -- **Read first for every task.** Naming conventions, formatting, and code organization
- `references/memory.md` -- RAII, ownership semantics, smart pointer selection
- `references/concurrency.md` -- Threading, synchronization, async patterns, lambda capture rules
- `references/error-handling.md` -- Assertions, exceptions, defensive coding, return value patterns
- `references/performance.md` -- Data-oriented design, cache awareness, allocation discipline
- `references/patterns.md` -- Design patterns and modern C++ idioms (C++17/20)
- `references/api-design.md` -- Clean interfaces, minimal surface area, type safety
- `references/testing.md` -- Unit testing patterns, mock conventions, test data builders
- `references/simplicity.md` -- Code quality principles, readability, "good taste"
