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

from __future__ import annotations

import argparse
import html
import json
import math
import re
import subprocess
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


def _git_added_date(path: Path) -> date | None:
    try:
        r = subprocess.run(
            ["git", "log", "--diff-filter=A", "--follow", "--format=%aI", "--", str(path)],
            capture_output=True, text=True, check=True,
        )
        lines = [line for line in r.stdout.splitlines() if line.strip()]
        if not lines:
            return None
        return date.fromisoformat(lines[-1][:10])
    except (subprocess.CalledProcessError, FileNotFoundError, ValueError):
        return None


def parse_sprint(sprint_path: Path) -> tuple[str, list[int]]:
    _require(sprint_path)
    text = sprint_path.read_text()

    h1 = re.search(r"^#\s+(.+)", text, re.MULTILINE)
    title = h1.group(1).strip() if h1 else sprint_path.stem

    composition = re.search(r"##\s+(?:Composition|Состав)(.*?)(?=^##\s|\Z)", text, re.DOTALL | re.MULTILINE)
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


def compute_shares(prs_merged: list, keywords: dict, sprint_cutoff: date | None = None) -> dict:
    total = len(prs_merged)
    if total == 0:
        return dict(infra_share=0.0, docs_share=0.0, retro_share=0.0, retro_share_sprint=0.0)

    infra = sum(1 for p in prs_merged if categorise(p["title"], keywords) == "infra")
    retro = sum(1 for p in prs_merged if categorise(p["title"], keywords) == "retro")
    docs_kw = keywords.get("docs_keywords", [])
    docs = sum(1 for p in prs_merged if any(k in p["title"].lower() for k in docs_kw))

    sprint_prs = [
        p for p in prs_merged
        if sprint_cutoff and p.get("mergedAt")
        and date.fromisoformat(p["mergedAt"][:10]) >= sprint_cutoff
    ]
    sprint_total = len(sprint_prs)
    sprint_retro = sum(1 for p in sprint_prs if categorise(p["title"], keywords) == "retro")
    retro_share_sprint = sprint_retro / sprint_total if sprint_total else 0.0

    return dict(
        infra_share=infra / total,
        docs_share=docs / total,
        retro_share=retro / total,
        retro_share_sprint=retro_share_sprint,
    )


def hero_class(shares: dict, classes_data: dict) -> tuple[str, str]:
    scope = {
        "infra_share": shares["infra_share"],
        "docs_share": shares["docs_share"],
        "retro_share": shares["retro_share"],
        "retro_share_sprint": shares.get("retro_share_sprint", 0.0),
    }
    for rule in classes_data["rules"]:
        pred = rule["predicate"]
        if pred == "default":
            return rule["class"], rule["lore"]
        if eval(pred, scope):  # noqa: S307
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


def seal_of_debt(issues: list, sprints_dir: Path, cutoff: date, keywords: dict) -> dict:
    planned_numbers: set[int] = set()
    for sprint_file in sorted(sprints_dir.glob("sprint-*.md")):
        _, nums = parse_sprint(sprint_file)
        planned_numbers.update(nums)

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


_ROMAN = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
          "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"]


def _roman(n: int) -> str:
    return _ROMAN[n - 1] if 1 <= n <= len(_ROMAN) else str(n)


def render_header(today: date) -> str:
    day_ru = [
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    ]
    date_str = f"{today.day}-й день месяца {day_ru[today.month - 1].capitalize()}, год {today.year}-й"
    return f"""<h1>✦ Tether Saga ✦</h1>
<div class="sub">Хроники Драконорождённого Разработчика</div>
<div class="sub">{_e(date_str)}</div>"""


def render_character_sheet(lx: dict, shares: dict, cls_name: str, cls_lore: str,
                            balance: dict, prs_merged: list, issues: list,
                            sprint_title: str) -> str:
    blood = pal("blood")
    txt_m = pal("text.muted")

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
    closed_issues = lx["closed_issues"]

    # find PR with most discussions for "hottest discussion" line
    hottest = max(
        prs_merged,
        key=lambda p: p.get("comments", {}).get("totalCount", 0) + p.get("reviewThreads", {}).get("totalCount", 0),
        default=None,
    )
    hottest_line = ""
    if hottest:
        hottest_total = (hottest.get("comments", {}).get("totalCount", 0)
                         + hottest.get("reviewThreads", {}).get("totalCount", 0))
        hottest_line = (f'Самое жаркое обсуждение: <span style="color:var(--gold);">{hottest_total}</span>'
                        f' на #{_e(hottest["number"])}<br>')

    return f"""<h2>Лист Персонажа</h2>
<div class="frame">
  <div class="character">
    <div class="char-left">
      <div class="row">
        <div class="lbl">Класс</div>
        <div class="val">{_e(cls_name)}</div>
        <div class="muted" style="font-style:italic; font-size:13px;">{_e(cls_lore)}</div>
      </div>
      <div class="row">
        <div class="lbl">Уровень</div>
        <div class="val level mono">{lx["level"]}</div>
        <div class="xp"><div class="xp-fill" style="width:{xp_pct}%"></div></div>
        <div class="muted mono" style="font-size:12px;">{lx["xp_in_level"]} / {lx["xp_needed"]} XP до {lx["level"] + 1}</div>
      </div>
    </div>
    <div class="char-right">
      <div class="lbl">Хроника пути</div>
      <div class="mono" style="line-height:2;">
        Артефактов выковано: <span style="color:var(--gold);">{all_prs}</span><br>
        Свитков утверждено: <span style="color:var(--gold);">{closed_issues}</span><br>
        Кровь обсуждений: <span style="color:var(--gold);">{total_discussions}</span> голосов на советах<br>
        {hottest_line}Спринт сейчас: <span style="color:var(--gold);">{_e(sprint_title)}</span>
      </div>
      <div class="duty-col" style="margin-top:14px; display:inline-block;">
        <div class="lbl" style="font-size:12px; margin-bottom:4px;">Баланс недели</div>
        <div class="mono" style="font-size:18px; color:var(--gold);">
          {balance["current"]:.1f}% feature {delta_str}
        </div>
      </div>
    </div>
  </div>
</div>"""


