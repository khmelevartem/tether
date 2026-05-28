#!/usr/bin/env python3
"""
Raw-data dir (--raw-data) must contain:
  prs.json          Array of PR objects from GraphQL repository.pullRequests.
                    Required per node: number, title, state, createdAt, mergedAt,
                    additions, deletions, changedFiles,
                    commits: { totalCount, nodes: [{ commit: { committedDate } }] }
                      (nodes sorted asc; first node used for cycleHours),
                    comments: { totalCount },
                    reviews: { totalCount },
                    reviewThreads: { totalCount }.
  issues.json       Array of issue objects from REST repos/<o>/<r>/issues?state=all.
                    Required per object: number, title, state, created_at, closed_at,
                    labels: [{name}], parent_issue_url (nullable),
                    issue_dependencies_summary: { total_blocked_by, ... } (nullable),
                    pull_request (presence → filter out; it's a PR not an issue).
  blocked_by.json   Map { "<issue_number>": [<blocker_number>, ...] }.
                    Empty map ({}) when no issue has blocked_by dependencies.
  loc.json          Map { "<sourceSet>": <int> } for source sets in assets/locations.json.
  sprint_cutoff.txt Single ISO date line — when docs/sprints/sprint-01.md was first added.

MVP file (--mvp): JSON array ordered by roadmap chapter:
  [{ "title": "Chapter I: …", "subtitle": "…", "percent": 60, "note": "…" }, …]
  percent: 0–100. note: optional one-liner.

Sprint file (--sprint): docs/sprints/sprint-NN.md. Parses ## Composition section for #N refs.

Assets dir defaults to <script_dir>/assets; reads palette.json, classes.json, keywords.json,
  locations.json, schools.json.

Exit codes: 0 success, 1 runtime error, 2 missing required input file.

docs_share approximation: a merged PR is counted as touching docs/ when its title contains
  any keyword from assets/keywords.json#docs_keywords (case-insensitive). Per-file path data
  is not available in the GraphQL shape listed above.
"""

import argparse
import html
import json
import math
import re
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

_palette: dict = {}


def _to_script_json(obj) -> str:
    # `</` would end the enclosing <script> block if present in string values.
    return json.dumps(obj).replace("</", "<\\/")


def _load_palette(assets_dir: Path) -> None:
    global _palette
    with open(assets_dir / "palette.json") as f:
        _palette = json.load(f)


def pal(dotted: str) -> str:
    parts = dotted.split(".")
    node = _palette
    for p in parts:
        node = node[p]
    return str(node)


def _require(path: Path) -> None:
    if not path.exists():
        print(f"ERROR: required file missing: {path}", file=sys.stderr)
        sys.exit(2)


def load_raw(raw_dir: Path) -> dict:
    for name in ("prs.json", "issues.json", "blocked_by.json", "loc.json", "sprint_cutoff.txt"):
        _require(raw_dir / name)

    with open(raw_dir / "prs.json") as f:
        prs = json.load(f)
    with open(raw_dir / "issues.json") as f:
        raw_issues = json.load(f)
    with open(raw_dir / "blocked_by.json") as f:
        blocked_by = json.load(f)
    with open(raw_dir / "loc.json") as f:
        loc = json.load(f)
    cutoff_str = (raw_dir / "sprint_cutoff.txt").read_text().strip()

    issues = [i for i in raw_issues if "pull_request" not in i]

    return dict(
        prs=prs,
        issues=issues,
        blocked_by=blocked_by,
        loc=loc,
        cutoff=date.fromisoformat(cutoff_str[:10]),
    )


def load_mvp(mvp_path: Path) -> list:
    _require(mvp_path)
    with open(mvp_path) as f:
        return json.load(f)


def load_assets(assets_dir: Path) -> dict:
    result = {}
    for name in ("palette.json", "classes.json", "keywords.json", "locations.json", "schools.json"):
        with open(assets_dir / name) as f:
            result[name.replace(".json", "")] = json.load(f)
    return result


def parse_sprint(sprint_path: Path) -> tuple[str, list[int]]:
    _require(sprint_path)
    text = sprint_path.read_text()

    h1 = re.search(r"^#\s+(.+)", text, re.MULTILINE)
    title = h1.group(1).strip() if h1 else sprint_path.stem

    composition = re.search(r"##\s+Composition(.*?)(?=^##\s|\Z)", text, re.DOTALL | re.MULTILINE)
    numbers: list[int] = []
    if composition:
        numbers = [int(m) for m in re.findall(r"#(\d+)", composition.group(1))]
    return title, numbers


def categorise(title: str, keywords: dict) -> str:
    t = title.lower()
    if t.startswith("retro"):
        return "retro"
    if re.search(r"\B/[a-z][a-z0-9-]+", t):
        return "infra"
    for kw in keywords["infra_keywords"]:
        if kw in t:
            return "infra"
    for kw in keywords["feature_keywords"]:
        if kw in t:
            return "feature"
    return keywords.get("default_category", "feature")


def merged_prs(prs: list) -> list:
    return [p for p in prs if p.get("state") == "MERGED"]


def compute_shares(prs_merged: list, keywords: dict) -> dict:
    total = len(prs_merged)
    if total == 0:
        return dict(infra_share=0.0, docs_share=0.0, retro_share=0.0)

    infra = sum(1 for p in prs_merged if categorise(p["title"], keywords) == "infra")
    retro = sum(1 for p in prs_merged if categorise(p["title"], keywords) == "retro")
    docs_kw = keywords.get("docs_keywords", [])
    docs = sum(1 for p in prs_merged if any(k in p["title"].lower() for k in docs_kw))
    return dict(
        infra_share=infra / total,
        docs_share=docs / total,
        retro_share=retro / total,
    )


def hero_class(shares: dict, classes_data: dict) -> tuple[str, str]:
    inf = shares["infra_share"]
    doc = shares["docs_share"]
    ret = shares["retro_share"]
    for rule in classes_data["rules"]:
        pred = rule["predicate"]
        if pred == "default":
            return rule["class"], rule["lore"]
        if eval(pred, {"infra_share": inf, "docs_share": doc, "retro_share": ret}):  # noqa: S307
            return rule["class"], rule["lore"]
    return classes_data["rules"][-1]["class"], classes_data["rules"][-1]["lore"]


