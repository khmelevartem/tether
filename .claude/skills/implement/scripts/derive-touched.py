#!/usr/bin/env python3
# Derives the `touched` bucket set for classify-state.sh from a newline-separated
# file list on stdin and the adapter config at .claude/project.json.
# Usage: derive-touched.py <path-to-project.json>
# Output: comma-separated sorted bucket names (ui, code, platform, docs, engdoc,
# claude, ux-brief), possibly empty.

import json
import re
import sys


def derive(lines, buckets):
    result = set()

    platform_alt = "|".join(buckets.get("platformSourceSets", []))
    test_sourceset_alt = "|".join(buckets.get("testSourceSets", []))
    test_sourceset_re = re.compile("/(" + test_sourceset_alt + ")/") if test_sourceset_alt else None
    test_filename = buckets.get("testFilename", "")
    test_filename_re = re.compile(test_filename) if test_filename else None
    ux_brief_pattern = buckets.get("uxBrief", "")
    ui_pattern = buckets.get("ui", "")
    docs_prefix = buckets.get("docsPrefix", "")
    engdoc_prefix = buckets.get("engdocPrefix", "")
    claude_prefix = buckets.get("claudePrefix", "")

    for f in lines:
        if ux_brief_pattern and re.match(ux_brief_pattern, f):
            result.add("ux-brief")
        if ui_pattern and re.match(ui_pattern, f):
            result.add("ui")
            result.add("code")
        if platform_alt and re.search("/(" + platform_alt + ")/", f):
            result.add("platform")
        is_test = (test_sourceset_re and test_sourceset_re.search(f)) or (
            test_filename_re and test_filename_re.search(f)
        )
        if re.match(r".+/src/", f) and not is_test:
            result.add("code")
        if docs_prefix and f.startswith(docs_prefix):
            result.add("docs")
            if engdoc_prefix and f.startswith(engdoc_prefix):
                result.add("engdoc")
        if claude_prefix and f.startswith(claude_prefix):
            result.add("claude")

    return result


if __name__ == "__main__":
    config_path = sys.argv[1] if len(sys.argv) > 1 else ".claude/project.json"
    try:
        with open(config_path) as f:
            config = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        config = {}

    lines = [l.strip() for l in sys.stdin.read().splitlines() if l.strip()]
    result = derive(lines, config.get("touchedBuckets", {}))
    print(",".join(sorted(result)))