def render_mvp(chapters: list) -> str:
    rows = ""
    for idx, ch in enumerate(chapters):
        pct = ch.get("percent", 0)
        if pct >= 100:
            row_cls = "done"
            stat_word = "Завершено ✓"
        elif pct == 0:
            row_cls = "todo"
            stat_word = "Не начато"
        else:
            row_cls = "active"
            stat_word = "В пути"

        note = _e(ch.get("note", ""))
        chapter_label = f"Глава {_roman(idx + 1)}"
        rows += f"""<tr class="{row_cls}">
  <td class="ch">{_e(chapter_label)}</td>
  <td>
    <div class="qname">{_e(ch["title"])}</div>
    <div class="qepic">{_e(ch.get("subtitle", ""))}</div>
  </td>
  <td class="qstat">
    <div>{_e(stat_word)}</div>
    <div class="mvp-bar"><div class="mvp-fill" style="width:{pct}%"></div></div>
    <div class="mvp-pct mono">{pct}%</div>
  </td>
  <td class="qev">{note}</td>
</tr>"""

    return f"""<h2>Главный Сюжет — Хроника MVP</h2>
<div class="frame">
  <table class="scroll"><tbody>{rows}</tbody></table>
</div>"""


def render_locations(loc: dict, locations_data: dict) -> str:
    locs = locations_data["locations"]

    loc_entries = [(e, loc.get(e["source_set"], 0)) for e in locs]
    sorted_open = sorted(
        [(e, v) for e, v in loc_entries if v > 0],
        key=lambda x: -x[1],
    )

    cards_html = '<div class="locations">'
    for entry, lines in sorted_open:
        ss = _e(entry["source_set"])
        files = loc.get(entry["source_set"] + "_files", "?")
        cards_html += f"""<div class="loc">
  <div class="loc-name">{_e(entry["name"])}</div>
  <div class="loc-ss">{ss}</div>
  <div class="loc-stats">{lines} LOC · {files} файлов</div>
  <div class="loc-desc">{_e(entry["lore"])}</div>
</div>"""

    for entry, _ in [(e, v) for e, v in loc_entries if v == 0]:
        cards_html += f"""<div class="loc dim">
  <div class="loc-name">{_e(entry["name"])}</div>
  <div class="loc-ss">{_e(entry["source_set"])}</div>
  <div class="loc-stats">не открыта</div>
  <div class="loc-desc">{_e(entry["lore"])}</div>
</div>"""

    cards_html += "</div>"

    chart_labels = _to_script_json([e["name"] for e, v in sorted_open])
    chart_data = _to_script_json([v for _, v in sorted_open])
    gold_p = pal("gold.primary")
    txt_m = pal("text.muted")
    txt_p = pal("text.primary")
    font_h = pal("fonts.headings")

    chart_html = f"""
<div style="margin-top:24px; padding-top:20px; border-top:1px dashed var(--gold-dim);">
  <div class="muted" style="font-style:italic; margin-bottom:12px; font-size:13px;">Та же карта, прочитанная гномьим землемером:</div>
  <div class="chart-box short"><canvas id="locchart"></canvas></div>
</div>
<script>
(function(){{
  var ctx = document.getElementById('locchart').getContext('2d');
  new Chart(ctx, {{
    type: 'bar',
    data: {{
      labels: {chart_labels},
      datasets: [{{ label:'LOC', data: {chart_data},
        backgroundColor: ['#d4af37','#b89030','#9c7826','#80601e','#644818','#4a3812','#7a3a26','#c9302c'],
        borderColor:'#3a2e1c', borderWidth:1 }}]
    }},
    options: {{
      responsive: true, maintainAspectRatio: false, indexAxis: 'y',
      plugins: {{ legend: {{ display: false }}, tooltip: {{ callbacks: {{ label: c => c.parsed.x + ' LOC' }} }} }},
      scales: {{
        x: {{ ticks: {{ color: '{txt_m}' }}, grid: {{ color: '#2a2018' }} }},
        y: {{ ticks: {{ color: '{txt_p}', font: {{ family: "'{font_h}'" }} }}, grid: {{ display: false }} }}
      }}
    }}
  }});
}})();
</script>"""

    return f"""<h2>Открытые Локации</h2>
<div class="frame">
  {cards_html}
  {chart_html}
</div>"""


def render_artifacts(top5: list) -> str:
    rarity_keys = ["legendary", "epic", "rare", "uncommon", "common"]

    cards = ""
    for i, pr in enumerate(top5):
        rarity = rarity_keys[i] if i < len(rarity_keys) else "common"
        rarity_label = rarity.capitalize()
        commits = pr.get("commits", {}).get("totalCount", 0)
        comments = pr.get("comments", {}).get("totalCount", 0)
        rt = pr.get("reviewThreads", {}).get("totalCount", 0)
        add = pr.get("additions", 0)
        dele = pr.get("deletions", 0)

        flavor_map = {
            "legendary": "Свиток такой длины, что писец трижды менял перо.",
            "epic": "Клинок, что ковали целым кругом подмастерьев — каждый оставил знак.",
            "rare": "Древняя реликвия, переписанная и обсуждённая старейшинами не раз.",
            "uncommon": "Прочное снаряжение — носить можно, гордиться можно.",
            "common": "Простая, но надёжная вещь дорожного мешка.",
        }
        flavor = flavor_map.get(rarity, "")

        cards += f"""<div class="art art-{rarity}">
  <div class="art-rarity">{_e(rarity_label)}</div>
  <div class="art-name">#{_e(pr["number"])} — {_e(pr["title"][:80])}</div>
  <ul class="art-stats">
    <li>⚔ <b>{commits}</b> коммитов</li>
    <li>☉ <b>{comments + rt}</b> обсуждений</li>
    <li>◈ <b>+{add}</b> / <b>−{dele}</b> строк</li>
  </ul>
  <div class="art-flavor">{_e(flavor)}</div>
</div>"""

    return f"""<h2>Легендарные Артефакты</h2>
<div class="frame">
  <div class="artifacts">{cards}</div>
</div>"""


