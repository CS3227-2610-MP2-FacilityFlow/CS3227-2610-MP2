# FacilityFlow

FacilityFlow is a Java 25/JavaFX desktop application for managing facilities
maintenance requests in a formal organization. Requesters report problems,
Technicians perform assigned work, and a Facilities Manager triages and closes
requests through an auditable workflow.

## Current status

The Requester branch contains a development form preview and a Java 25/JavaFX
build/test scaffold. The preview validates fields but does not log in, save
requests, or implement the complete workflow. Shared integration decisions
still require team review.

The merged Facilities Manager foundation adds an authorized, transactional
`OPEN`-request assignment service, SQLite persistence, automated tests, and an
injectable queue/detail view. Shared login, demo seeding, and the complete
cross-role workflow remain unfinished.

## Run the Requester starter

Install a JDK 25 and configure `JAVA_HOME` to its installation directory. From
the repository root in PowerShell:

```powershell
./gradlew.bat test
./gradlew.bat check
./gradlew.bat run
```

The wrapper downloads Gradle and dependencies on first use; no separate Gradle
or JavaFX SDK installation is needed. On macOS/Linux use `sh ./gradlew` in place
of `./gradlew.bat`. Linux UI tests need a display; CI uses `xvfb-run`.
See [Requester preparation](docs/RequesterPreparation.md) for the next tasks and
the [Developer Guide](docs/DeveloperGuide.md) for the file map and setup details.

## Product roles

- **Requester:** submits and follows their own maintenance requests.
- **Technician:** works on assigned requests and records progress.
- **Facilities Manager:** triages, assigns, reviews, and administers the system.

## Specifications

Start with [`specs/README.md`](specs/README.md). It explains the source-of-truth
rules, requirement identifiers, and the complete specification index.

Repository-level guidance for AI agents and contributors is in [`AGENTS.md`](AGENTS.md).

## Pull Request Reviews with PR-Agent

[PR-Agent](https://github.com/the-pr-agent/pr-agent) provides AI-assisted
reviews, change descriptions, and improvement suggestions. The workflow in
[`.github/workflows/pr-agent.yml`](.github/workflows/pr-agent.yml) automatically
reviews and describes pull requests when opened, reopened, updated with new
commits, or marked ready for review. Bot-generated events are skipped.
[`.pr_agent.toml`](.pr_agent.toml) sets FacilityFlow-specific review priorities.
Descriptions are posted as comments; automatic improvement runs and automatic
approval are disabled. Suggestions require human review and application.

To enable it, a repository administrator must open **Settings > Secrets and
variables > Actions > New repository secret**, name the secret `OPENAI_KEY`,
paste the OpenAI API key as its value, and save it. `GITHUB_TOKEN` is provided
automatically by GitHub Actions; do not create a personal token for this setup.
Ensure GitHub Actions and `the-pr-agent/pr-agent@main` are permitted by the
repository and organization policies.

To create a pull request, create a topic branch from `master`, make and verify
your changes, then commit and push that branch. On GitHub, select **Pull requests
> New pull request**, choose `master` as the base and your branch as the compare
branch, describe the changes and relevant requirement IDs, and select **Create
pull request**. Request a teammate's review; AI feedback does not replace it.

Post any of these commands as a comment in the pull request conversation:

| Command | Purpose |
|---|---|
| `/review` | Request a review of the current changes. |
| `/describe` | Generate a change description as a comment. |
| `/improve` | Request code improvement suggestions for human consideration. |
| `/ask "question"` | Ask a question about the pull request. |

Comment commands require the workflow to be present on the default branch
(`master`). Ordinary issue comments are skipped. Automatic runs on fork pull
requests cannot access `OPENAI_KEY` because GitHub withholds repository secrets
from those `pull_request` events; use a branch in this repository for initial
verification.

For failures, open **Actions > PR-Agent**, select the failed run, then open the
`pr-agent` job and its step logs. Check for a missing `OPENAI_KEY`, API billing
or model-access errors, rate limits, or organization token-permission policies.
If feedback is missing despite a green run, inspect the logs as well, since
upstream tools may log errors without failing the job.

**Never commit API keys or other secrets**, including in configuration files,
examples, screenshots, or logs. Store the API key only in GitHub Actions secrets.
The setup follows the upstream
[GitHub Actions installation guide](https://docs.pr-agent.ai/installation/github/).

## Team

- `ngkhengyang` — Technician
- `yooplo` — Requester
- `yu-sutong` — Facilities Manager

Role assignments were agreed by the team and confirmed on 14 September 2026.
See the [Requester preparation checklist](docs/RequesterPreparation.md) for
`yooplo`'s scope, shared dependencies, and suggested first pull requests.
