# IDE Bug Prediction Plugin — Implementation Plan

## Overview

The Seer backend exposes two endpoints for local (non-PR) bug prediction. The IDE plugin submits a git diff, then polls for results. Seer clones the repo at the base commit, applies the diff, and runs the full bug prediction pipeline (LLM-powered code analysis). Results are structured predictions with file locations, severity, and suggested fixes.

## API Contract

### Base URL

Production: `https://seer.sentry.io` (or whatever the deployed Seer URL is)
Local dev: `http://localhost:9091`

### 1. Submit diff for analysis

```
POST /v1/automation/codegen/pr-review-local
Content-Type: application/json
```

**Request body:**

```json
{
  "repo": {
    "provider": "github",
    "owner": "getsentry",
    "name": "seer",
    "external_id": "123456",
    "base_commit_sha": "91ddbf36fa7a0c6053fbcfada45642d8e5f55708",
    "organization_id": 1
  },
  "diff": "diff --git a/file.py b/file.py\n--- a/file.py\n+++ b/file.py\n@@ -1,3 +1,4 @@\n line1\n+added_line\n line2\n line3",
  "organization_id": 123,
  "commit_message": "optional commit message for context",
  "user_name": "optional username"
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `repo.provider` | string | yes | `"github"` or `"github_enterprise"` |
| `repo.owner` | string | yes | Repository owner (e.g., `"getsentry"`) |
| `repo.name` | string | yes | Repository name (e.g., `"seer"`) |
| `repo.external_id` | string | yes | External repo ID (GitHub repo ID) |
| `repo.base_commit_sha` | string | yes | The commit SHA to use as base. **Must be pushed to GitHub** — Seer clones at this commit. |
| `repo.organization_id` | int | yes | Sentry organization ID (required for repo validation) |
| `diff` | string | yes | Unified git diff format (output of `git diff`) |
| `organization_id` | int | no | Sentry org ID — enables Sentry issue enrichment for predictions |
| `organization_slug` | string | no | Sentry org slug |
| `user_id` | int | no | Sentry user ID |
| `commit_message` | string | no | Commit message for additional LLM context |
| `user_name` | string | no | Author name for attribution |

**Response (200):**

```json
{
  "run_id": 3,
  "status": "pending"
}
```

### 2. Poll for results

```
GET /v1/automation/codegen/pr-review-local/{run_id}
```

**Response (200):**

```json
{
  "status": "completed",
  "run_id": 3,
  "predictions": [
    {
      "title": "Incorrect Newline Handling for Files Without Trailing Newlines",
      "description": "The `_apply_hunks_to_content` method processes diff hunks by splitting them into lines and then unconditionally appending a newline character...",
      "short_description": "The method `_apply_hunks_to_content` unconditionally adds a newline to all lines, which will incorrectly add a trailing newline to files that originally did not have one.",
      "suggested_fix": "Modify the logic to correctly handle the `\\ No newline at end of file` marker...",
      "encoded_location": "src/seer/automation/codegen/pr_review_local_step.py:265~267",
      "severity": "low"
    }
  ],
  "is_draft": false,
  "diagnostics": {
    "files_analyzed": 12,
    "execution_time_seconds": 201.75
  },
  "error_message": null
}
```

**Status values:**

| Status | Meaning | Action |
|--------|---------|--------|
| `pending` | Queued, not started | Keep polling |
| `in_progress` | LLM pipeline running, may have draft predictions | Keep polling, show drafts if `is_draft: true` |
| `completed` | Done, final predictions available | Show results |
| `errored` | Failed, check `error_message` | Show error |

**Draft predictions (`is_draft` field):**

The response includes an `is_draft` boolean (default `false`). When `is_draft` is `true`, predictions are early/unverified results available while the full pipeline is still running. Draft predictions have `suggested_fix` set to empty string and default `medium` severity. Once the pipeline completes (`status: "completed"`), `is_draft` becomes `false` and predictions are replaced with fully verified results including suggested fixes and accurate severity.

**404** is returned if `run_id` doesn't exist or has wrong type.

### Prediction fields

| Field | Type | Description |
|-------|------|-------------|
| `title` | string | Short title for the bug |
| `description` | string | Full description of the bug and why it's a problem |
| `short_description` | string | One-line summary (max ~30 words) |
| `suggested_fix` | string | How to fix it (max ~100 words) |
| `encoded_location` | string | File and line range: `path/to/file.py:startLine~endLine` |
| `severity` | string | One of: `"low"`, `"medium"`, `"high"`, `"critical"` |

### Parsing `encoded_location`

Format: `<filename>:<start_line>~<end_line>`

Examples:
- `src/file.py:265~267` — file.py, lines 265-267
- `src/file.py:42~42` — file.py, line 42 only
- `src/file.py:42` — file.py, line 42 only (no tilde = single line)

Regex: `(?P<filename>.+):(?P<start_line>\d+)(?:~(?P<end_line>\d+))?`

**Important:** These line numbers refer to the **new version** of the file (after the diff is applied), not the base version.

## Plugin Implementation Plan

### Step 1: Gather inputs from the IDE

The plugin needs to extract from the user's local git repo:

```kotlin
// 1. Parse git remote to get owner/name/provider
val remoteUrl = git("remote get-url origin")
// Parse: git@github.com:owner/name.git or https://github.com/owner/name.git
// → provider="github", owner="owner", name="name"

