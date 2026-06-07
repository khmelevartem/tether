#!/usr/bin/env python3
"""Collect raw data for the /progress snapshot.

Gathers everything build.py consumes into one directory:

  prs.json          all PRs (paginated GraphQL — REST /pulls omits commits/comments/threads)
  issues.json       all issues, PRs filtered out
  blocked_by.json   { "<issue>": [<blocker>, ...] } via REST dependencies (no GraphQL mutation exists)
  loc.json          lines + files per source set from assets/locations.json
  sprint_cutoff.txt ISO date sprint-01.md first landed in git

owner/repo is resolved from `gh repo view`; LOC/git read --repo-root (default cwd).
MVP chapters and the diary are judged in chat — this script does not touch them.

  python3 collect.py [--raw-data DIR] [--repo-root PATH]
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent

PR_FIELDS = """
            number title state createdAt mergedAt additions deletions changedFiles
            commits(first:100){ totalCount nodes{ commit{ committedDate } } }
            comments{ totalCount } reviews{ totalCount } reviewThreads{ totalCount }
"""

PR_QUERY = """
query($owner:String!,$repo:String!,$cursor:String){
  repository(owner:$owner,name:$repo){
    pullRequests(first:50, after:$cursor, orderBy:{field:CREATED_AT, direction:ASC}){
      pageInfo{ hasNextPage endCursor }
      nodes{ %s }
    }
  }
}""" % PR_FIELDS


def gh(args: list[str]) -> str:
    res = subprocess.run(["gh", *args], capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(f"gh {' '.join(args)} failed:\n{res.stderr}")
    return res.stdout


def resolve_repo() -> tuple[str, str]:
    full = gh(["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"]).strip()
    owner, repo = full.split("/", 1)
    return owner, repo


def fetch_prs(owner: str, repo: str) -> list:
    nodes, cursor = [], ""
    while True:
        cmd = ["api", "graphql", "-f", f"query={PR_QUERY}", "-F", f"owner={owner}", "-F", f"repo={repo}"]
        # GraphQL String! cursor: pass null on the first page via -F (gh maps absent -F to null).
        if cursor:
            cmd += ["-F", f"cursor={cursor}"]
        else:
            cmd += ["-f", "cursor="]  # empty string -> treated as no cursor by the API for the first page
        page = json.loads(gh(cmd))["data"]["repository"]["pullRequests"]
        nodes += page["nodes"]
        if not page["pageInfo"]["hasNextPage"]:
            break
        cursor = page["pageInfo"]["endCursor"]
    return nodes


def fetch_issues(owner: str, repo: str) -> list:
    raw = gh(["api", f"repos/{owner}/{repo}/issues?state=all&per_page=100", "--paginate",
              "--slurp"])
    pages = json.loads(raw)
    flat = [item for page in pages for item in page]
    return [i for i in flat if "pull_request" not in i]


def fetch_blocked_by(owner: str, repo: str, issues: list) -> dict:
    out = {}
    for iss in issues:
        summ = iss.get("issue_dependencies_summary") or {}
        if summ.get("total_blocked_by", 0) > 0:
            n = iss["number"]
            deps = json.loads(gh(["api", f"repos/{owner}/{repo}/issues/{n}/dependencies/blocked_by"]))
            out[str(n)] = [b["number"] for b in deps]
    return out


def count_loc(repo_root: Path) -> dict:
    locations = json.loads((SKILL_DIR / "assets" / "locations.json").read_text())["locations"]
    out = {}
    for entry in locations:
        ss = entry["source_set"]
        base = repo_root / "composeApp" / "src" / ss
        total = files = 0
        if base.is_dir():
            for path in base.rglob("*"):
                if path.suffix in (".kt", ".swift") and path.is_file():
                    files += 1
                    total += sum(1 for _ in path.open(encoding="utf-8", errors="ignore"))
        out[ss] = total
        out[f"{ss}_files"] = files
    return out


def sprint_cutoff(repo_root: Path) -> str:
    res = subprocess.run(
        ["git", "-C", str(repo_root), "log", "--diff-filter=A", "--follow",
         "--format=%aI", "--", "docs/sprints/sprint-01.md"],
        capture_output=True, text=True)
    lines = [ln for ln in res.stdout.splitlines() if ln.strip()]
    return lines[-1] if lines else ""


def main() -> None:
    ap = argparse.ArgumentParser(description="Collect raw data for /progress")
    ap.add_argument("--raw-data", default="/tmp/tether-progress-raw")
    ap.add_argument("--repo-root", default=".")
    args = ap.parse_args()

    raw = Path(args.raw_data)
    raw.mkdir(parents=True, exist_ok=True)
    repo_root = Path(args.repo_root).resolve()

    owner, repo = resolve_repo()
    print(f"repo: {owner}/{repo}")

    prs = fetch_prs(owner, repo)
    (raw / "prs.json").write_text(json.dumps(prs, indent=2))
    print(f"prs.json: {len(prs)}")

    issues = fetch_issues(owner, repo)
    (raw / "issues.json").write_text(json.dumps(issues, indent=2))
    print(f"issues.json: {len(issues)}")

    blocked = fetch_blocked_by(owner, repo, issues)
    (raw / "blocked_by.json").write_text(json.dumps(blocked, indent=2))
    print(f"blocked_by.json: {len(blocked)}")

    loc = count_loc(repo_root)
    (raw / "loc.json").write_text(json.dumps(loc, indent=2))
    print(f"loc.json: {sum(v for k, v in loc.items() if not k.endswith('_files'))} LOC")

    cutoff = sprint_cutoff(repo_root)
    (raw / "sprint_cutoff.txt").write_text(cutoff + "\n")
    print(f"sprint_cutoff.txt: {cutoff}")

    print(f"\nwritten to {raw}")


if __name__ == "__main__":
    main()