def level_xp(prs_merged: list, issues: list) -> dict:
    merged = len(prs_merged)
    closed = sum(1 for i in issues if i.get("state") == "closed")
    xp_total = 2 * merged + closed
    level = int(math.floor(math.sqrt(xp_total)))
    xp_in_level = xp_total - level * level
    xp_needed = (level + 1) * (level + 1) - level * level
    return dict(
        level=level,
        xp_total=xp_total,
        xp_in_level=xp_in_level,
        xp_needed=xp_needed,
        merged=merged,
        closed_issues=closed,
    )


def artifact_weight(pr: dict) -> float:
    commits = pr.get("commits", {}).get("totalCount", 0)
    comments = pr.get("comments", {}).get("totalCount", 0)
    rt = pr.get("reviewThreads", {}).get("totalCount", 0)
    add = pr.get("additions", 0)
    dele = pr.get("deletions", 0)
    return commits * 2 + comments + rt * 3 + (add + dele) / 200


def top_artifacts(prs_merged: list, n: int = 5) -> list:
    ranked = sorted(prs_merged, key=artifact_weight, reverse=True)
    return ranked[:n]


def hot_battles(prs: list, n: int = 5) -> list:
    return sorted(prs, key=lambda p: p.get("comments", {}).get("totalCount", 0) + p.get("reviewThreads", {}).get("totalCount", 0), reverse=True)[:n]


def heavy_marches(prs: list, n: int = 5) -> list:
    return sorted(prs, key=lambda p: p.get("additions", 0) + p.get("deletions", 0), reverse=True)[:n]


def _pr_to_date(pr: dict, field: str) -> date | None:
    val = pr.get(field)
    if not val:
        return None
    return date.fromisoformat(val[:10])


def balance_of_week(prs_merged: list, keywords: dict, today: date) -> dict:
    def window_share(start: date, end: date) -> float:
        feature = infra = 0
        for p in prs_merged:
            d = _pr_to_date(p, "mergedAt")
            if d and start <= d < end:
                cat = categorise(p["title"], keywords)
                if cat == "feature":
                    feature += 1
                else:
                    infra += 1
        total = feature + infra
        return feature / total * 100 if total > 0 else 0.0

    end = today + timedelta(days=1)
    mid = today - timedelta(days=6)
    prev_start = mid - timedelta(days=7)

    current = window_share(mid, end)
    prev = window_share(prev_start, mid)
    delta = current - prev
    return dict(current=round(current, 1), prev=round(prev, 1), delta=round(delta, 1))


def seal_of_debt(issues: list, prs: list, cutoff: date, keywords: dict) -> dict:
    planned_numbers: set[int] = set()
    for p in prs:
        m = re.match(r"#(\d+):", p.get("title", ""))
        if m:
            planned_numbers.add(int(m.group(1)))

    post_cutoff = [
        i for i in issues
        if i.get("created_at") and date.fromisoformat(i["created_at"][:10]) >= cutoff
    ]

    closed_post = [i for i in post_cutoff if i.get("state") == "closed"]

    by_scroll = [i for i in closed_post if i["number"] in planned_numbers]
    random_enc = [i for i in closed_post if i["number"] not in planned_numbers]

    total = len(by_scroll) + len(random_enc)
    return dict(
        by_scroll=by_scroll,
        random_enc=random_enc,
        total=total,
        by_scroll_last6=by_scroll[-6:],
        random_enc_last6=random_enc[-6:],
    )


def _cycle_hours(pr: dict) -> float:
    nodes = pr.get("commits", {}).get("nodes", [])
    if not nodes:
        return 0.0
    first_commit = nodes[0].get("commit", {}).get("committedDate")
    merged_at = pr.get("mergedAt")
    if not first_commit or not merged_at:
        return 0.0
    try:
        t0 = datetime.fromisoformat(first_commit.replace("Z", "+00:00"))
        t1 = datetime.fromisoformat(merged_at.replace("Z", "+00:00"))
        return max(0.0, (t1 - t0).total_seconds() / 3600)
    except ValueError:
        return 0.0


def _issue_size(pr: dict, issues_by_number: dict) -> str:
    m = re.match(r"#(\d+):", pr.get("title", ""))
    if m:
        issue = issues_by_number.get(int(m.group(1)))
        if issue:
            for lbl in issue.get("labels", []):
                name = lbl.get("name", "")
                if name in ("size:S", "size:M", "size:L"):
                    return name.split(":")[1]
    return "unlabeled"


def glory_of_days(prs_merged: list, issues_by_number: dict, keywords: dict,
                  cutoff: date, today: date) -> dict:
    valor_size = _palette.get("valor_size", {"S": 1, "M": 3, "L": 8, "unlabeled": 2, "retro": 1})

    days: dict[str, dict] = {}
    d = cutoff
    while d <= today:
        days[d.isoformat()] = {"feature": 0.0, "infra": 0.0}
        d += timedelta(days=1)

    for pr in prs_merged:
        merged_date = _pr_to_date(pr, "mergedAt")
        if not merged_date:
            continue
        day_key = merged_date.isoformat()
        if day_key not in days:
            continue

        cat = categorise(pr["title"], keywords)
        size = "retro" if cat == "retro" else _issue_size(pr, issues_by_number)
        base = valor_size.get(size, valor_size.get("unlabeled", 2))
        comments = (pr.get("comments", {}).get("totalCount", 0)
                    + pr.get("reviewThreads", {}).get("totalCount", 0))
        loc = pr.get("additions", 0) + pr.get("deletions", 0)
        cycle = _cycle_hours(pr)
        valor = base + 0.3 * comments + loc / 200 + cycle / 24

        bucket = "infra" if cat in ("infra", "retro") else "feature"
        days[day_key][bucket] += valor

    totals = [days[k]["feature"] + days[k]["infra"] for k in sorted(days)]
    total_valor = sum(totals)
    peak_val = max(totals, default=0.0)
    peak_day = sorted(days)[totals.index(peak_val)] if totals else today.isoformat()

    return dict(
        days=days,
        total=round(total_valor, 1),
        avg=round(total_valor / len(totals) if totals else 0.0, 2),
        peak_val=round(peak_val, 1),
        peak_day=peak_day,
    )


def artifact_spread(prs_merged: list, issues_by_number: dict) -> dict:
    counts = {"S": 0, "M": 0, "L": 0, "unlabeled": 0}
    for pr in prs_merged:
        size = _issue_size(pr, issues_by_number)
        counts[size] = counts.get(size, 0) + 1
    return counts


def classify_school(title: str, schools: list) -> str:
    t = title.lower()
    for school in schools:
        for kw in school.get("keywords", []):
            if kw in t:
                return school["id"]
    return "other"


