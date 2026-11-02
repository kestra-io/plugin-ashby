# Kestra Ashby Plugin

## What

Provides recruiting tasks and triggers under `io.kestra.plugin.ashby`:

- `jobpostings.List` / `Info`: public job postings.
- `jobs.List` / `Info`: jobs and hiring details.
- `openings.List` / `Info`: headcount openings.
- `applications.List` / `Info`: applications and hiring status.
- `candidates.List` / `Info`: candidate profiles.
- `offers.List` / `Info`: offer records.
- `directory.Departments`, `Locations`, `Users`: organization directory.
- `interviews.Schedules`: interview schedules.
- `triggers.CandidateHiredTrigger`, `JobPostingTrigger`, `Webhook`: recruiting events.

## Why

Recruiting teams can sync Ashby data and react to hires, job publication changes, and signed webhooks in Kestra workflows.

## How

This is a single-module Java 21 plugin. `AshbyClient` shares Kestra HTTP Basic authentication,
bounded retries on 429/5xx, response validation, and cursor checks. `AbstractAshbyConnection`,
`AbstractAshbyList`, and `AbstractAshbyInfo` share task behavior. List tasks stream STORE results
as Ion and expose typed endpoint filters plus an advanced filters map. Info tasks require the
endpoint-specific ID and return the results object.

Polling triggers use tenant-scoped namespace KV state and `TriggerService.generateExecution`.
The first successful poll seeds state without firing. Candidate hires use incremental sync and
track hired IDs; posting changes compare listed-posting snapshots. Webhook validates HMAC-SHA256
before delegating execution handling to the running Kestra version's core webhook implementation.

## Project Structure

- `src/main/java/io/kestra/plugin/ashby/`: shared code and the subpackages above.
- `src/test/java/io/kestra/plugin/ashby/`: WireMock task/trigger tests and webhook evaluation tests.
- `src/main/resources/doc/`: plugin how-to documentation.
- `src/main/resources/metadata/`: subgroup metadata (UTF-8 without BOM).
- `build.gradle`: dependencies and optional `kestraRuntimeVersion` compatibility verification.

## Validation

Use Java 21 and run `./gradlew build`. To test the same compiled plugin with Kestra 2.0,
run `./gradlew test -PkestraRuntimeVersion=2.0.2 -x compileJava -x compileTestJava` after the default build,
with Java 25 available as a Gradle toolchain (or selected in `JAVA_HOME`). The runtime test property
defaults to Java 25; `kestraRuntimeJavaVersion` can override the test launcher version.
All tests use local fixtures; no Ashby credentials are needed.

## Local rules

Base the wording on the implemented packages and classes, not on template README text.
Use Kestra's HTTP client and serializers. Keep API keys and signing secrets out of logs.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://developers.ashbyhq.com/reference