def render_hot_heavy(prs: list) -> str:
    hot = hot_battles(prs)
    heavy = heavy_marches(prs)

    def pr_rows_hot(items: list) -> str:
        return "".join(
            f'<tr><td class="prnum mono">#{_e(p["number"])}</td>'
            f'<td class="prtitle">{_e(p["title"][:80])}</td>'
            f'<td class="prval mono">'
            f'{p.get("comments", {}).get("totalCount", 0) + p.get("reviewThreads", {}).get("totalCount", 0)}'
            f'</td></tr>'
            for p in items
        )

    def pr_rows_heavy(items: list) -> str:
        return "".join(
            f'<tr><td class="prnum mono">#{_e(p["number"])}</td>'
            f'<td class="prtitle">{_e(p["title"][:80])}</td>'
            f'<td class="prval mono">+{p.get("additions", 0)}/−{p.get("deletions", 0)}</td></tr>'
            for p in items
        )

    return f"""<div class="two-col" style="margin-top:48px;">
  <div>
    <h2 style="margin-top:0;">Жаркие Сражения <span class="muted small">(топ по обсуждениям)</span></h2>
    <div class="frame">
      <table class="stat-table">
        <thead><tr><th>PR</th><th>Название</th><th class="r">обсуждений</th></tr></thead>
        <tbody>{pr_rows_hot(hot)}</tbody>
      </table>
    </div>
  </div>
  <div>
    <h2 style="margin-top:0;">Тяжёлые Походы <span class="muted small">(топ по размеру)</span></h2>
    <div class="frame">
      <table class="stat-table">
        <thead><tr><th>PR</th><th>Название</th><th class="r">+/− строк</th></tr></thead>
        <tbody>{pr_rows_heavy(heavy)}</tbody>
      </table>
    </div>
  </div>
</div>"""


def render_current_chapter(sprint_title: str, sprint_issues: list[int],
                            issues_by_number: dict) -> str:
    items = ""
    for num in sprint_issues:
        issue = issues_by_number.get(num)
        if issue:
            state = issue.get("state", "open")
            mark = "✓" if state == "closed" else "◯"
            items += f'<li><span class="qmark">{mark}</span> #{num} — {_e(issue["title"])}</li>'
        else:
            items += f'<li><span class="qmark">◯</span> #{num}</li>'

    quests_html = f'<ul class="quests" style="margin-top:12px;">{items}</ul>' if items else "<p>Нет задач.</p>"

    return f"""<h2>Текущая Глава — {_e(sprint_title)}</h2>
<div class="frame">
  {quests_html}
</div>"""