def build_graph_data(issues: list, blocked_by: dict, schools: list) -> dict:
    nodes = []
    edges = []

    issue_map = {i["number"]: i for i in issues}
    has_parent = set()
    has_blocked_by: set[int] = set()
    has_blocks: set[int] = set()

    for num_str, blockers in blocked_by.items():
        num = int(num_str)
        has_blocked_by.add(num)
        for b in blockers:
            has_blocks.add(int(b))
            edges.append({"source": int(b), "target": num, "type": "block"})

    for issue in issues:
        if issue.get("parent_issue_url"):
            m = re.search(r"/issues/(\d+)$", issue["parent_issue_url"])
            if m:
                parent_num = int(m.group(1))
                has_parent.add(issue["number"])
                edges.append({"source": parent_num, "target": issue["number"], "type": "parent"})

    for issue in issues:
        num = issue["number"]
        is_open = issue.get("state") == "open"
        isolated = (num not in has_parent and num not in has_blocked_by and num not in has_blocks)
        orphan = is_open and isolated
        blocked = is_open and num in has_blocked_by
        nodes.append({
            "id": num,
            "title": issue["title"],
            "state": issue.get("state", "open"),
            "school": classify_school(issue["title"], schools),
            "orphan": orphan,
            "blocked": blocked,
        })

    return dict(nodes=nodes, edges=edges)


def _e(s) -> str:
    return html.escape(str(s))


def section_frame(title: str, content: str) -> str:
    gold_dim = pal("gold.dim")
    gold_p = pal("gold.primary")
    bg_sec = pal("background.section")
    font_h = pal("fonts.headings")
    return f"""
<section style="background:{bg_sec};border:2px double {gold_dim};border-radius:6px;
  padding:24px 28px;margin:24px 0;position:relative;">
  <div style="position:absolute;top:6px;left:10px;color:{gold_dim};font-size:18px;">❧</div>
  <div style="position:absolute;top:6px;right:10px;color:{gold_dim};font-size:18px;">❧</div>
  <h2 style="font-family:'{font_h}',serif;color:{gold_p};margin:0 0 16px;
      font-size:20px;letter-spacing:.06em;">{title}</h2>
  {content}
</section>"""


def card(label: str, value: str, color: str = "", extra_style: str = "") -> str:
    bg = pal("background.card")
    gold_d = pal("gold.dim")
    txt = pal("text.primary")
    mono = pal("fonts.numbers")
    val_color = color if color else pal("gold.primary")
    return f"""<div style="background:{bg};border:1px solid {gold_d};border-radius:4px;
  padding:10px 14px;display:inline-block;min-width:120px;vertical-align:top;{extra_style}">
  <div style="font-size:11px;color:{pal('text.muted')};margin-bottom:4px;">{_e(label)}</div>
  <div style="font-family:'{mono}',monospace;color:{val_color};font-size:20px;">{value}</div>
</div>"""


def _table(headers: list[str], rows: list[list], right_last: bool = True) -> str:
    mono = pal("fonts.numbers")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    bg_card = pal("background.card")
    ths = "".join(
        f'<th style="font-family:\'{mono}\',monospace;color:{txt_m};font-size:11px;'
        f'padding:4px 8px;border-bottom:1px solid {gold_d};'
        f'text-align:{"right" if right_last and i == len(headers)-1 else "left"};">'
        f'{_e(h)}</th>'
        for i, h in enumerate(headers)
    )
    trs = ""
    for row in rows:
        tds = "".join(
            f'<td style="font-family:\'{mono}\',monospace;padding:4px 8px;color:{txt_p};'
            f'text-align:{"right" if right_last and j == len(row)-1 else "left"};">'
            f'{_e(cell)}</td>'
            for j, cell in enumerate(row)
        )
        trs += f"<tr>{tds}</tr>"
    return (f'<table style="width:100%;border-collapse:collapse;background:{bg_card};'
            f'border-radius:4px;overflow:hidden;">'
            f'<thead><tr>{ths}</tr></thead><tbody>{trs}</tbody></table>')


def render_header(today: date) -> str:
    bg = pal("background.page")
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    font_h = pal("fonts.headings")
    return f"""
<header style="text-align:center;padding:40px 20px 24px;background:{bg};">
  <h1 style="font-family:'{font_h}',serif;color:{gold_p};font-size:36px;
      letter-spacing:.1em;margin:0;">✦ Tether Saga ✦</h1>
  <p style="font-family:'{font_h}',serif;color:{gold_d};font-size:15px;margin:6px 0 0;">
    Chronicles of the Dragonborn Developer</p>
  <p style="color:{txt_m};font-size:13px;margin:8px 0 0;">{_e(today.isoformat())}</p>
</header>"""


