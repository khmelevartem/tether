#!/usr/bin/env python3
"""Re-derive the issue-sizing review-burden bands from closed-issue actuals.

Backs the grooming self-calibration step and the `.claude/sizing-rubric.md`
calibration table. For every closed, size-labelled issue it joins the merged PR
(`#<N>: …` title) and the PR's inline review comments, then reports, per size:

  - ROOT review comments  — top-level inline comments (in_reply_to == null),
                            i.e. the author's substantive feedback points; thread
                            replies (often /check-review auto-acks) are excluded.
  - review rounds         — ROOT comments clustered by created_at, a gap of more
                            than ROUND_GAP_MIN minutes opening a new cluster.
  - LOC (additions+deletions) as a weak prior only.

owner/repo is resolved from `gh repo view`.

  python3 review-burden.py [--round-gap-min 60]
"""
import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict
from datetime import datetime
from statistics import mean, median

SIZE_LABELS = ("size:S", "size:M", "size:L")


def gh(args: list[str]) -> str:
    res = subprocess.run(["gh", *args], capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit(f"gh {' '.join(args)} failed:\n{res.stderr}")
    return res.stdout


def resolve_repo() -> str:
    return gh(["repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"]).strip()


def closed_sized_issues() -> dict[int, str]:
    raw = gh(["issue", "list", "--state", "closed", "--limit", "1000", "--json", "number,labels"])
    out = {}
    for i in json.loads(raw):
        names = {lbl["name"] for lbl in i["labels"]}
        size = next((n.split(":")[1] for n in names if n in SIZE_LABELS), None)
        if size:
            out[i["number"]] = size
    return out


def issue_to_pr(repo: str) -> dict[int, dict]:
    raw = gh(["pr", "list", "--state", "merged", "--limit", "500",
              "--json", "number,title,additions,deletions"])
    out = {}
    for p in json.loads(raw):
        m = re.match(r"#(\d+):", p["title"])
        if m:
            n = int(m.group(1))
            out.setdefault(n, {"pr": p["number"], "loc": p["additions"] + p["deletions"]})
    return out


def review_burden(repo: str, pr: int, gap_min: int) -> tuple[int, int]:
    raw = gh(["api", f"repos/{repo}/pulls/{pr}/comments", "--paginate",
              "--jq", ".[] | {t:.created_at, reply:(.in_reply_to_id!=null)}"])
    roots = [json.loads(line)["t"] for line in raw.splitlines()
             if line.strip() and not json.loads(line)["reply"]]
    if not roots:
        return 0, 0
    times = sorted(datetime.fromisoformat(t.replace("Z", "+00:00")) for t in roots)
    rounds = 1
    for a, b in zip(times, times[1:]):
        if (b - a).total_seconds() > gap_min * 60:
            rounds += 1
    return len(roots), rounds


def main() -> None:
    ap = argparse.ArgumentParser(description="Re-derive issue-sizing review-burden bands")
    ap.add_argument("--round-gap-min", type=int, default=60,
                    help="minutes between ROOT comments that start a new review round")
    args = ap.parse_args()

    repo = resolve_repo()
    print(f"repo: {repo}")
    sizes = closed_sized_issues()
    prs = issue_to_pr(repo)

    by_size: dict[str, dict[str, list]] = defaultdict(lambda: {"comments": [], "rounds": [], "loc": []})
    linked = 0
    for number, size in sizes.items():
        pr = prs.get(number)
        if not pr:
            continue
        linked += 1
        comments, rounds = review_burden(repo, pr["pr"], args.round_gap_min)
        by_size[size]["comments"].append(comments)
        by_size[size]["rounds"].append(rounds)
        by_size[size]["loc"].append(pr["loc"])

    print(f"closed sized issues: {len(sizes)} · with merged PR: {linked}\n")
    print(f"{'size':4} {'n':>3}  {'ROOT comments (mean/median)':>28}  {'rounds mean':>11}  {'LOC median':>10}")
    for size in ("S", "M", "L"):
        d = by_size.get(size)
        if not d or not d["comments"]:
            continue
        c = d["comments"]
        print(f"{size:4} {len(c):>3}  "
              f"{mean(c):>10.1f} / {median(c):<13.0f}  "
              f"{mean(d['rounds']):>11.2f}  {median(d['loc']):>10.0f}")


if __name__ == "__main__":
    main()