def render_quest_map(graph: dict, schools_data: dict) -> str:
    gold_p = pal("gold.primary")
    gold_d = pal("gold.dim")
    txt_m = pal("text.muted")
    bg_card = pal("background.card")
    font_h = pal("fonts.headings")

    gn = _palette["graph_nodes"]
    ge = _palette["graph_edges"]

    nodes = graph["nodes"]
    edges = graph["edges"]

    free_count = sum(1 for n in nodes if n["state"] == "open" and not n["blocked"] and not n["orphan"])
    chained_count = sum(1 for n in nodes if n["blocked"])
    orphan_count = sum(1 for n in nodes if n["orphan"])

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

    schools_legend_items = "".join(
        f'<div><span class="school-name">{_e(s["name"])}</span>'
        f'<span class="muted"> — {_e(s["summary"])}</span></div>'
        for s in schools
    )

    return f"""<h2>Карта Заданий — что свободно, что в цепях</h2>
<div class="frame">
  <div class="duty-summary">
    <div class="duty-col">
      <div class="duty-num mono">{free_count}</div>
      <div class="duty-lbl">Свободные открытые</div>
      <div class="duty-pct muted">можно брать</div>
    </div>
    <div class="duty-col">
      <div class="duty-num mono" style="color:var(--blood);">{chained_count}</div>
      <div class="duty-lbl">В цепях</div>
      <div class="duty-pct muted">ждут предков</div>
    </div>
    <div class="duty-col">
      <div class="duty-num mono" style="color:#e8d070;">{orphan_count}</div>
      <div class="duty-lbl">Сирые открытые</div>
      <div class="duty-pct muted">без связей</div>
    </div>
  </div>
  <div id="graph-box" class="chart-box" style="height:620px; margin-top:14px;">
    <div class="graph-tooltip" id="graph-tooltip"></div>
    <div class="graph-controls">
      <button onclick="window.__graphZoom('in')">+</button>
      <button onclick="window.__graphZoom('out')">−</button>
      <button onclick="window.__graphZoom('reset')">⤺</button>
      <span class="muted small">или scroll / drag</span>
    </div>
    <svg id="quest_map_svg" style="width:100%;height:100%;cursor:grab;background:#15100c;border:1px solid #2a2018;"></svg>
  </div>
  <div class="graph-legend mono">
    <span><i class="dot" style="background:{free_fill}; border-radius:50%; display:inline-block; width:11px; height:11px; margin-right:6px; vertical-align:middle;"></i> открыт, свободен</span>
    <span><i class="dot" style="background:{blocked_fill}; border-radius:50%; display:inline-block; width:11px; height:11px; margin-right:6px; vertical-align:middle;"></i> открыт, в цепях</span>
    <span><i class="dot" style="background:{orphan_fill}; border:1px solid {pal("gold.primary")}; border-radius:50%; display:inline-block; width:11px; height:11px; margin-right:6px; vertical-align:middle;"></i> сирый (без связей)</span>
    <span><i class="dot" style="background:{closed_fill}; border:1px solid #6a5a40; border-radius:50%; display:inline-block; width:11px; height:11px; margin-right:6px; vertical-align:middle;"></i> закрыт</span>
    <span><i class="line" style="display:inline-block; width:24px; height:2px; background:{parent_stroke}; margin-right:6px; vertical-align:middle;"></i> sub-issue / parent</span>
    <span><i class="line dashed" style="display:inline-block; width:24px; height:0; border-top:2px dashed {block_stroke}; margin-right:6px; vertical-align:middle;"></i> блокирует</span>
  </div>
  <div class="schools-legend">{schools_legend_items}</div>
</div>
<script>
(function(){{
  const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  const box = document.getElementById('graph-box');
  const W = box.clientWidth || 1100, H = 620, PAD = 16;
  const schools = {schools_js};
  const rawNodes = {nodes_js};
  const rawEdges = {edges_js};

  const svg = d3.select('#quest_map_svg').attr('viewBox', `0 0 ${{W}} ${{H}}`);
  svg.append('defs').html(`
    <marker id="arrow-block" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="{block_stroke}" opacity=".8"/>
    </marker>
    <marker id="arrow-parent" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="{parent_stroke}" opacity=".6"/>
    </marker>
  `);
  const root = svg.append('g').attr('class', 'root');

  const cx = W/2, cy = H/2;
  const R = Math.min(W, H) * 0.30;
  const R_LBL = Math.min(W * 0.46, H * 0.48);
  const clusterMap = {{}};
  schools.forEach((s, i) => {{
    const ang = (i / schools.length) * Math.PI * 2 - Math.PI / 2;
    s.x = cx + R * Math.cos(ang);
    s.y = cy + R * Math.sin(ang);
    s.lx = cx + R_LBL * Math.cos(ang);
    s.ly = cy + R_LBL * Math.sin(ang);
    s.anchor = Math.abs(Math.cos(ang)) < 0.3 ? 'middle' : (Math.cos(ang) > 0 ? 'start' : 'end');
    clusterMap[s.id] = s;
  }});

  root.append('g').selectAll('text').data(schools).join('text')
    .attr('class','cluster-label')
    .attr('text-anchor', d => d.anchor)
    .attr('x', d => d.lx).attr('y', d => d.ly)
    .text(d => d.name);

  const link = root.append('g').selectAll('line').data(rawEdges).join('line')
    .attr('class', d => 'ge-' + (d.type === 'block' ? 'block' : 'parent'))
    .attr('marker-end', d => d.type === 'block' ? 'url(#arrow-block)' : 'url(#arrow-parent)');

  const node = root.append('g').selectAll('g').data(rawNodes).join('g').attr('class','gn');
  node.append('circle')
    .attr('r', d => d.state === 'closed' ? 4 : (d.orphan ? 4 : 6))
    .attr('class', d => d.state === 'closed' ? 'gn-closed' : (d.blocked ? 'gn-blocked' : (d.orphan ? 'gn-orphan' : 'gn-free')));

  const labelLayer = root.append('g').attr('class','labels');
  const label = labelLayer.selectAll('text').data(rawNodes).join('text')
    .attr('class','gn-label').attr('dx', 8).attr('dy', 3)
    .text(d => '#' + d.id);

  const tip = document.getElementById('graph-tooltip');
  const stateRu = {{open:'открыт', closed:'закрыт'}};
  node.on('mouseenter', (e, d) => {{
    const status = (stateRu[d.state] || d.state)
      + (d.blocked ? ' · в цепях' : '') + (d.orphan ? ' · сирый' : '');
    tip.innerHTML = `<div><span class="tt-num">#${{d.id}}</span>${{esc(d.title)}}</div><div class="tt-meta">${{d.school}} · ${{status}}</div>`;
    const r = box.getBoundingClientRect();
    tip.style.left = (e.clientX - r.left) + 'px';
    tip.style.top  = (e.clientY - r.top - 12) + 'px';
    tip.classList.add('show');
  }}).on('mousemove', (e) => {{
    const r = box.getBoundingClientRect();
    tip.style.left = (e.clientX - r.left) + 'px';
    tip.style.top  = (e.clientY - r.top - 12) + 'px';
  }}).on('mouseleave', () => tip.classList.remove('show'));

  const sim = d3.forceSimulation(rawNodes)
    .force('link', d3.forceLink(rawEdges).id(d => d.id).distance(38).strength(.7))
    .force('charge', d3.forceManyBody().strength(-50))
    .force('x', d3.forceX(d => (clusterMap[d.school]||{{x:cx}}).x).strength(.14))
    .force('y', d3.forceY(d => (clusterMap[d.school]||{{y:cy}}).y).strength(.14))
    .force('collide', d3.forceCollide(13))
    .alphaDecay(.025)
    .on('tick', () => {{
      rawNodes.forEach(d => {{
        d.x = Math.max(PAD, Math.min(W - PAD, d.x));
        d.y = Math.max(PAD, Math.min(H - PAD, d.y));
      }});
      link.attr('x1', d=>d.source.x).attr('y1', d=>d.source.y)
          .attr('x2', d=>d.target.x).attr('y2', d=>d.target.y);
      node.attr('transform', d => `translate(${{d.x}},${{d.y}})`);
      label.attr('x', d => d.x).attr('y', d => d.y);
    }});

  node.call(d3.drag()
    .on('start', (e,d) => {{ if (!e.active) sim.alphaTarget(.3).restart(); d.fx=d.x; d.fy=d.y; }})
    .on('drag',  (e,d) => {{ d.fx=e.x; d.fy=e.y; }})
    .on('end',   (e,d) => {{ if (!e.active) sim.alphaTarget(0); d.fx=null; d.fy=null; }}));

  const zoom = d3.zoom().scaleExtent([0.3, 4])
    .filter(e => !e.target.closest('.gn'))
    .on('zoom', e => root.attr('transform', e.transform));
  svg.call(zoom);

  window.__graphZoom = (kind) => {{
    if (kind === 'in')    svg.transition().duration(250).call(zoom.scaleBy, 1.4);
    if (kind === 'out')   svg.transition().duration(250).call(zoom.scaleBy, 1/1.4);
    if (kind === 'reset') svg.transition().duration(350).call(zoom.transform, d3.zoomIdentity);
  }};
}})();
</script>"""


