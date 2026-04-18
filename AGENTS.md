# AGENTS.md

## Scope
These instructions apply to the entire repository.

## Project Summary
- This repository contains the Gradle plugin `org.danilopianini.publish-on-central`.
- Main implementation lives under `src/main/kotlin/org/danilopianini/gradle/mavencentral`.
- The codebase is Kotlin-based and built with Gradle Kotlin DSL.
- The plugin targets Maven Central Portal publishing. For historical support boundaries and current publishing behavior, check `README.md` and the Gradle build files instead of assuming old Nexus/OSSRH flows still apply.

## Repository Layout
- Plugin entry point: `src/main/kotlin/org/danilopianini/gradle/mavencentral/PublishOnCentral.kt`
- Portal-specific publishing code: `src/main/kotlin/org/danilopianini/gradle/mavencentral/portal`
- Custom tasks: `src/main/kotlin/org/danilopianini/gradle/mavencentral/tasks`
- Main automated tests: `src/test/kotlin/org/danilopianini/gradle/test/Tests.kt`
- Test fixtures for Gradle TestKit scenarios: `src/test/resources/org/danilopianini/gradle/test/**`
- CI workflows: `.github/workflows`

## Working Rules
- Preserve the current Gradle Kotlin DSL style and existing naming patterns.
- Keep changes narrowly scoped. Avoid broad refactors unless the task requires them.
- Do not edit generated build output under `build/`.
- Assume the working tree may contain user changes. Never revert unrelated modifications.
- Prefer ASCII in new files unless the file already uses non-ASCII content.

## Build And Test
- Preferred validation command after changes: `./gradlew build`
- Use `./gradlew build` as the default check that the project still works after modifications.
- If you change build logic, plugin wiring, publication setup, or task registration, consider running additional focused verification commands only if `build` is insufficient to cover the change.
- Tests are primarily Gradle TestKit integration tests driven by fixture projects under `src/test/resources/org/danilopianini/gradle/test/`.
- When adding coverage for a new scenario, prefer adding or extending a fixture project plus a TestKit-backed test instead of only adding a narrow unit test.
- Test output is intentionally verbose; do not "simplify" logging unless explicitly requested.

## Toolchain Expectations
- Java toolchains are managed via Gradle. Compilation is configured for Java 17.
- Java, Gradle plugin, and other dependency versions should be retrieved from `build.gradle.kts`, `settings.gradle.kts`, and the Gradle version catalog files under `gradle/`.
- Node and npm version requirements should be retrieved from `package.json`.
- The repository uses pre-commit hooks configured in `settings.gradle.kts`:
  - commit messages must follow conventional commits
  - `ktlintCheck` and `detekt` run in pre-commit

## Release And Publishing
- Releases are driven by `semantic-release` via `release.config.mjs`.
- Publishing includes:
  - Gradle Plugin Portal via `publishPlugins`
  - Maven Central Portal via `publishAllPublicationsToProjectLocalRepository`, `zipMavenCentralPortalPublication`, and `releaseMavenCentralPortalPublication`
  - GitHub Packages as a secondary target
- Do not change release automation, tags, or publishing credentials flow unless the task is specifically about release infrastructure.
- Publishing and signing rely on environment variables or Gradle properties; never hardcode secrets.

## Change Guidance
- For plugin behavior changes, update both implementation and the relevant fixture-based tests.
- For documentation changes affecting usage or publishing flow, keep `README.md` aligned with the actual tasks and credential names.
- If changing task names, publication names, or credential lookup behavior, treat that as a breaking or potentially breaking change and verify all references in code, tests, README, and workflows.

## Things To Check Before Finishing
- Relevant Gradle tests pass.
- New behavior is covered by tests when practical.
- README and workflow references remain accurate if user-facing behavior changed.
- No accidental edits were made to generated files or unrelated user changes.
