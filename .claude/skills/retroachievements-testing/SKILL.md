---
name: retroachievements-testing
description: Validate RetroAchievements memory condition evaluation. Use when modifying rcheevos integration, achievement parsing, or memory peek callbacks.
---

# RetroAchievements Testing

Validate native RetroAchievements (rcheevos) memory condition evaluation.

---

## When to Use This Skill

**Automatically invoke:**
- After modifying `achievements.cpp` or `achievements.h`
- After changing the `peekMemory` callback
- After updating rcheevos library version
- Before releasing builds with RA changes

**Manual invocation when:**
- Debugging achievement trigger failures
- Adding support for new memory access patterns
- Investigating false positives/negatives in achievements

---

## Test Location

```
libretrodroid/src/main/cpp/
├── achievements_test.h      # Test harness header
├── achievements_test.cpp    # Test implementation (52 test cases)
└── tests/
    ├── CMakeLists.txt       # Host build config
    ├── log_host.h           # printf-based logging for host
    ├── test_runner.cpp      # Main entry point
    └── build/               # Build output (gitignored)
```

---

## Running Tests

### Host (macOS) - Fast Iteration

```bash
cd libretrodroid/src/main/cpp/tests/build
make && ./achievement_tests
```

Build time: ~3 seconds
Use for: Development iteration, debugging

### Android Device - Full Integration

**WARNING**: `connectedAndroidTest` UNINSTALLS the app under test, wiping its on-device data. It requires explicit user approval - never run it unprompted.

```bash
./gradlew :libretrodroid:connectedAndroidTest
```

Build time: ~30 seconds
Use for: Final validation before release (with user approval)

---

## Test Categories

### 1. Basic Memory Reads (10 tests)
Verify correct byte sizes and little-endian ordering.

| Test | Condition | Description |
|------|-----------|-------------|
| 8-bit read | `0xH<addr>=<val>` | Single byte |
| 16-bit read | `0x <addr>=<val>` | Little-endian 16-bit |
| 32-bit read | `0xX<addr>=<val>` | Little-endian 32-bit |
| Endian verify | Manual byte writes | Confirm LE ordering |

### 2. Comparison Operators (8 tests)
All rcheevos comparison operators.

| Operator | Syntax | Example |
|----------|--------|---------|
| Equals | `=` | `0xH0100=50` |
| Not equals | `!=` | `0xH0101!=0` |
| Less than | `<` | `0xH0102<100` |
| Less or equal | `<=` | `0xH0103<=100` |
| Greater than | `>` | `0xH0105>100` |
| Greater or equal | `>=` | `0xH0106>=100` |

### 3. Failure Cases (9 tests)
Verify conditions correctly reject invalid states.

- Off-by-one errors (too low, too high)
- Boundary conditions (equal when expecting less/greater)
- Partial byte matches (16-bit/32-bit with wrong bytes)

### 4. Delta/Prior Values (5 tests)
Track value changes between frames.

| Pattern | Syntax | Description |
|---------|--------|-------------|
| Increased | `0xH<addr>>d0xH<addr>` | Current > previous |
| Decreased | `0xH<addr><d0xH<addr>` | Current < previous |
| Was X, now Y | `d0xH<addr>=X_0xH<addr>=Y` | Specific transition |

### 5. Compound Conditions (9 tests)
AND/OR logic combinations.

| Pattern | Syntax | Description |
|---------|--------|-------------|
| AND | `cond1_cond2` | Both must be true |
| OR (alt groups) | `Scond1Scond2` | Either can be true |

### 6. Bit Operations (5 tests)
Individual bit and nibble checks.

| Prefix | Bit | Example |
|--------|-----|---------|
| `0xM` | Bit 0 | `0xM0500=1` |
| `0xN` | Bit 1 | `0xN0500=1` |
| `0xO` | Bit 2 | ... |
| `0xP` | Bit 3 | ... |
| `0xQ` | Bit 4 | ... |
| `0xR` | Bit 5 | ... |
| `0xS` | Bit 6 | ... |
| `0xT` | Bit 7 | `0xT0501=1` |
| `0xL` | Lower nibble | `0xL0503=15` |
| `0xU` | Upper nibble | `0xU0504=15` |

### 7. Memory-to-Memory (3 tests)
Compare values at two addresses.

```
0xH0600=0xH0601    # addr1 == addr2
0xH0604>0xH0605    # addr1 > addr2
```

### 8. Edge Cases (3 tests)
Boundary and special conditions.

- High addresses (0xFF00+)
- 16-bit reads spanning two distinct bytes
- Value transitions (255 -> 0)

(The address-0x0000 test lives in the basic memory reads category.)