def render_character_sheet(lx: dict, shares: dict, cls_name: str, cls_lore: str,
                            balance: dict, prs_merged: list, issues: list,
                            sprint_title: str) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_p = pal("text.primary")
    txt_m = pal("text.muted")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")
    blood = pal("blood")

    xp_pct = round(lx["xp_in_level"] / lx["xp_needed"] * 100) if lx["xp_needed"] else 0

    delta = balance["delta"]
    delta_color = pal("gold.primary") if delta > 0 else (blood if delta < 0 else txt_m)
    sign = "+" if delta > 0 else ("−" if delta < 0 else "±")
    delta_str = f'<span style="color:{delta_color};">({sign}{abs(delta):.1f}%)</span>'

    total_discussions = sum(
        p.get("comments", {}).get("totalCount", 0) + p.get("reviewThreads", {}).get("totalCount", 0)
        for p in prs_merged
    )
    all_prs = len(prs_merged)
    total_issues = len(issues)

    content = f"""
<div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;">
  <div>
    <div style="font-family:'{font_h}',serif;color:{gold_d};font-size:11px;
        letter-spacing:.08em;margin-bottom:6px;">CLASS</div>
    <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:22px;
        margin-bottom:2px;">{_e(cls_name)}</div>
    <div style="color:{txt_m};font-size:12px;margin-bottom:16px;font-style:italic;">{_e(cls_lore)}</div>

    <div style="font-family:'{font_h}',serif;color:{gold_d};font-size:11px;
        letter-spacing:.08em;margin-bottom:4px;">LEVEL {lx["level"]}</div>
    <div style="background:#2a2010;border-radius:3px;height:14px;margin-bottom:4px;overflow:hidden;">
      <div style="background:{gold_p};width:{xp_pct}%;height:100%;border-radius:3px;"></div>
    </div>
    <div style="color:{txt_m};font-size:12px;font-family:'{mono}',monospace;">
      {lx["xp_in_level"]} / {lx["xp_needed"]} XP &nbsp;·&nbsp; total {lx["xp_total"]}</div>
  </div>
  <div>
    <div style="font-family:'{font_h}',serif;color:{gold_d};font-size:11px;
        letter-spacing:.08em;margin-bottom:8px;">JOURNEY CHRONICLE</div>
    <div style="color:{txt_p};font-size:14px;line-height:2;">
      <span style="color:{txt_m};">Merged PRs</span>
      <span style="font-family:'{mono}',monospace;color:{gold_p};margin-left:8px;">{all_prs}</span><br>
      <span style="color:{txt_m};">Closed Issues</span>
      <span style="font-family:'{mono}',monospace;color:{gold_p};margin-left:8px;">{lx["closed_issues"]}</span><br>
      <span style="color:{txt_m};">Discussions</span>
      <span style="font-family:'{mono}',monospace;color:{gold_p};margin-left:8px;">{total_discussions}</span><br>
      <span style="color:{txt_m};">Current Chapter</span>
      <span style="color:{txt_p};margin-left:8px;">{_e(sprint_title)}</span>
    </div>
    <div style="margin-top:12px;background:#15100c;border:1px solid {gold_d};
        border-radius:4px;padding:8px 12px;display:inline-block;">
      <div style="font-size:11px;color:{txt_m};margin-bottom:2px;">Balance of the Week</div>
      <div style="font-family:'{mono}',monospace;font-size:18px;color:{gold_p};">
        {balance["current"]:.1f}% feature {delta_str}</div>
    </div>
  </div>
</div>"""
    return section_frame("⚔ Character Sheet", content)