def render_seal_of_debt(debt: dict) -> str:
    blood = pal("blood")
    gold_p = pal("gold.primary")

    by_scroll = len(debt["by_scroll"])
    random_enc = len(debt["random_enc"])
    total = debt["total"]

    bar_scroll_pct = round(by_scroll / total * 100) if total > 0 else 0
    bar_random_pct = 100 - bar_scroll_pct

    scroll_pct_str = f"{bar_scroll_pct}%"
    random_pct_str = f"{bar_random_pct}%"

    def debt_list_items(items: list) -> str:
        return "".join(
            f'<li><span class="prnum mono">#{i["number"]}</span> {_e(i["title"][:65])}</li>'
            for i in items
        ) or "<li>—</li>"

    last_scroll_html = debt_list_items(debt["by_scroll_last6"])
    last_random_html = debt_list_items(debt["random_enc_last6"])

    return f"""<h2>Печать Долга — план vs импровизация</h2>
<div class="frame">
  <div class="duty-summary">
    <div class="duty-col">
      <div class="duty-num mono">{by_scroll}</div>
      <div class="duty-lbl">По свитку спринтов</div>
      <div class="duty-pct mono">{scroll_pct_str}</div>
    </div>
    <div class="duty-col">
      <div class="duty-num mono">{random_enc}</div>
      <div class="duty-lbl">Случайные встречи</div>
      <div class="duty-pct mono">{random_pct_str}</div>
    </div>
    <div class="duty-col">
      <div class="duty-num mono">{total}</div>
      <div class="duty-lbl">Всего сразили</div>
      <div class="duty-pct mono">&nbsp;</div>
    </div>
  </div>
  <div class="duty-bar">
    <div class="duty-bar-fill planned" style="width:{scroll_pct_str}"></div>
    <div class="duty-bar-fill wild" style="width:{random_pct_str}"></div>
  </div>
  <div class="duty-legend mono">
    <span><i class="dot dot-planned"></i> Из планов спринтов</span>
    <span><i class="dot dot-wild"></i> Сверх плана — пойманные в пути</span>
  </div>
  <div class="duty-lists">
    <div>
      <h3 class="duty-h3">◈ Последние из плана</h3>
      <ul class="duty-list">{last_scroll_html}</ul>
    </div>
    <div>
      <h3 class="duty-h3">✦ Последние случайные</h3>
      <ul class="duty-list">{last_random_html}</ul>
    </div>
  </div>
</div>"""

def render_glory_of_days(glory: dict) -> str:
    txt_m = pal("text.muted")
    feat_color = _palette["valor_day"]["feature"]
    infra_color = _palette["valor_day"]["infra"]

    days_sorted = sorted(glory["days"].keys())
    labels = _to_script_json(days_sorted)
    feat_data = _to_script_json([round(glory["days"][d]["feature"], 2) for d in days_sorted])
    infra_data = _to_script_json([round(glory["days"][d]["infra"], 2) for d in days_sorted])

    chart_script = f"""<script>
(function(){{
  const ctx = document.getElementById('glory_chart').getContext('2d');
  new Chart(ctx, {{
    type: 'line',
    data: {{
      labels: {labels},
      datasets: [
        {{
          label: 'Feature', data: {feat_data}, fill: true,
          backgroundColor: '{feat_color}b3', borderColor: '{feat_color}',
          borderWidth: 1.5, pointRadius: 0, tension: 0.3
        }},
        {{
          label: 'Infra', data: {infra_data}, fill: true,
          backgroundColor: '{infra_color}b3', borderColor: '{infra_color}',
          borderWidth: 1.5, pointRadius: 0, tension: 0.3
        }}
      ]
    }},
    options: {{
      responsive: true, maintainAspectRatio: false,
      interaction: {{ mode: 'index', intersect: false }},
      plugins: {{
        legend: {{ labels: {{ color: '{txt_m}', font: {{ size: 11 }} }} }},
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

    stats_cards = f"""<div class="three-col" style="margin-top:14px;">
  <div class="duty-col">
    <div class="duty-num mono">{glory["total"]}</div>
    <div class="duty-lbl">Всего доблести</div>
  </div>
  <div class="duty-col">
    <div class="duty-num mono">{glory["avg"]}</div>
    <div class="duty-lbl">Средний шаг</div>
  </div>
  <div class="duty-col" style="font-size:13px;">
    <div class="duty-num mono" style="font-size:24px;">{glory["peak_val"]}</div>
    <div class="duty-lbl">Пик: {_e(glory["peak_day"])}</div>
  </div>
</div>"""

    return f"""<div class="glory-col">
  <h2 style="margin-top:0;">Хроника Подвигов</h2>
  <div class="frame">
    <div class="chart-box"><canvas id="glory_chart"></canvas></div>
    {stats_cards}
  </div>
</div>
{chart_script}"""


def render_artifact_spread(spread: dict) -> str:
    txt_m = pal("text.muted")
    bg_card = pal("background.card")

    labels = ["S", "M", "L", "unlabeled"]
    colors = [pal("gold.primary"), pal("gold.secondary"), pal("gold.tertiary"), pal("text.muted_dim")]
    data = [spread.get(l, 0) for l in labels]
    labels_js = _to_script_json(labels)
    data_js = _to_script_json(data)
    colors_js = _to_script_json(colors)

    return f"""<div class="spread-col">
  <h2 style="margin-top:0;">Размах Артефактов</h2>
  <div class="frame">
    <div class="chart-box"><canvas id="spread_chart"></canvas></div>
  </div>
