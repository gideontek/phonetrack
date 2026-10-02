#!/usr/bin/env python3
"""Builds the release-gate report from what the gate collected and decides pass or fail.

usage: gate_report.py <report dir> <version> <sha> [--update-skips]

Reads <dir>/steps.tsv (status, seconds, name for lint, unit tests, F-Droid guard) and, for each
<dir>/api-<N>/: summary.txt (run-e2e.sh rows) and instrumented/*.xml (JUnit results). Writes
<dir>/report.md and exits 1 if anything failed or a test was skipped that is not expected.
Expected skips: the phase tests (skipped by design outside their scenario) and, per API level, the
names in scripts/gate-baseline/skips-api<N>.txt (known device limitations, reviewed by a person).
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

report, version, sha = sys.argv[1], sys.argv[2], sys.argv[3]
update = "--update-skips" in sys.argv
baseline_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gate-baseline")
failed = False
lines = [f"# Release gate: {version} ({sha})", ""]


def rows(path):
    if not os.path.exists(path):
        return []
    return [l.rstrip("\n").split("\t", 2) for l in open(path) if l.strip()]


lines += ["## Build checks", "", "| Result | Seconds | Step |", "|---|---|---|"]
for r in rows(os.path.join(report, "steps.tsv")):
    status, secs, name = (r + ["", "", ""])[:3]
    failed |= status != "PASS"
    lines.append(f"| {status} | {secs} | {name} |")

apis = sorted((int(os.path.basename(d)[4:]) for d in glob.glob(os.path.join(report, "api-*"))), reverse=True)
unexpected_all = []
for api in apis:
    d = os.path.join(report, f"api-{api}")
    lines += ["", f"## API {api}", "", "| Result | Step |", "|---|---|"]
    for r in rows(os.path.join(d, "summary.txt")):
        status, name = (r + ["", ""])[:2]
        failed |= status != "PASS"
        lines.append(f"| {status} | {name} |")

    tests = fails = 0
    skipped = []
    for f in glob.glob(os.path.join(d, "instrumented", "*.xml")):
        for case in ET.parse(f).getroot().iter("testcase"):
            tests += 1
            if case.find("failure") is not None or case.find("error") is not None:
                fails += 1
            if case.find("skipped") is not None:
                skipped.append(f"{case.get('classname')}.{case.get('name')}")
    phase = [s for s in skipped if ".phase." in s]
    other = sorted(s for s in skipped if ".phase." not in s)
    base_file = os.path.join(baseline_dir, f"skips-api{api}.txt")
    if update:
        if other:
            open(base_file, "w").write("\n".join(other) + "\n")
        elif os.path.exists(base_file):
            os.remove(base_file)
        expected = set(other)
    else:
        expected = set(l.strip() for l in open(base_file)) if os.path.exists(base_file) else set()
    unexpected = [s for s in other if s not in expected]
    missing = [s for s in expected if s not in other]
    unexpected_all += [f"API {api}: {s}" for s in unexpected]
    failed |= bool(unexpected) or fails > 0
    lines += ["", f"Instrumented: {tests} tests, {fails} failed, {len(skipped)} skipped "
              f"({len(phase)} phase tests by design, {len(other)} other)."]
    if any(s in expected for s in other):
        lines += ["", "Skipped (expected for this API level, from `scripts/gate-baseline`):"]
        lines += [f"- {s}" for s in other if s in expected]
    if unexpected:
        lines += ["", "**UNEXPECTED skips (fail the gate):**"] + [f"- {s}" for s in unexpected]
    if missing:
        lines += ["", "Expected skips that ran this time (update the baseline if intended):"] + [f"- {s}" for s in missing]
    shots = glob.glob(os.path.join(d, "screens", "*.png"))
    if shots:
        lines += ["", f"Screenshots to look through: `{os.path.relpath(os.path.join(d, 'screens'), report)}/` ({len(shots)} images)."]

lines += ["", "## Verdict", "", "**GATE FAILED**" if failed else "**GATE PASSED** (the manual items in RELEASE_CHECKLIST.md are still to do)"]
open(os.path.join(report, "report.md"), "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(1 if failed else 0)
