#!/usr/bin/env python3
"""Cost / context health report over Claude Code session transcripts.

A session is `<dir>/<id>.jsonl` (orchestrator main thread) plus an optional
`<dir>/<id>/subagents/agent-*.jsonl` (one file per dispatched sub-agent, with a
sibling `.meta.json` giving its agentType). Sub-agent token spend is invisible
from the main-thread file alone — it lives only in the subagents folder.

Output is a bounded aggregate (summary + top offenders), never a per-session
dump, so running it over the whole corpus does not blow up the caller's context.

Usage:
  session-cost.py --session <path-to-.jsonl>      # one session, detailed
  session-cost.py --latest <project-dir>          # newest session in a dir
  session-cost.py --all <glob> [<glob> ...]       # aggregate over many dirs
"""
import argparse
import glob
import json
import os

# Per-Mtok pricing by model tier. cache_write = 1.25× input (5-min TTL),
# cache_read = 0.1× input. REVISIT when Anthropic pricing changes. Long-context
# (>200K) premium is not modelled — treat costs as a lower bound.
PRICE = {
    "opus": {"in": 5.0, "out": 25.0, "cc": 6.25, "cr": 0.50},
    "sonnet": {"in": 3.0, "out": 15.0, "cc": 3.75, "cr": 0.30},
    "haiku": {"in": 1.0, "out": 5.0, "cc": 1.25, "cr": 0.10},
}


def price_for(model):
    m = (model or "opus").lower()
    if "haiku" in m:
        return PRICE["haiku"]
    if "sonnet" in m:
        return PRICE["sonnet"]
    return PRICE["opus"]  # opus / fable / unknown → top tier

# Health thresholds (heuristic — treat a fail as a prompt to look, not a verdict).
PEAK_WARN, PEAK_FAIL = 400_000, 600_000          # main-thread context tokens
ORCH_SHARE_WARN, ORCH_SHARE_FAIL = 0.75, 0.85    # orchestrator / total cost
CC_RATIO_WARN = 0.10                             # cache_create / cache_read
TURNS_WARN = 500                                 # main-thread assistant turns
# Agent types that should never run on an Opus tier (cost tiering guard).
CHEAP_AGENTS_OPUS_FORBIDDEN = {
    "review-dod", "review-glossary", "review-design-system",
    "review-guides", "review-tests", "review-correctness",
    "review-platform", "review-reuse", "coder",
}


def cost(model, out, cc, cr, inp=0):
    p = price_for(model)
    return (out * p["out"] + cc * p["cc"] + cr * p["cr"] + inp * p["in"]) / 1e6


def _usage_rows(jf):
    """Yield (model, usage_dict) for each assistant message carrying usage."""
    with open(jf, encoding="utf-8") as f:
        for line in f:
            try:
                o = json.loads(line)
            except (ValueError, TypeError):
                continue
            if o.get("type") != "assistant":
                continue
            m = o.get("message", {})
            u = m.get("usage")
            if u:
                yield o.get("isSidechain", False), m.get("model"), u


def main_thread(jf):
    out = cc = cr = inp = turns = peak = 0
    agent_dispatches = 0
    model = None
    with open(jf, encoding="utf-8") as f:
        for line in f:
            try:
                o = json.loads(line)
            except (ValueError, TypeError):
                continue
            msg = o.get("message", {})
            content = msg.get("content")
            if isinstance(content, list):
                for b in content:
                    if (isinstance(b, dict) and b.get("type") == "tool_use"
                            and b.get("name") in ("Agent", "Task")):
                        agent_dispatches += 1
            if o.get("type") != "assistant" or o.get("isSidechain"):
                continue
            u = msg.get("usage")
            if not u:
                continue
            mdl = msg.get("model")
            if mdl and mdl != "<synthetic>":
                model = mdl
            turns += 1
            o_, cc_, cr_, i_ = (u.get("output_tokens", 0),
                                u.get("cache_creation_input_tokens", 0),
                                u.get("cache_read_input_tokens", 0),
                                u.get("input_tokens", 0))
            out += o_; cc += cc_; cr += cr_; inp += i_
            peak = max(peak, i_ + cr_ + cc_)
    return dict(out=out, cc=cc, cr=cr, inp=inp, turns=turns, peak=peak,
                model=model, dispatches=agent_dispatches)