def render_mvp(chapters: list) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    muted_d = pal("text.muted_dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    font_h = pal("fonts.headings")
    mono = pal("fonts.numbers")

    rows = ""
    for ch in chapters:
        pct = ch.get("percent", 0)
        bar_color = gold_p if pct > 0 else muted_d

        note_html = (f'<div style="font-size:11px;color:{txt_m};margin-top:2px;">'
                     f'{_e(ch["note"])}</div>') if ch.get("note") else ""
        rows += f"""
<tr>
  <td style="padding:10px 8px;vertical-align:top;width:38%;">
    <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:13px;">{_e(ch["title"])}</div>
    <div style="color:{txt_m};font-size:11px;font-style:italic;">{_e(ch.get("subtitle",""))}</div>
    {note_html}
  </td>
  <td style="padding:10px 8px;vertical-align:middle;">
    <div style="background:#2a2010;border-radius:3px;height:10px;overflow:hidden;">
      <div style="background:{bar_color};width:{pct}%;height:100%;border-radius:3px;"></div>
    </div>
  </td>
  <td style="padding:10px 8px;vertical-align:middle;text-align:right;
      font-family:'{mono}',monospace;color:{gold_p};font-size:14px;width:50px;">{pct}%</td>
</tr>"""

    content = f"""<table style="width:100%;border-collapse:collapse;">{rows}</table>"""
    return section_frame("◈ Main Quest — MVP Chronicle", content)


def render_locations(loc: dict, locations_data: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    font_h = pal("fonts.headings")
    mono = pal("fonts.numbers")
    bg_card = pal("background.card")

    locs = locations_data["locations"]
    opened = [(e, loc.get(e["source_set"], 0)) for e in locs if loc.get(e["source_set"], 0) > 0]
    closed_locs = [e for e in locs if loc.get(e["source_set"], 0) == 0]

    cards_html = ""
    for entry, lines in sorted(opened, key=lambda x: -x[1]):
        cards_html += f"""<div style="background:{bg_card};border:1px solid {gold_d};
  border-radius:4px;padding:10px 14px;margin:6px;display:inline-block;
  vertical-align:top;min-width:160px;">
  <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:13px;">{_e(entry["name"])}</div>
  <div style="color:{txt_m};font-size:11px;font-style:italic;margin-bottom:4px;">{_e(entry["lore"])}</div>
  <div style="font-family:'{mono}',monospace;color:{gold_p};">{lines} LOC</div>
</div>"""

    if closed_locs:
        not_opened = ", ".join(_e(e["name"]) for e in closed_locs)
        cards_html += f'<div style="color:{txt_m};font-size:12px;margin:8px 0;clear:both;">Not opened: {not_opened}</div>'

    chart_labels = _to_script_json([e["name"] for e, _ in sorted(opened, key=lambda x: -x[1])])
    chart_data = _to_script_json([lines for _, lines in sorted(opened, key=lambda x: -x[1])])
    chart_id = "loc_chart"

    chart_html = f"""
<div style="max-width:600px;margin:16px 0;">
  <canvas id="{chart_id}" height="120"></canvas>
</div>
<script>
(function(){{
  var ctx = document.getElementById('{chart_id}').getContext('2d');
  new Chart(ctx, {{
    type: 'bar',
    data: {{
      labels: {chart_labels},
      datasets: [{{ data: {chart_data}, backgroundColor: '{gold_p}', borderRadius: 3 }}]
    }},
    options: {{
      indexAxis: 'y', responsive: true, plugins: {{ legend: {{ display: false }},
        tooltip: {{ callbacks: {{ label: ctx => ctx.raw + ' LOC' }} }} }},
      scales: {{
        x: {{ ticks: {{ color: '{txt_m}' }}, grid: {{ color: '#2a2010' }} }},
        y: {{ ticks: {{ color: '{txt_p}', font: {{ family: "'{font_h}'" }} }}, grid: {{ display: false }} }}
      }}
    }}
  }});
}})();
</script>"""

    return section_frame("◈ Open Locations", cards_html + chart_html)


def render_artifacts(top5: list) -> str:
    rarity_keys = ["legendary", "epic", "rare", "uncommon", "common"]
    gold_p = pal("gold.primary")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")
    bg_card = pal("background.card")

    cards = ""
    for i, pr in enumerate(top5):
        rarity = rarity_keys[i] if i < len(rarity_keys) else "common"
        r_data = _palette["artifact_rarity"][rarity]
        border_color = r_data["border"]
        glow = r_data.get("glow", "")
        box_shadow = f"0 0 12px {glow}" if glow else "none"
        commits = pr.get("commits", {}).get("totalCount", 0)
        comments = pr.get("comments", {}).get("totalCount", 0)
        rt = pr.get("reviewThreads", {}).get("totalCount", 0)
        add = pr.get("additions", 0)
        dele = pr.get("deletions", 0)
        weight = round(artifact_weight(pr), 1)

        cards += f"""<div style="background:{bg_card};border:2px solid {border_color};
  border-radius:6px;padding:14px;margin:8px;display:inline-block;
  vertical-align:top;min-width:200px;max-width:240px;box-shadow:{box_shadow};">
  <div style="font-size:10px;color:{border_color};letter-spacing:.1em;
      font-family:'{mono}',monospace;margin-bottom:4px;">{rarity.upper()} · ⚡ {weight}</div>
  <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:13px;margin-bottom:8px;">
    #{_e(pr["number"])} {_e(pr["title"][:50])}</div>
  <div style="font-family:'{mono}',monospace;font-size:12px;color:{txt_m};line-height:1.8;">
    commits: <span style="color:{txt_p};">{commits}</span><br>
    discussions: <span style="color:{txt_p};">{comments + rt}</span><br>
    lines: <span style="color:{txt_p};">+{add} −{dele}</span>
  </div>
</div>"""

    return section_frame("◈ Legendary Artifacts", cards)


def render_hot_heavy(prs: list) -> str:
    hot = hot_battles(prs)
    heavy = heavy_marches(prs)
    mono = pal("fonts.numbers")
    gold_p = pal("gold.primary")
    txt_m = pal("text.muted")

    hot_rows = [
        [f"#{p['number']}", p["title"][:60],
         str(p.get("comments", {}).get("totalCount", 0) + p.get("reviewThreads", {}).get("totalCount", 0))]
        for p in hot
    ]
    heavy_rows = [
        [f"#{p['number']}", p["title"][:60],
         str(p.get("additions", 0) + p.get("deletions", 0))]
        for p in heavy
    ]

    content = f"""
<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;">
  <div>
    <div style="font-family:'{pal("fonts.headings")}',serif;color:{gold_p};
        font-size:14px;margin-bottom:8px;">⚔ Hot Battles</div>
    {_table(["#PR", "Title", "Discussions"], hot_rows)}
  </div>
  <div>
    <div style="font-family:'{pal("fonts.headings")}',serif;color:{gold_p};
        font-size:14px;margin-bottom:8px;">⚔ Heavy Marches</div>
    {_table(["#PR", "Title", "Lines"], heavy_rows)}
  </div>
</div>"""
    return section_frame("⚔ Hot Battles / Heavy Marches", content)


def render_current_chapter(sprint_title: str, sprint_issues: list[int],
                            issues_by_number: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    font_h = pal("fonts.headings")
    blood = pal("blood")

    rows = ""
    for num in sprint_issues:
        issue = issues_by_number.get(num)
        if issue:
            state = issue.get("state", "open")
            state_color = gold_d if state == "closed" else txt_p
            rows += f"""<div style="padding:5px 0;border-bottom:1px solid #2a2010;">
  <span style="font-family:'{pal("fonts.numbers")}',monospace;color:{txt_m};
      font-size:12px;margin-right:8px;">#{num}</span>
  <span style="color:{state_color};">{_e(issue["title"][:80])}</span>
  <span style="float:right;font-size:11px;color:{"#5a8a3a" if state=="closed" else blood};">
    {state}</span>
</div>"""
        else:
            rows += f"""<div style="padding:5px 0;border-bottom:1px solid #2a2010;">
  <span style="font-family:'{pal("fonts.numbers")}',monospace;color:{txt_m};
      font-size:12px;">#{num}</span>
  <span style="color:{txt_m};font-size:12px;margin-left:8px;font-style:italic;">unknown</span>
</div>"""

    content = f'<div style="margin-top:8px;">{rows}</div>' if rows else f'<div style="color:{txt_m};">No planned tasks found.</div>'
    return section_frame(f"◈ Current Chapter — {_e(sprint_title)}", content)


def render_quest_map(graph: dict, schools_data: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    bg_card = pal("background.card")
    blood = pal("blood")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")

    gn = _palette["graph_nodes"]
    ge = _palette["graph_edges"]

    nodes = graph["nodes"]
    edges = graph["edges"]

    free_count = sum(1 for n in nodes if n["state"] == "open" and not n["blocked"] and not n["orphan"])
    chained_count = sum(1 for n in nodes if n["blocked"])
    orphan_count = sum(1 for n in nodes if n["orphan"])

    summary = f"""
<div style="margin-bottom:12px;">
  {card("Free Nodes", str(free_count), gn["free"]["fill"])}
  {card("In Chains", str(chained_count), gn["blocked"]["fill"])}
  {card("Lone Orphans", str(orphan_count), gn["orphan"]["fill"])}
</div>"""

    schools = schools_data["schools"]
    schools_js = _to_script_json([{"id": s["id"], "name": s["name"]} for s in schools])
    nodes_js = _to_script_json(nodes)
    edges_js = _to_script_json(edges)
    free_fill = gn["free"]["fill"]
    blocked_fill = gn["blocked"]["fill"]
    orphan_fill = gn["orphan"]["fill"]
    closed_fill = gn["closed"]["fill"]
    closed_stroke = gn["closed"]["stroke"]
    parent_stroke = ge["parent"]["stroke"]
    block_stroke = ge["block"]["stroke"]

    school_legend = "".join(
        f'<div style="margin:3px 0;"><span style="color:{gold_p};">▸</span> '
        f'<b style="color:{gold_p};">{_e(s["name"])}</b> '
        f'<span style="color:{txt_m};font-size:11px;">— {_e(s["summary"])}</span></div>'
        for s in schools
    )

    graph_html = f"""
{summary}
<div id="quest_map_box" style="position:relative;background:{bg_card};
  border:1px solid {gold_d};border-radius:6px;overflow:hidden;height:560px;">
  <svg id="quest_map_svg" style="width:100%;height:100%;"></svg>
  <div id="qm_tooltip" style="position:absolute;pointer-events:none;opacity:0;
    background:{bg_card};border:1px solid {gold_d};border-radius:4px;
    padding:8px 12px;font-size:12px;color:{txt_p};max-width:240px;
    transition:opacity .15s;z-index:10;"></div>
  <div style="position:absolute;top:8px;right:8px;display:flex;gap:4px;">
    <button onclick="qmZoomIn()" style="background:{bg_card};color:{gold_p};
      border:1px solid {gold_d};border-radius:3px;width:28px;height:28px;cursor:pointer;">+</button>
    <button onclick="qmZoomOut()" style="background:{bg_card};color:{gold_p};
      border:1px solid {gold_d};border-radius:3px;width:28px;height:28px;cursor:pointer;">−</button>
    <button onclick="qmReset()" style="background:{bg_card};color:{gold_p};
      border:1px solid {gold_d};border-radius:3px;width:28px;height:28px;cursor:pointer;">⤺</button>
  </div>
</div>
<div style="margin-top:10px;display:grid;grid-template-columns:1fr 1fr;gap:16px;">
  <div>
    <div style="font-size:12px;color:{gold_d};margin-bottom:6px;">Node colours</div>
    <div style="font-size:12px;line-height:1.9;">
      <span style="color:{free_fill};">●</span> <span style="color:{txt_m};">Free (open, unblocked)</span><br>
      <span style="color:{blocked_fill};">●</span> <span style="color:{txt_m};">In chains (blocked)</span><br>
      <span style="color:{orphan_fill};">●</span> <span style="color:{txt_m};">Lone orphan (isolated open)</span><br>
      <span style="color:{closed_fill};text-shadow:0 0 2px {closed_stroke};">●</span> <span style="color:{txt_m};">Closed</span>
    </div>
    <div style="font-size:12px;color:{gold_d};margin:10px 0 6px;">Edge types</div>
    <div style="font-size:12px;line-height:1.9;">
      <span style="color:{parent_stroke};">━━</span> <span style="color:{txt_m};">Parent → child (sub-issue)</span><br>
      <span style="color:{block_stroke};">╌╌</span> <span style="color:{txt_m};">Blocks → blocked-by</span>
    </div>
  </div>
  <div>
    <div style="font-size:12px;color:{gold_d};margin-bottom:6px;">Schools</div>
    {school_legend}
  </div>
</div>
<script>
(function(){{
  const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  const W_BOX = document.getElementById('quest_map_box').clientWidth || 800;
  const H = 560, PAD = 40;
  const schools = {schools_js};
  const rawNodes = {nodes_js};
  const rawEdges = {edges_js};

  const svg = d3.select('#quest_map_svg');
  const g = svg.append('g');

  const zoom = d3.zoom().scaleExtent([0.3, 4]).on('zoom', e => g.attr('transform', e.transform));
  svg.call(zoom);

  const R = Math.min(W_BOX, H) * 0.30;
  const R_LBL = Math.min(W_BOX * 0.46, H * 0.48);
  const cx = W_BOX / 2, cy = H / 2;

  const anchors = {{}};
  schools.forEach((s, i) => {{
    const angle = (2 * Math.PI * i / schools.length) - Math.PI / 2;
    anchors[s.id] = {{ x: cx + R * Math.cos(angle), y: cy + R * Math.sin(angle), angle }};
  }});

  const nodeMap = {{}};
  rawNodes.forEach(n => {{ nodeMap[n.id] = n; }});

  const nodes = rawNodes.map(n => ({{ ...n }}));
  const edges = rawEdges.map(e => ({{ ...e }}));

  const linkSel = g.append('g').selectAll('line').data(edges).join('line')
    .attr('stroke', e => e.type === 'block' ? '{block_stroke}' : '{parent_stroke}')
    .attr('stroke-width', 1.5)
    .attr('stroke-dasharray', e => e.type === 'block' ? '5,3' : null)
    .attr('opacity', .7);

  function nodeFill(n) {{
    if (n.state === 'closed') return '{closed_fill}';
    if (n.orphan) return '{orphan_fill}';
    if (n.blocked) return '{blocked_fill}';
    return '{free_fill}';
  }}
  function nodeStroke(n) {{
    if (n.state === 'closed') return '{closed_stroke}';
    return 'none';
  }}

  const nodeSel = g.append('g').selectAll('circle').data(nodes).join('circle')
    .attr('r', 8).attr('fill', nodeFill).attr('stroke', nodeStroke)
    .attr('stroke-width', 1.5).attr('cursor', 'grab')
    .on('mouseenter', (event, d) => {{
      const tt = document.getElementById('qm_tooltip');
      const school = schools.find(s => s.id === d.school);
      tt.innerHTML = '<b style="color:{gold_p};">#' + esc(d.id) + '</b><br>' +
        esc(d.title) + '<br><span style="color:{txt_m};">' + esc(school ? school.name : d.school) +
        ' · ' + esc(d.state) + (d.orphan ? ' · lone' : d.blocked ? ' · in chains' : '') + '</span>';
      const box = document.getElementById('quest_map_box').getBoundingClientRect();
      tt.style.left = (event.clientX - box.left + 12) + 'px';
      tt.style.top = (event.clientY - box.top + 12) + 'px';
      tt.style.opacity = '1';
    }})
    .on('mouseleave', () => {{ document.getElementById('qm_tooltip').style.opacity = '0'; }});

  const labelSel = g.append('g').selectAll('text').data(nodes).join('text')
    .text(d => '#' + d.id)
    .attr('font-family', 'JetBrains Mono, monospace').attr('font-size', 9)
    .attr('fill', '{txt_m}')
    .style('paint-order', 'stroke').attr('stroke', '{bg_card}').attr('stroke-width', 3)
    .attr('text-anchor', 'middle').attr('dy', -11).attr('pointer-events', 'none');

  const drag = d3.drag()
    .on('start', (event, d) => {{ d.fx = d.x; d.fy = d.y; }})
    .on('drag', (event, d) => {{ d.fx = event.x; d.fy = event.y; }})
    .on('end', (event, d) => {{ d.fx = null; d.fy = null; }});
  nodeSel.call(drag);

  svg.on('mousedown.zoom', function(event) {{
    if (event.target.tagName === 'circle') event.stopImmediatePropagation();
  }});

  const sim = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(edges).id(d => d.id).distance(38))
    .force('charge', d3.forceManyBody().strength(-50))
    .force('collide', d3.forceCollide(13))
    .force('cluster', d3.forceX(d => (anchors[d.school] || {{x: cx}}).x).strength(0.14))
    .force('clusterY', d3.forceY(d => (anchors[d.school] || {{y: cy}}).y).strength(0.14))
    .alphaDecay(0.025)
    .on('tick', () => {{
      nodes.forEach(n => {{
        n.x = Math.max(PAD, Math.min(W_BOX - PAD, n.x));
        n.y = Math.max(PAD, Math.min(H - PAD, n.y));
      }});
      linkSel.attr('x1', d => d.source.x).attr('y1', d => d.source.y)
             .attr('x2', d => d.target.x).attr('y2', d => d.target.y);
      nodeSel.attr('cx', d => d.x).attr('cy', d => d.y);
      labelSel.attr('x', d => d.x).attr('y', d => d.y);
    }});

  schools.forEach((s, i) => {{
    const a = anchors[s.id];
    const cos_a = Math.cos(a.angle);
    const lx = cx + R_LBL * Math.cos(a.angle);
    const ly = cy + R_LBL * Math.sin(a.angle);
    const anchor_txt = cos_a < -0.3 ? 'end' : (cos_a > 0.3 ? 'start' : 'middle');
    svg.append('text').text(s.name)
      .attr('x', lx).attr('y', ly).attr('text-anchor', anchor_txt)
      .attr('font-family', "'{font_h}', serif").attr('font-size', 11)
      .attr('fill', '{gold_d}').attr('pointer-events', 'none');
  }});

  window.qmZoomIn  = () => svg.transition().call(zoom.scaleBy, 1.4);
  window.qmZoomOut = () => svg.transition().call(zoom.scaleBy, 1/1.4);
  window.qmReset   = () => svg.transition().call(zoom.transform, d3.zoomIdentity);
}})();
</script>"""

    return section_frame("◈ Quest Map", graph_html)


def render_seal_of_debt(debt: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")
    blood = pal("blood")

    by_scroll = len(debt["by_scroll"])
    random_enc = len(debt["random_enc"])
    total = debt["total"]

    bar_scroll_pct = round(by_scroll / total * 100) if total > 0 else 0
    bar_random_pct = 100 - bar_scroll_pct

    last_scroll = "".join(
        f'<div style="font-size:12px;color:{txt_m};padding:2px 0;">#{i["number"]} {_e(i["title"][:55])}</div>'
        for i in debt["by_scroll_last6"]
    )
    last_random = "".join(
        f'<div style="font-size:12px;color:{txt_m};padding:2px 0;">#{i["number"]} {_e(i["title"][:55])}</div>'
        for i in debt["random_enc_last6"]
    )

    content = f"""
<div style="margin-bottom:16px;display:flex;gap:16px;flex-wrap:wrap;">
  {card("By the Sprint Scroll", str(by_scroll), gold_p)}
  {card("Random Encounters", str(random_enc), blood)}
  {card("Total Resolved", str(total), txt_p)}
</div>
<div style="height:20px;border-radius:4px;overflow:hidden;margin-bottom:16px;display:flex;">
  <div style="background:{gold_p};width:{bar_scroll_pct}%;"></div>
  <div style="background:#6a2a8a;width:{bar_random_pct}%;"></div>
</div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;">
  <div>
    <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:13px;margin-bottom:6px;">
      Last 6 — By the Scroll</div>
    {last_scroll or f'<div style="color:{txt_m};font-size:12px;">none</div>'}
  </div>
  <div>
    <div style="font-family:'{font_h}',serif;color:{blood};font-size:13px;margin-bottom:6px;">
      Last 6 — Random Encounters</div>
    {last_random or f'<div style="color:{txt_m};font-size:12px;">none</div>'}
  </div>
</div>"""

    return section_frame("◈ Seal of Debt", content)


def render_glory_of_days(glory: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")

    days_sorted = sorted(glory["days"].keys())
    labels = _to_script_json(days_sorted)
    feat_data = _to_script_json([round(glory["days"][d]["feature"], 2) for d in days_sorted])
    infra_data = _to_script_json([round(glory["days"][d]["infra"], 2) for d in days_sorted])
    feat_color = _palette["valor_day"]["feature"]
    infra_color = _palette["valor_day"]["infra"]
    line_color = _palette["valor_day"]["line"]

    content = f"""
<div style="margin-bottom:16px;">
  {card("Total Valour", str(glory["total"]), gold_p)}
  {card("Average Stride", str(glory["avg"]), gold_d)}
  {card("Day of the Great Battle", f'{glory["peak_day"]} · {glory["peak_val"]}', gold_p)}
</div>
<div style="max-width:900px;">
  <canvas id="glory_chart" height="160"></canvas>
</div>
<script>
(function(){{
  const ctx = document.getElementById('glory_chart').getContext('2d');
  new Chart(ctx, {{
    type: 'line',
    data: {{
      labels: {labels},
      datasets: [
        {{
          label: 'Feature',
          data: {feat_data},
          fill: true,
          backgroundColor: '{feat_color}b3',
          borderColor: '{feat_color}',
          borderWidth: 1.5,
          pointRadius: 0,
          tension: 0.3
        }},
        {{
          label: 'Infra',
          data: {infra_data},
          fill: true,
          backgroundColor: '{infra_color}b3',
          borderColor: '{infra_color}',
          borderWidth: 1.5,
          pointRadius: 0,
          tension: 0.3
        }}
      ]
    }},
    options: {{
      responsive: true,
      interaction: {{ mode: 'index', intersect: false }},
      plugins: {{
        legend: {{ labels: {{ color: '{txt_m}', font: {{ size: 11 }} }} }},
        tooltip: {{ }},
        filler: {{ propagate: true }}
      }},
      scales: {{
        x: {{ stacked: true, ticks: {{ color: '{txt_m}', maxTicksLimit: 12,
            font: {{ family: "'JetBrains Mono'" }} }}, grid: {{ color: '#2a2010' }} }},
        y: {{ stacked: true, ticks: {{ color: '{txt_m}' }}, grid: {{ color: '#2a2010' }} }}
      }}
    }}
  }});
}})();
</script>"""

    return section_frame("◈ Glory of Days", content)


def render_artifact_spread(spread: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")

    labels = ["S", "M", "L", "unlabeled"]
    colors = [pal("gold.primary"), pal("gold.secondary"), pal("gold.tertiary"), pal("text.muted_dim")]
    data = [spread.get(l, 0) for l in labels]
    labels_js = _to_script_json(labels)
    data_js = _to_script_json(data)
    colors_js = _to_script_json(colors)

    content = f"""
<div style="max-width:360px;margin:0 auto;">
  <canvas id="spread_chart" height="200"></canvas>
</div>
<script>
(function(){{
  const ctx = document.getElementById('spread_chart').getContext('2d');
  new Chart(ctx, {{
    type: 'doughnut',
    data: {{
      labels: {labels_js},
      datasets: [{{ data: {data_js}, backgroundColor: {colors_js}, borderWidth: 2,
        borderColor: '{pal("background.card")}' }}]
    }},
    options: {{
      responsive: true,
      plugins: {{
        legend: {{ position: 'right', labels: {{ color: '{txt_m}', font: {{ size: 12 }} }} }},
        tooltip: {{ callbacks: {{ label: ctx => ctx.label + ': ' + ctx.raw + ' PRs' }} }}
      }}
    }}
  }});
}})();
</script>"""

    return section_frame("◈ Artifact Spread", content)


def render_book_of_knowledge() -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    mono = pal("fonts.numbers")
    font_h = pal("fonts.headings")
    bg_card = pal("background.card")

    def formula(name: str, expr: str, note: str = "") -> str:
        note_html = f'<div style="color:{txt_m};font-size:11px;margin-top:3px;">{_e(note)}</div>' if note else ""
        return f"""<div style="background:{bg_card};border:1px solid {gold_d};border-radius:4px;
  padding:10px 14px;margin-bottom:10px;">
  <div style="font-family:'{font_h}',serif;color:{gold_p};font-size:12px;margin-bottom:4px;">{_e(name)}</div>
  <code style="font-family:'{mono}',monospace;color:{txt_p};font-size:12px;">{_e(expr)}</code>
  {note_html}
</div>"""

    col1 = (
        formula("Level", "floor(sqrt(2·merged_PRs + closed_issues))")
        + formula("XP in Level", "xp_total − level²", "xp_total = 2·merged + closed")
        + formula("XP to next", "(level+1)² − level²")
        + formula("Artifact Weight", "commits·2 + comments + review_threads·3 + (add+del)/200")
    )
    col2 = (
        formula("Quest Valour", "base(size) + 0.3·comments + LOC/200 + cycleHours/24",
                "base: S=1, M=3, L=8, unlabeled=2, retro=1")
        + formula("Hero Class", "first matching rule in assets/classes.json (retro>docs>infra>balance>default)")
        + formula("MVP %", "0–100 by chapter evidence: done/partial/spec-only/none")
        + formula("Hot / Heavy", "top-5 by discussions / top-5 by additions+deletions")
    )

    content = f"""
<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;">
  <div>{col1}</div>
  <div>{col2}</div>
</div>"""
    return section_frame("◈ Book of Knowledge", content)


def render_html(
    raw: dict,
    mvp_chapters: list,
    sprint_title: str,
    sprint_issues: list[int],
    assets: dict,
    today: date,
) -> str:
    prs_all = raw["prs"]
    issues = raw["issues"]
    blocked_by = raw["blocked_by"]
    loc = raw["loc"]
    cutoff = raw["cutoff"]

    kw = assets["keywords"]
    merged = merged_prs(prs_all)
    shares = compute_shares(merged, kw)
    cls_name, cls_lore = hero_class(shares, assets["classes"])
    lx = level_xp(merged, issues)
    balance = balance_of_week(merged, kw, today)

    issues_by_number = {i["number"]: i for i in issues}

    top5 = top_artifacts(merged)
    glory = glory_of_days(merged, issues_by_number, kw, cutoff, today)
    spread = artifact_spread(merged, issues_by_number)
    debt = seal_of_debt(issues, prs_all, cutoff, kw)
    graph = build_graph_data(issues, blocked_by, assets["schools"]["schools"])

    font_h = pal("fonts.headings")
    font_b = pal("fonts.body")
    font_n = pal("fonts.numbers")
    base_sz = pal("fonts.base_size_px")
    bg_page = pal("background.page")
    txt_p = pal("text.primary")

    google_fonts_url = (
        f"https://fonts.googleapis.com/css2?family={font_h.replace(' ', '+')}:wght@400;700"
        f"&family={font_b.replace(' ', '+')}"
        f"&family={font_n.replace(' ', '+')}"
        f"&display=swap"
    )

    body = (
        render_header(today)
        + render_character_sheet(lx, shares, cls_name, cls_lore, balance, merged, issues, sprint_title)
        + render_mvp(mvp_chapters)
        + render_locations(loc, assets["locations"])
        + render_artifacts(top5)
        + render_hot_heavy(prs_all)
        + render_current_chapter(sprint_title, sprint_issues, issues_by_number)
        + render_quest_map(graph, assets["schools"])
        + render_seal_of_debt(debt)
        + render_glory_of_days(glory)
        + render_artifact_spread(spread)
        + render_book_of_knowledge()
    )

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tether Progress — {today.isoformat()}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="{google_fonts_url}">
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
<style>
  *, *::before, *::after {{ box-sizing: border-box; }}
  html, body {{ margin: 0; padding: 0; background: {bg_page}; color: {txt_p};
    font-family: '{font_b}', serif; font-size: {base_sz}px; line-height: 1.55; }}
  .container {{ max-width: 1100px; margin: 0 auto; padding: 0 20px 60px; }}
  a {{ color: {pal("gold.primary")}; }}
  code {{ font-family: '{font_n}', monospace; }}
</style>
</head>
<body>
{body}
<div class="container"></div>
</body>
</html>"""


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Render Tether project progress as a self-contained HTML RPG character sheet."
    )
    parser.add_argument("--raw-data", required=True, metavar="DIR",
                        help="Directory with prs.json, issues.json, blocked_by.json, loc.json, sprint_cutoff.txt")
    parser.add_argument("--mvp", required=True, metavar="FILE",
                        help="JSON file: array of MVP chapter judgments")
    parser.add_argument("--sprint", required=True, metavar="FILE",
                        help="Path to the active docs/sprints/sprint-NN.md")
    parser.add_argument("--output", required=True, metavar="HTML",
                        help="Output path for the self-contained HTML file")
    parser.add_argument("--today", metavar="YYYY-MM-DD",
                        help="Override today's date (default: system date)")
    parser.add_argument("--assets", metavar="DIR",
                        help="Assets directory (default: <script_dir>/assets)")
    args = parser.parse_args()

    today = date.fromisoformat(args.today) if args.today else date.today()
    assets_dir = Path(args.assets) if args.assets else Path(__file__).parent / "assets"

    _load_palette(assets_dir)
    assets = load_assets(assets_dir)
    raw = load_raw(Path(args.raw_data))
    mvp_chapters = load_mvp(Path(args.mvp))
    sprint_title, sprint_issues = parse_sprint(Path(args.sprint))

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render_html(raw, mvp_chapters, sprint_title, sprint_issues, assets, today), encoding="utf-8")
    print(f"Written: {out}")


if __name__ == "__main__":
    main()