// 2. Get base commit SHA (must be pushed to remote)
val baseSha = git("merge-base HEAD main")
// Or let the user pick the base branch

// 3. Generate unified diff
val diff = git("diff $baseSha..HEAD")

// 4. Optional: get commit message
val commitMessage = git("log --format=%B $baseSha..HEAD")
```

**Key constraint:** `base_commit_sha` must exist on GitHub. If the user is working on unpushed commits, use the merge-base with the remote tracking branch instead.

### Step 2: Submit and poll

```
1. POST /pr-review-local → get run_id
2. Poll GET /pr-review-local/{run_id} every 2-5 seconds
3. Stop when status is "completed" or "errored"
```

Typical execution time: **60-300 seconds** depending on diff size (limited to 50 files, 500KB diff).

### Step 3: Display results in the IDE

For each prediction:

1. **Parse `encoded_location`** → extract filename and line range
2. **Show notification** → show notification indicating there's some bugs found by Seer
3. **Map to editor** → open/navigate to the file, highlight the line range
4. **Show inline annotation** or gutter icon at the relevant lines
5. **Display details** in a panel/popup:
   - Title + severity badge
   - Short description (inline)
   - Full description (expandable)
   - Suggested fix (expandable)

### Step 4: UX considerations

**When to trigger:**
- Manual trigger via action/menu item ("Run Bug Prediction")
- Optionally on pre-commit or pre-push hook
- Optionally on branch switch or after a batch of saves
- Optionally after 5 seconds of editor/IDE inactivity

**Progress indication:**
- Show a progress indicator while polling (can take 1-5 minutes)
- Show "Analyzing X files..." based on diff size
- Allow cancellation (just stop polling — the backend will time out on its own)

**Severity display:**
- `critical` / `high` — red warning icon, prominent display
- `medium` — yellow warning icon
- `low` — blue info icon

**Empty results:**
- If `predictions` is empty and status is `completed`, show "No issues found" (this is a good outcome)

### Step 5: Configuration

The plugin should let users configure:
- **Seer URL** — defaults to production, configurable for self-hosted
- **Sentry Organization ID** — optional, enables issue-enriched predictions
- **Base branch** — defaults to `main`, configurable (some repos use `master`, `develop`, etc.)
- **Auto-trigger** — whether to run automatically on certain events

### Architecture Notes

- Seer needs the `base_commit_sha` pushed to GitHub because it clones the repo at that commit, then applies the diff on top to create the full working state. The LLM agents then have access to read any file in the repo (not just changed files).
- Without `organization_id`, predictions are purely code-based. With it, the LLM can also search Sentry issues related to the code being changed, producing richer predictions.
- The backend has size limits: max 50 files, 500KB diff, 10,000 lines per file. The plugin should validate these before submitting and show a helpful message if exceeded.
- The `external_id` field on repo is the GitHub numeric repo ID. You can get it from the GitHub API (`GET /repos/{owner}/{name}` → `id` field), or it may be available from the Sentry API as part of the integration config.