</div>
<script>
(function(){{
  const ctx = document.getElementById('spread_chart').getContext('2d');
  new Chart(ctx, {{
    type: 'doughnut',
    data: {{
      labels: {labels_js},
      datasets: [{{ data: {data_js}, backgroundColor: {colors_js}, borderWidth: 2,
        borderColor: '{bg_card}' }}]
    }},
    options: {{
      responsive: true, maintainAspectRatio: false,
      plugins: {{
        legend: {{ position: 'right', labels: {{ color: '{txt_m}', font: {{ size: 13 }} }} }},
        tooltip: {{ callbacks: {{ label: ctx => ctx.label + ': ' + ctx.raw + ' PRs' }} }}
      }}
    }}
  }});
}})();
</script>"""


def render_book_of_knowledge() -> str:
    gold_p = pal("gold.primary")
    txt_m = pal("text.muted")

    def entry(title: str, formula_expr: str, detail: str = "", note: str = "") -> str:
        detail_html = f"<p>{_e(detail)}</p>" if detail else ""
        note_html = f'<p class="small">{_e(note)}</p>' if note else ""
        return (f"<h3>{_e(title)}</h3>"
                f'<div class="entry">'
                f"<p><code>{_e(formula_expr)}</code></p>"
                f"{detail_html}{note_html}"
                f"</div>")

    col1 = (
        entry("◈ Уровень и XP",
              "Level = floor(sqrt(2·PR + closed_issues))",
              "PR — «выкованный артефакт», стоит вдвое дороже закрытого свитка. "
              "sqrt — кривая diminishing returns: первые уровни лёгкие, дальше всё дороже.",
              "xp_total = 2·merged + closed; XP_in_level = xp_total − level²")
        + entry("⚔ Вес артефакта (топ-5)",
                "weight = commits·2 + comments + threads·3 + LOC/200",
                "",
                "Самые тяжёлые PR — много коммитов, много обсуждения и много кода.")
        + entry("✦ Класс",
                "first matching rule in assets/classes.json",
                "Если retro-PR > 20% — Паладин ретро. Иначе по доле инфра-PR: >55% — Архитектор-некромант, "
                "40–55% — Инженер-рейнджер, иначе — Боевой маг протоколов. "
                "Жрец документации — если доминируют PR в docs/.",
                "Категоризация PR по ключевым словам в title.")
    )
    col2 = (
        entry("◇ Главы MVP — прогресс",
              "0–100% по доказательствам",
              "Каждая глава имеет процент готовности — оценка по статусу feature в features/README.md, "
              "наличию смерженных PR, парности по платформам.",
              "100% — done на всех платформах; 50–80% — частично; 30% — только spec; 5% — ни кода, ни решения.")
        + entry("⚔ Жаркие Сражения / Тяжёлые Походы",
                "Жаркие: top-5 по sum(comments + review_threads). Тяжёлые: top-5 по additions + deletions.")
        + entry("❧ Локации",
                "Source sets из composeApp/src/. LOC по .kt/.swift через git ls-files.",
                "",
                "Локация «не открыта», если LOC = 0.")
    )

    return f"""<h2>Книга Знаний — как считались числа</h2>
<div class="frame compendium">
  <div>{col1}</div>
  <div>{col2}</div>