def subagents(session_jsonl):
    subdir = session_jsonl[:-6] + "/subagents"  # strip ".jsonl"
    by_type = {}
    tot = dict(out=0, cc=0, cr=0, inp=0, n=0, cost=0.0)
    if not os.path.isdir(subdir):
        return tot, by_type
    for jf in glob.glob(os.path.join(subdir, "*.jsonl")):
        meta = jf[:-6] + ".meta.json"
        atype = "?"
        if os.path.exists(meta):
            try:
                with open(meta, encoding="utf-8") as fh:
                    atype = json.load(fh).get("agentType", "?")
            except (ValueError, OSError):
                pass
        out = cc = cr = inp = 0
        model = None
        for side, mdl, u in _usage_rows(jf):
            if mdl and mdl != "<synthetic>":
                model = mdl
            out += u.get("output_tokens", 0)
            cc += u.get("cache_creation_input_tokens", 0)
            cr += u.get("cache_read_input_tokens", 0)
            inp += u.get("input_tokens", 0)
        c = cost(model, out, cc, cr, inp)  # price each file at its own tier
        d = by_type.setdefault(atype, dict(out=0, cc=0, cr=0, n=0, cost=0.0, models=set()))
        d["out"] += out; d["cc"] += cc; d["cr"] += cr; d["n"] += 1; d["cost"] += c
        if model:
            d["models"].add(model.replace("claude-", ""))
        tot["out"] += out; tot["cc"] += cc; tot["cr"] += cr
        tot["inp"] += inp; tot["n"] += 1; tot["cost"] += c
    return tot, by_type


def analyze(session_jsonl):
    mt = main_thread(session_jsonl)
    sub_tot, by_type = subagents(session_jsonl)
    orch_cost = cost(mt["model"], mt["out"], mt["cc"], mt["cr"], mt["inp"])
    sub_cost = sub_tot["cost"]
    total = orch_cost + sub_cost
    share = orch_cost / total if total else 0.0
    cc_ratio = mt["cc"] / mt["cr"] if mt["cr"] else 0.0
    flags = []
    if mt["peak"] >= PEAK_FAIL:
        flags.append("FAIL:peak")
    elif mt["peak"] >= PEAK_WARN:
        flags.append("warn:peak")
    if total > 1 and share >= ORCH_SHARE_FAIL:
        flags.append("FAIL:orch-share")
    elif total > 1 and share >= ORCH_SHARE_WARN:
        flags.append("warn:orch-share")
    if cc_ratio >= CC_RATIO_WARN:
        flags.append("warn:cold-cache")
    if mt["turns"] >= TURNS_WARN:
        flags.append("warn:turns")
    for atype, d in by_type.items():
        if atype in CHEAP_AGENTS_OPUS_FORBIDDEN and any("opus" in m for m in d["models"]):
            flags.append(f"FAIL:model[{atype}=opus]")
    return dict(path=session_jsonl, mt=mt, sub_tot=sub_tot, by_type=by_type,
                orch_cost=orch_cost, sub_cost=sub_cost, total=total,
                share=share, cc_ratio=cc_ratio, flags=flags)


def _pct(vals, p):
    if not vals:
        return 0
    s = sorted(vals)
    return s[min(len(s) - 1, int(p / 100 * len(s)))]