---

## Adding New Tests

Edit `achievements_test.cpp` and add to `getStandardTestCases()`:

```cpp
{
    "Test name - description",
    "0xH0800=123",  // rcheevos condition string
    [](TestMemory& m) { m.write8(0x0800, 0); },      // setup (initial state)
    [](TestMemory& m) { m.write8(0x0800, 123); },    // trigger (change state)
    true  // expectTrigger: true = should trigger, false = should NOT trigger
},
```

### TestMemory API

```cpp
m.write8(addr, value)   // Write 1 byte
m.write16(addr, value)  // Write 2 bytes (little-endian)
m.write32(addr, value)  // Write 4 bytes (little-endian)
m.peek(addr, numBytes)  // Read bytes (used by rcheevos callback)
```

---

## rcheevos Condition Syntax Reference

### Memory Size Prefixes

| Prefix | Size | Example |
|--------|------|---------|
| `0xH` | 8-bit | `0xH1234` |
| `0x ` | 16-bit | `0x 1234` |
| `0xX` | 32-bit | `0xX1234` |
| `0xM`-`0xT` | Single bit | `0xM1234` (bit 0) |
| `0xL` | Lower nibble | `0xL1234` |
| `0xU` | Upper nibble | `0xU1234` |

### Operators

| Op | Meaning |
|----|---------|
| `=` | Equals |
| `!=` | Not equals |
| `<` | Less than |
| `<=` | Less or equal |
| `>` | Greater than |
| `>=` | Greater or equal |

### Modifiers

| Prefix | Meaning |
|--------|---------|
| `d` | Delta (previous frame value) |
| `p` | Prior (value before delta) |

### Combinators

| Syntax | Meaning |
|--------|---------|
| `_` | AND (both conditions) |
| `S` | Start new alt group (OR) |

---

## Debugging Failed Tests

### 1. Check condition syntax
Use [RATools](https://github.com/Jamiras/RATools) or RA documentation to validate syntax.

### 2. Add verbose logging
Modify `testEventCallback` to log all events:

```cpp
static void testEventCallback(const rc_runtime_event_t* event) {
    LOGI("Event type=%d, id=%u, value=%u", event->type, event->id, event->value);
    if (event->type == RC_RUNTIME_EVENT_ACHIEVEMENT_TRIGGERED) {
        g_testTriggered = true;
    }
}
```

### 3. Verify memory contents
For host tests, add debug output in `testPeekCallback` (achievements_test.cpp):

```cpp
uint32_t value = activeMemory->peek(addr, numBytes);
LOGD("peek(0x%04X, %u) = %u", addr, numBytes, value);
return value;
```

Production `peekMemory` (achievements.cpp) already logs the first 5 peeks on its own - check logcat before adding logging there.

### 4. Check little-endian ordering
For multi-byte values, verify byte order:
- 16-bit 0x1234 stored as [0x34, 0x12]
- 32-bit 0x12345678 stored as [0x78, 0x56, 0x34, 0x12]

---

## Common Issues

### Condition parses but never triggers
- Check delta requirements (need multiple frames)
- Verify value ranges (8-bit max 255, etc.)
- Ensure setup state differs from trigger state

### Condition triggers unexpectedly
- Check for accidental matches in setup phase
- Verify comparison direction (< vs >)
- Review alt group logic (S separator)

### Bit operations fail
- Remember: `0xM` = bit 0, `0xT` = bit 7 (not `0xN`!)
- Bit value is always 0 or 1, not the masked result

---

## Instrumented Test (Manual Only)

CI (`.github/workflows/build.yml`) does NOT run `connectedAndroidTest` - it only runs `assembleDebug` and `testDebugUnitTest`. The instrumented test below runs only when invoked manually on a device (with explicit user approval; it uninstalls the app under test - see warning above):

```kotlin
// AchievementNativeTest.kt
@Test
fun runNativeConditionTests() {
    val passed = LibretroDroid.runAchievementTests()
    assertEquals("All native achievement tests should pass", 52, passed)
}
```

Update the expected count (52) when adding new tests.

---

## Quick Reference

```bash
# Host build + run (fast)
cd libretrodroid/src/main/cpp/tests/build && make && ./achievement_tests

# Rebuild from scratch
cd libretrodroid/src/main/cpp/tests/build && cmake .. && make && ./achievement_tests

# Android device test - UNINSTALLS the app under test (wipes its data);
# requires explicit user approval before running
./gradlew :libretrodroid:connectedAndroidTest

# Check logcat for test details (Android)
adb logcat -d | grep -E "(Running test:|PASS|FAIL|Results:)"
```
