Integrate Kestra with the [Ashby recruiting platform](https://developers.ashbyhq.com/).
Use tasks to sync recruiting records and triggers to react to hires, publication changes, or signed webhooks.

## Prerequisites

Create an Ashby API key with read permissions for the resources you access. Store it in Kestra
Secrets as `ASHBY_API_KEY`. Requests use HTTP Basic authentication with the key as username and
an empty password. The default `baseUrl` is `https://api.ashbyhq.com`.

## Tasks

| Task | Endpoint | Typed inputs |
| --- | --- | --- |
| `jobpostings.List` | `jobPosting.list` | `listedOnly`, `includeUnpublishedJobPostings`, `location`, `department` |
| `jobs.List` | `job.list` | `status` (array), `syncToken` |
| `openings.List` | `opening.list` | `createdAfter`, `syncToken` |
| `applications.List` | `application.list` | `jobId`, `status`, `createdAfter`, `syncToken` |
| `candidates.List` | `candidate.list` | `syncToken` |
| `offers.List` | `offer.list` | `offerStatus` (array), `acceptanceStatus` (array), `applicationId`, `syncToken` |
| `directory.Departments` / `Locations` / `Users` | `department.list` / `location.list` / `user.list` | Optional advanced `filters` |
| `interviews.Schedules` | `interviewSchedule.list` | `applicationId`, `interviewStageId`, `createdAfter`, `syncToken` |

Prefix all task names with `io.kestra.plugin.ashby.`. Optional typed filters are omitted by default.
`createdAfter` uses Unix epoch milliseconds. Location and department filters use case-sensitive
names as specified by Ashby's [job posting API](https://developers.ashbyhq.com/reference/jobpostinglist).
Use `offerStatus` rather than `status` for [offers](https://developers.ashbyhq.com/reference/offerlist).
The [interview schedule API](https://developers.ashbyhq.com/reference/interviewschedulelist) accepts
`createdAfter`, rather than `dateFrom`/`dateTo`. Additional supported API fields can be passed in
`filters`; typed properties take precedence. Cursors are managed automatically.

Every list task supports `fetchType`:

- `STORE` (default): stream all pages into an Ion file in internal storage; return `uri` and `size`.
- `FETCH`: return all records in `rows` and their count in `size`; uses memory proportional to results.
- `FETCH_ONE`: return the first record in `row` and size 0 or 1; stop once found.
- `NONE`: return only `size`, consuming all pages without retaining records.

```yaml
id: ashby_job_postings
namespace: company.people
tasks:
  - id: postings
    type: io.kestra.plugin.ashby.jobpostings.List
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    listedOnly: true
    fetchType: STORE
```

The six Info tasks require their endpoint-specific ID: `jobpostings.Info.jobPostingId`,
`jobs.Info.jobId`, `openings.Info.openingId`, `applications.Info.applicationId`,
`candidates.Info.candidateId`, and `offers.Info.offerId`. The output `result` contains only the
returned record, without the `success` response envelope.

```yaml
id: ashby_application
namespace: company.people
tasks:
  - id: application
    type: io.kestra.plugin.ashby.applications.Info
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    applicationId: "00000000-0000-0000-0000-000000000001"
```

Tasks and polling triggers retry HTTP 429 and 5xx responses up to four total attempts.
`Retry-After` accepts seconds or an HTTP date; without a valid header the delays are 1, 2,
and 4 seconds. Other HTTP errors fail immediately. Empty bodies, API errors, missing cursors,
and cursor cycles fail clearly.

## Triggers

`triggers.CandidateHiredTrigger` follows all `application.list` pages with the previous sync token,
then saves only the final token and current hired application IDs. The first successful poll seeds
state without firing. Later polls return newly hired applications in `trigger.events` and the final
`trigger.syncToken`. Editing an already hired application does not fire again. An application that
leaves Hired and later returns to Hired can fire again.

`triggers.JobPostingTrigger` compares snapshots of listed, published job postings. `event` accepts
`PUBLISHED` (default) or `UNPUBLISHED`. Published events include newly listed postings and changed
`publishedDate` values; unpublished events contain the previous posting when it disappears or
becomes unlisted. The first poll seeds state. Events are in `trigger.events`; this endpoint has
no incremental sync token.

Both polling triggers default to `interval: PT15M`, batch all matching records into one execution,
and store state in the flow namespace's KV Store under a length-prefixed flow/trigger key.
An incomplete or failed poll does not advance state. Deleting that KV entry causes a new baseline
on the next successful poll. Polling can miss changes that occur and revert between polls;
use webhooks when each individual event is required.

```yaml
id: ashby_on_hire
namespace: company.people
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: "Newly hired applications: {{ trigger.events }}"
triggers:
  - id: hired
    type: io.kestra.plugin.ashby.triggers.CandidateHiredTrigger
    apiKey: "{{ secret('ASHBY_API_KEY') }}"
    interval: PT15M
```

`triggers.Webhook` requires `key` and `signingSecret`. Configure the secret token in Ashby and point
the webhook to Kestra's `/api/v1/{tenant}/executions/webhook/{namespace}/{flowId}/{key}` endpoint.
The required `Ashby-Signature` header is `sha256=` followed by the lowercase hexadecimal
HMAC-SHA256 digest of the raw body. Invalid signatures return 401. Server failures return 500
and produce a warning without logging the payload or signature. After signature validation,
Kestra's built-in webhook implementation handles conditions, inputs, and execution submission,
providing compatibility with Kestra 1.3 and 2.0. The payload is in `trigger.body`, with
`trigger.headers` and `trigger.parameters` also available.

```yaml
id: ashby_signed_webhook
namespace: company.people
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: "Received Ashby event {{ trigger.body.action }}"
triggers:
  - id: ashby
    type: io.kestra.plugin.ashby.triggers.Webhook
    key: "{{ secret('ASHBY_WEBHOOK_KEY') }}"
    signingSecret: "{{ secret('ASHBY_SIGNING_SECRET') }}"
```