def report_one(a):
    mt = a["mt"]
    print(f"session: {os.path.basename(a['path'])}")
    print(f"  model={mt['model']}  main-turns={mt['turns']}  agent-dispatches={mt['dispatches']}  subagent-runs={a['sub_tot']['n']}")
    print(f"  peak main ctx = {mt['peak']:,} tok")
    print(f"  cost: orchestrator ${a['orch_cost']:,.0f} ({a['share']*100:.0f}%) | subagents ${a['sub_cost']:,.0f} | total ${a['total']:,.0f}")
    print(f"  cache_create/read ratio = {a['cc_ratio']:.3f}")
    if a["by_type"]:
        worst = sorted(a["by_type"].items(), key=lambda x: -(x[1]["cr"] + x[1]["cc"]))[:6]
        print("  top subagents:", ", ".join(
            f"{t}×{d['n']}({'/'.join(sorted(d['models'])) or '?'})" for t, d in worst))
    print(f"  verdict: {' '.join(a['flags']) if a['flags'] else 'OK'}")


def report_all(analyses):
    analyses = [a for a in analyses if a["mt"]["turns"] > 0]
    n = len(analyses)
    orch_sessions = [a for a in analyses if a["sub_tot"]["n"] > 0]
    peaks = [a["mt"]["peak"] for a in analyses]
    shares = [a["share"] for a in orch_sessions]
    total_cost = sum(a["total"] for a in analyses)
    print(f"sessions scanned: {n}  (with sub-agent dispatch: {len(orch_sessions)})")
    print(f"total est. cost: ${total_cost:,.0f}  (lower bound — no long-context premium)")
    print(f"peak main ctx   median={_pct(peaks,50):,}  p90={_pct(peaks,90):,}  max={max(peaks):,}")
    if shares:
        print(f"orch cost share median={_pct(shares,50)*100:.0f}%  p90={_pct(shares,90)*100:.0f}%")
    buckets = {"green<250K": 0, "250-400K": 0, "400-600K": 0, ">600K": 0}
    for p in peaks:
        if p < 250_000: buckets["green<250K"] += 1
        elif p < 400_000: buckets["250-400K"] += 1
        elif p < 600_000: buckets["400-600K"] += 1
        else: buckets[">600K"] += 1
    print("peak-ctx distribution:", "  ".join(f"{k}={v}" for k, v in buckets.items()))
    anomalies = {}
    for a in orch_sessions:
        for atype, d in a["by_type"].items():
            if atype in CHEAP_AGENTS_OPUS_FORBIDDEN and any("opus" in m for m in d["models"]):
                anomalies.setdefault(atype, set()).update(m for m in d["models"] if "opus" in m)
    print("model-tier anomalies:", dict(anomalies) if anomalies else "none")
    print("\ntop sessions by est. cost:")
    print(f"  {'cost':>8}{'orch%':>7}{'peakCtx':>10}{'turns':>7}{'disp':>5}  session")
    for a in sorted(analyses, key=lambda x: -x["total"])[:12]:
        print(f"  ${a['total']:>6,.0f}{a['share']*100:>6.0f}%{a['mt']['peak']:>10,}"
              f"{a['mt']['turns']:>7}{a['mt']['dispatches']:>5}  "
              f"{os.path.basename(os.path.dirname(a['path']))[:30]}/{os.path.basename(a['path'])[:12]}")


def iter_sessions(globs):
    for g in globs:
        for d in glob.glob(os.path.expanduser(g)):
            if os.path.isdir(d):
                for jf in glob.glob(os.path.join(d, "*.jsonl")):
                    yield jf
            elif d.endswith(".jsonl"):
                yield d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--session")
    ap.add_argument("--latest")
    ap.add_argument("--all", nargs="+")
    args = ap.parse_args()
    if args.session:
        report_one(analyze(args.session))
    elif args.latest:
        files = glob.glob(os.path.join(os.path.expanduser(args.latest), "*.jsonl"))
        if not files:
            print("no sessions found")
            return
        report_one(analyze(max(files, key=os.path.getmtime)))
    elif args.all:
        analyses = [analyze(jf) for jf in iter_sessions(args.all)]
        report_all(analyses)
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