</div>"""


def render_html(
    raw: dict,
    mvp_chapters: list,
    sprint_title: str,
    sprint_issues: list[int],
    assets: dict,
    today: date,
    sprints_dir: Path,
) -> str:
    prs_all = raw["prs"]
    issues = raw["issues"]
    blocked_by = raw["blocked_by"]
    loc = raw["loc"]
    cutoff = raw["cutoff"]

    kw = assets["keywords"]
    merged = merged_prs(prs_all)
    shares = compute_shares(merged, kw, raw.get("active_sprint_cutoff"))
    cls_name, cls_lore = hero_class(shares, assets["classes"])
    lx = level_xp(merged, issues)
    balance = balance_of_week(merged, kw, today)

    issues_by_number = {i["number"]: i for i in issues}

    top5 = top_artifacts(merged)
    glory = glory_of_days(merged, issues_by_number, kw, cutoff, today)
    spread = artifact_spread(merged, issues_by_number)
    debt = seal_of_debt(issues, sprints_dir, cutoff, kw)
    graph = build_graph_data(issues, blocked_by, assets["schools"]["schools"])

    font_h = pal("fonts.headings")
    font_b = pal("fonts.body")
    font_n = pal("fonts.numbers")

    google_fonts_url = (
        "https://fonts.googleapis.com/css2"
        f"?family={font_h.replace(' ', '+')}:wght@400;700"
        f"&family={font_b.replace(' ', '+').replace(' SC', '+SC')}"
        f":ital@0;1"
        f"&family={font_n.replace(' ', '+')}"
        f"&display=swap"
    )

    # Pull each CSS value through pal() so palette.json stays source of truth
    bg = pal("background.page")
    bg2 = pal("background.section")
    gold = pal("gold.primary")
    gold_dim = pal("gold.dim")
    blood = pal("blood")
    text_p = pal("text.primary")
    muted = pal("text.muted")
    epic = "#9b4dca"
    rare = "#4a7bc8"
    unc = "#5a8a3a"

    body_sections = (
        render_header(today)
        + render_character_sheet(lx, shares, cls_name, cls_lore, balance, merged, issues, sprint_title)
        + render_mvp(mvp_chapters)
        + render_locations(loc, assets["locations"])
        + render_artifacts(top5)
        + render_hot_heavy(prs_all)
        + render_current_chapter(sprint_title, sprint_issues, issues_by_number)
        + render_quest_map(graph, assets["schools"])
        + render_seal_of_debt(debt)
        + f'<div class="two-col" style="margin-top:48px;">'
        + render_glory_of_days(glory)
        + render_artifact_spread(spread)
        + "</div>"
        + render_book_of_knowledge()
        + '<div class="footer">Пусть Восемь хранят сборку.</div>'
    )

    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tether Saga — Хроники Драконорождённого</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="{google_fonts_url}" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
<style>
  :root {{
    --bg:{bg}; --bg2:{bg2}; --gold:{gold}; --gold-dim:{gold_dim};
    --blood:{blood}; --text:{text_p}; --muted:{muted};
    --epic:{epic}; --rare:{rare}; --unc:{unc}; --leg:{gold};
  }}
  * {{ box-sizing: border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--text); font-family:'{font_b}', Georgia, serif; line-height:1.5; padding:32px; }}
  h1, h2, h3 {{ font-family:'{font_h}', serif; color:var(--gold); text-shadow:0 0 8px rgba(212,175,55,.25); margin:0 0 12px; letter-spacing:.04em; }}
  h1 {{ font-size:42px; text-align:center; }}
  h2 {{ font-size:26px; border-bottom:1px double var(--gold-dim); padding-bottom:8px; margin-top:48px; }}
  .sub {{ text-align:center; color:var(--muted); font-style:italic; margin-bottom:6px; font-size:18px; }}
  .container {{ max-width:1200px; margin:0 auto; }}
  .frame {{ border:2px double var(--gold-dim); padding:24px; background:var(--bg2); position:relative; }}
  .frame::before, .frame::after {{ content:'❧'; position:absolute; color:var(--gold-dim); font-size:18px; }}
  .frame::before {{ top:-2px; left:8px; }}
  .frame::after {{ bottom:-12px; right:8px; }}
  .num, .mono {{ font-family:'{font_n}', monospace; }}
  .muted {{ color:var(--muted); }}

  .character {{ display:grid; grid-template-columns:1fr 1fr; gap:24px; }}
  .char-left .row {{ margin:8px 0; }}
  .char-left .lbl {{ color:var(--muted); font-size:14px; letter-spacing:.1em; text-transform:uppercase; }}
  .char-left .val {{ font-family:'{font_h}', serif; font-size:22px; color:var(--gold); }}
  .level {{ font-size:48px !important; }}
  .xp {{ height:10px; background:#0d0907; border:1px solid var(--gold-dim); margin-top:6px; }}
  .xp-fill {{ height:100%; background:linear-gradient(90deg,#6a5418,#d4af37); }}
  .char-right .lbl {{ color:var(--muted); font-size:14px; letter-spacing:.1em; text-transform:uppercase; }}

  .compendium {{ display:grid; grid-template-columns:1fr 1fr; gap:24px; }}
  .compendium h3 {{ font-family:'{font_h}', serif; color:var(--gold); font-size:16px; margin:0 0 8px; letter-spacing:.05em; }}
  .compendium .entry {{ margin-bottom:16px; font-size:14px; }}
  .compendium code {{ font-family:'{font_n}', monospace; background:#0d0907; padding:1px 6px; color:var(--gold); border:1px solid #2a2018; }}
  .compendium p {{ margin:4px 0; color:var(--text); }}
  .compendium .small {{ color:var(--muted); font-style:italic; font-size:12px; }}
  .three-col {{ display:grid; grid-template-columns:1fr 1fr 1fr; gap:24px; }}
  .duty-summary {{ display:grid; grid-template-columns:1fr 1fr 1fr; gap:24px; text-align:center; margin-bottom:18px; }}
  .duty-col {{ padding:12px; border:1px solid var(--gold-dim); background:#1f1812; }}
  .duty-num {{ font-size:48px; color:var(--gold); line-height:1; }}
  .duty-lbl {{ font-family:'{font_h}', serif; color:var(--text); margin-top:8px; letter-spacing:.05em; font-size:14px; }}
  .duty-pct {{ color:var(--muted); font-size:14px; margin-top:4px; }}
  .duty-bar {{ display:flex; height:18px; border:1px solid var(--gold-dim); background:#0d0907; margin-top:12px; overflow:hidden; }}
  .duty-bar-fill.planned {{ background:linear-gradient(90deg,#6a5418,#d4af37); }}
  .duty-bar-fill.wild    {{ background:linear-gradient(90deg,#5a3a52,#9b4dca); }}
  .duty-legend {{ display:flex; gap:32px; margin-top:10px; font-size:13px; color:var(--muted); }}
  .duty-legend .dot {{ display:inline-block; width:12px; height:12px; margin-right:6px; vertical-align:middle; }}
  .duty-legend .dot-planned {{ background:#d4af37; }}
  .duty-legend .dot-wild    {{ background:#9b4dca; }}
  .duty-lists {{ display:grid; grid-template-columns:1fr 1fr; gap:24px; margin-top:24px; }}
  .duty-h3 {{ font-family:'{font_h}', serif; color:var(--gold); font-size:15px; margin:0 0 10px; letter-spacing:.05em; }}
  .duty-list {{ list-style:none; padding:0; margin:0; }}
  .duty-list li {{ padding:7px 0; border-bottom:1px solid #2a2018; font-size:14px; }}
  .duty-list .prnum {{ display:inline-block; width:54px; color:var(--gold); }}
  .graph-legend {{ display:flex; flex-wrap:wrap; gap:18px 28px; margin-top:14px; font-size:13px; color:var(--muted); align-items:center; }}
  .schools-legend {{ display:grid; grid-template-columns:1fr 1fr; gap:6px 24px; margin-top:14px; padding-top:12px; border-top:1px dashed var(--gold-dim); font-size:13px; }}
  .schools-legend .school-name {{ font-family:'{font_h}', serif; color:var(--gold); letter-spacing:.05em; }}
  #graph-box {{ position:relative; }}
  #graph-box svg {{ width:100%; height:100%; cursor:grab; background:#15100c; border:1px solid #2a2018; }}
  #graph-box svg:active {{ cursor:grabbing; }}
  .graph-controls {{ position:absolute; top:10px; right:14px; z-index:5; display:flex; gap:6px; align-items:center; }}
  .graph-controls button {{ background:#1f1812; border:1px solid var(--gold-dim); color:var(--gold); font-family:'{font_n}', monospace; font-size:18px; width:32px; height:32px; cursor:pointer; }}
  .graph-controls button:hover {{ background:#2a2018; }}
  .cluster-label {{
    font-family:'{font_h}', serif; fill:#8a7028; font-size:13px;
    letter-spacing:.12em; text-transform:uppercase; pointer-events:none;
    opacity:.85; paint-order:stroke;
    stroke:#15100c; stroke-width:4px; stroke-linejoin:round;
  }}
  .graph-tooltip {{
    position:absolute; pointer-events:none; z-index:10;
    background:#1a1410; border:1px solid var(--gold-dim);
    padding:8px 12px; max-width:320px; min-width:160px;
    box-shadow:0 4px 16px rgba(0,0,0,.6);
    font-family:'{font_b}', serif; color:var(--text);
    font-size:13px; line-height:1.4;
    opacity:0; transition:opacity .12s; transform:translate(0,-100%);
    white-space:normal;
  }}
  .graph-tooltip.show {{ opacity:1; }}
  .graph-tooltip .tt-num {{ font-family:'{font_n}', monospace; color:var(--gold); font-size:14px; margin-right:6px; }}
  .graph-tooltip .tt-meta {{ font-size:11px; color:var(--muted); margin-top:4px; letter-spacing:.04em; }}
  #graph-box .gn {{ cursor:pointer; }}
  #graph-box .gn-free {{ fill:#d4af37; }}
  #graph-box .gn-blocked {{ fill:#c9302c; }}
  #graph-box .gn-orphan {{ fill:#e8d070; stroke:#d4af37; stroke-width:1; }}
  #graph-box .gn-closed {{ fill:#3a2e1c; stroke:#6a5a40; stroke-width:1; }}
  #graph-box .gn-label {{
    font-family:'{font_n}', monospace; font-size:9px;
    fill:#e8d8a8; pointer-events:none;
    paint-order:stroke; stroke:#15100c; stroke-width:3px; stroke-linejoin:round;
  }}
  #graph-box .ge-parent {{ stroke:#8a7028; stroke-width:1; opacity:.6; }}
  #graph-box .ge-block {{ stroke:#c9302c; stroke-width:1.2; stroke-dasharray:4 3; opacity:.7; }}
  .chart-box {{ position:relative; height:300px; }}
  .chart-box.tall {{ height:360px; }}
  .chart-box.short {{ height:240px; }}

  table.scroll {{ width:100%; border-collapse:collapse; }}
  table.scroll td, table.scroll th {{ padding:10px 12px; border-bottom:1px solid #2a2018; vertical-align:top; }}
  table.scroll tr.done td {{ color:var(--gold-dim); }}
  table.scroll tr.active td {{ color:var(--gold); }}
  table.scroll tr.todo td {{ color:var(--muted); font-style:italic; }}
  .ch {{ font-family:'{font_h}', serif; width:90px; }}
  .qname {{ font-family:'{font_h}', serif; font-size:17px; }}
  .qepic {{ font-style:italic; color:var(--muted); font-size:14px; }}
  .qstat {{ width:170px; }}
  .mvp-bar {{ height:6px; background:#0d0907; border:1px solid #2a2018; margin-top:6px; }}
  .mvp-fill {{ height:100%; background:linear-gradient(90deg,#6a5418,#d4af37); }}
  tr.done .mvp-fill {{ background:linear-gradient(90deg,#8a6820,#d4af37); }}
  tr.todo .mvp-fill {{ background:#3a2e1c; }}
  .mvp-pct {{ font-size:12px; color:var(--muted); margin-top:3px; }}
  .qev {{ font-size:13px; color:var(--muted); max-width:340px; }}

  .locations {{ display:grid; grid-template-columns:repeat(3,1fr); gap:14px; }}
  .loc {{ border:1px solid var(--gold-dim); padding:14px; background:#1f1812; }}
  .loc.dim {{ opacity:.45; }}
  .loc-name {{ font-family:'{font_h}', serif; color:var(--gold); font-size:18px; }}
  .loc-ss {{ font-family:'{font_n}', monospace; color:var(--muted); font-size:12px; }}
  .loc-stats {{ margin-top:6px; font-family:'{font_n}', monospace; color:var(--text); }}
  .loc-desc {{ margin-top:6px; font-style:italic; color:var(--muted); font-size:13px; }}

  .artifacts {{ display:grid; grid-template-columns:repeat(auto-fit, minmax(280px,1fr)); gap:16px; }}
  .art {{ border:2px solid; padding:16px; background:#1c1510; position:relative; }}
  .art-rarity {{ position:absolute; top:-12px; left:14px; background:var(--bg); padding:0 8px; font-family:'{font_h}', serif; letter-spacing:.1em; font-size:13px; }}
  .art-legendary {{ border-color:var(--leg); box-shadow:0 0 18px rgba(212,175,55,.45); }}
  .art-legendary .art-rarity {{ color:var(--leg); }}
  .art-epic {{ border-color:var(--epic); }}
  .art-epic .art-rarity {{ color:var(--epic); }}
  .art-rare {{ border-color:var(--rare); }}
  .art-rare .art-rarity {{ color:var(--rare); }}
  .art-uncommon {{ border-color:var(--unc); }}
  .art-uncommon .art-rarity {{ color:var(--unc); }}
  .art-common {{ border-color:#6a5a40; }}
  .art-common .art-rarity {{ color:#a89770; }}
  .art-name {{ font-family:'{font_h}', serif; font-size:15px; color:var(--gold); margin-top:4px; }}
  .art-stats {{ font-family:'{font_n}', monospace; font-size:12px; color:var(--muted); margin:8px 0 0; padding:0; list-style:none; line-height:1.7; }}
  .art-stats li {{ padding:0; }}
  .art-stats b {{ color:var(--text); font-weight:normal; }}
  .art-flavor {{ font-style:italic; color:var(--text); margin-top:8px; font-size:14px; }}

  ul.quests {{ list-style:none; padding:0; margin:0; }}
  .qmark {{ display:inline-block; width:24px; color:var(--gold); font-size:18px; }}
  ul.quests li {{ padding:6px 0; }}
  .blood {{ color:var(--blood); }}
  .small {{ font-family:'{font_b}', serif; font-size:14px; font-weight:normal; }}
  table.stat-table {{ width:100%; border-collapse:collapse; font-size:16px; }}
  table.stat-table th {{ font-family:'{font_h}', serif; font-size:13px; color:var(--muted); font-weight:normal; text-align:left; padding:8px 10px; border-bottom:1px solid var(--gold-dim); letter-spacing:.08em; text-transform:uppercase; }}
  table.stat-table th.r, table.stat-table td.prval {{ text-align:right; }}
  table.stat-table td {{ padding:11px 10px; border-bottom:1px solid #2a2018; vertical-align:middle; }}
  table.stat-table .prnum {{ color:var(--gold); width:64px; font-size:15px; }}
  table.stat-table .prtitle {{ color:var(--text); font-size:15px; line-height:1.4; font-family:'{font_b}', serif; }}
  table.stat-table .prval {{ color:var(--gold); width:110px; font-size:15px; }}

  .two-col {{ display:grid; grid-template-columns:1fr 1fr; gap:32px; }}

  .footer {{ text-align:center; color:var(--muted); margin-top:48px; padding:16px; font-style:italic; }}
</style>
</head>
<body>
<div class="container">
{body_sections}
</div>
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
    sprint_path = Path(args.sprint)
    sprint_title, sprint_issues = parse_sprint(sprint_path)
    sprints_dir = sprint_path.parent
    raw["active_sprint_cutoff"] = _git_added_date(sprint_path)

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render_html(raw, mvp_chapters, sprint_title, sprint_issues, assets, today, sprints_dir), encoding="utf-8")
    print(f"Written: {out}")


if __name__ == "__main__":
    main()
