# Yu Sutong — AI-assisted development reflection notes

Status: personal notes for later synthesis into `docs/Reflections.md`  
Human verification: **Approved by `yu-sutong` on 19 September 2026**

## Session: Manager foundation, 18 September 2026

### What I asked the AI to help with

I identified myself as the Facilities Manager owner and asked the AI to follow
the project instructions, decide what should be built next, and keep reflection
and decision material separate.

### What the AI contributed

The AI selected the Manager assignment vertical slice, mapped it to the approved
requirement IDs, created the Java 25/Gradle/JavaFX foundation, separated model,
service, storage, authentication, and UI packages, implemented SQLite
transaction handling, added the Manager queue/detail/assignment view, and wrote
automated tests.

It also applied a UI-design guidance skill. The useful parts were JavaFX-specific:
keyboard mnemonics, explicit labels, form grouping, visible focus, non-colour
feedback, and a layout sized for 1,024 × 700. A generic suggestion for a highly
vibrant operations style was not followed literally because a restrained,
information-dense facilities interface better matches the product and course
scope.

### Corrections and verification

The first test run compiled the code but Gradle 9 could not start JUnit because
the JUnit Platform launcher was absent from the runtime classpath. The AI added
the explicit launcher dependency and reran the tests. The next `./gradlew test`
passed, and the later `./gradlew check` passed compilation, nine tests,
Checkstyle, and JaCoCo report generation.

The SQLite rollback test was especially useful: it injected a database trigger
failure between the request update and audit insert and verified that neither
change remained. This is stronger evidence for LIF-012 than a mock-only test.

### What I still need to assess personally

- Whether the shared transaction interface is clear enough for my teammates'
  Requester and Technician slices.
- Whether the Manager queue layout and wording match our eventual shared visual
  language after it is manually launched through authentication.
- Whether the requirement-to-test coverage is sufficient before the pull
  request is merged.
- How much of this generated draft reflects my actual learning; I should replace
  generic statements with specific observations after reviewing the diff.

### Possible agent improvement

The agent could have detected the Gradle 9 JUnit launcher requirement before the
first test run. A future JavaFX project-foundation skill should also distinguish
between generic web-style design recommendations and desktop enterprise UI
needs earlier, and should include a checklist for authentication-safe previewing
of role screens.
