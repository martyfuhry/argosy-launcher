#!/usr/bin/env python3
"""Argosy smell guard. PostToolUse hook on Edit/Write: checks the just-written
content against scripts/ci/smell-rules.json (the same rules CI enforces) and
feeds violations back so the agent self-corrects at write time. Fails open on
any internal error."""

import json
import os
import re
import sys


def glob_to_re(glob):
    out, i = [], 0
    while i < len(glob):
        c = glob[i]
        if glob[i : i + 3] == "**/":
            out.append("(?:.*/)?")
            i += 3
        elif glob[i : i + 2] == "**":
            out.append(".*")
            i += 2
        elif c == "*":
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(c))
            i += 1
    return re.compile("".join(out) + r"\Z")


def matches_any(path, globs):
    return any(glob_to_re(g).match(path) for g in globs)


KDOC_PATHS = ["app/src/main/**/*.kt", "libretrodroid/src/**/*.kt"]

PROSE_PATHS = [
    "**/*.conf", "**/*.gradle", "**/*.gradle.kts", "**/*.pro",
    "**/*.properties", "**/*.sh", "**/*.xml", "**/*.json",
]
PROSE_COMMENT_RE = re.compile(r"^\s*(//|#|<!--)")
MAX_PROSE_COMMENT_LINES = 4

DECL_RE = re.compile(
    r"^\s*(?:(?P<vis>public|internal|private|protected)\s+)?"
    r"(?:(?:suspend|inline|noinline|crossinline|open|override|abstract|final|sealed|data|value|"
    r"annotation|enum|external|infix|operator|tailrec|const|lateinit|companion|expect|actual|"
    r"tailrec|vararg|reified)\s+)*"
    r"(?P<kind>fun|val|var|class|object|interface|typealias)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)

NARRATIVE_TELLS = [
    "used to", "previously", "which is why", "would have", "turned out",
    "meant that", "no longer", "historically", "the old", "before this",
    "the point is", "worse than", "better than", "the fix", "we", "our",
    "it is worth", "note that", "in practice", "let", "used to be",
    "this used", "originally", "at one point", "for now", "as discussed",
    "temporarily", "TODO", "FIXME", "XXX", "HACK",
    "because", "rather than", "instead of", "so that", "in order to",
    "to avoid", "which means", "this ensures", "that way", "otherwise",
    "the reason", "not just", "on purpose", "deliberately", "intentionally",
]

NARRATIVE_RE = re.compile(
    "|".join(r"\b" + re.escape(t).replace(r"\ ", r"\s+") + r"\b" for t in NARRATIVE_TELLS),
    re.IGNORECASE,
)

STOPWORDS = {
    "a", "an", "the", "of", "for", "to", "in", "on", "is", "are", "and", "or",
    "that", "this", "it", "its", "as", "by", "with", "from", "at", "be", "was",
    "one", "each", "every", "all", "any", "when", "which", "what", "how",
    "whether", "there", "has", "have", "had", "does", "do", "not", "no",
    "only", "ever", "never", "if", "then", "else", "than", "so", "but",
    "into", "over", "under", "up", "down", "out", "off", "per", "via", "also",
    "just", "still", "already", "may", "can", "will", "would", "should",
    "must", "here", "these", "those", "them", "they", "some", "such", "same",
}

WORD_RE = re.compile(r"[A-Za-z]+")


def split_identifier(name):
    parts = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).replace("_", " ")
    return {w.lower() for w in WORD_RE.findall(parts)}


def kdoc_blocks(lines):
    blocks, i = [], 0
    while i < len(lines):
        if lines[i].lstrip().startswith("/**"):
            start = i
            body = []
            while i < len(lines):
                body.append(lines[i])
                if "*/" in lines[i] and not (i == start and lines[i].lstrip() == "/**"):
                    break
                if i > start and "*/" in lines[i]:
                    break
                i += 1
            blocks.append((start, i, body))
        i += 1
    return blocks


def documented_declaration(lines, end):
    j = end + 1
    while j < len(lines):
        stripped = lines[j].strip()
        if not stripped or stripped.startswith("@"):
            j += 1
            continue
        return lines[j]
    return None


def prose_findings(text, rel, added=None):
    if not matches_any(rel, PROSE_PATHS):
        return []

    out = []
    run = []
    for line in text.splitlines():
        if PROSE_COMMENT_RE.match(line):
            run.append(line)
            continue
        out.extend(flag_prose_run(run, added))
        run = []
    out.extend(flag_prose_run(run, added))
    return out


def flag_prose_run(run, added):
    if len(run) <= MAX_PROSE_COMMENT_LINES:
        return []
    if added is not None and not any(ln in added for ln in run):
        return []
    return [(
        "prose-comment-block",
        "A comment block this long in a config or build file is narration. State the "
        "constraint in a line, or delete it and put the reasoning in the commit message.",
        "{} lines starting: {}".format(len(run), run[0].strip()[:90]),
    )]


def kdoc_findings(text, rel, added=None):
    if not matches_any(rel, KDOC_PATHS):
        return []

    lines = text.splitlines()
    out = []

    for _, end, body in kdoc_blocks(lines):
        if added is not None and not any(ln in added for ln in body):
            continue
        prose = " ".join(
            ln.strip().lstrip("/*").lstrip("*").strip() for ln in body
        ).replace("*/", " ").strip()
        content_lines = [
            ln for ln in body
            if ln.strip().strip("/*").strip("*").strip() and not ln.strip() in ("/**", "*/")
        ]

        decl = documented_declaration(lines, end)
        if decl is None:
            out.append((
                "kdoc-not-on-declaration",
                "A KDoc that does not sit directly above a declaration is an inline comment "
                "wearing a docblock. Delete it.",
                prose[:110],
            ))
            continue

        m = DECL_RE.match(decl)
        if not m:
            out.append((
                "kdoc-not-on-declaration",
                "A KDoc that does not sit directly above a declaration is an inline comment "
                "wearing a docblock. Delete it.",
                decl.strip()[:110],
            ))
            continue

        if m.group("vis") in ("private", "protected"):
            out.append((
                "kdoc-on-non-public",
                "KDoc is for non-obvious PUBLIC contracts. A private declaration explains itself "
                "in code or needs a better name. Delete it.",
                decl.strip()[:110],
            ))
            continue

        hits = sorted({m.group(0).lower() for m in NARRATIVE_RE.finditer(prose)})
        if hits:
            out.append((
                "kdoc-narrative",
                "KDoc states WHAT the declaration is, nothing else. Rationale, history, and "
                "what the code used to do belong in the commit message. Found: "
                + ", ".join(hits[:4]),
                prose[:110],
            ))
            continue

        if len(content_lines) > 5:
            out.append((
                "kdoc-too-long",
                "A KDoc past four lines is prose. Say what it is in one or two sentences or "
                "delete it.",
                prose[:110],
            ))
            continue

        doc_words = {w.lower() for w in WORD_RE.findall(prose)} - STOPWORDS
        name_words = split_identifier(m.group("name"))
        if doc_words and len(doc_words - name_words) <= 1:
            out.append((
                "kdoc-restates-name",
                "The declaration already says this. Delete the KDoc.",
                prose[:110],
            ))

    return out


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    if (payload.get("tool_name") or "") not in ("Edit", "Write"):
        sys.exit(0)

    tool_input = payload.get("tool_input") or {}
    file_path = tool_input.get("file_path", "") or ""
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    rel = os.path.relpath(file_path, root) if file_path.startswith(root) else file_path

    if payload["tool_name"] == "Edit":
        text = tool_input.get("new_string")
        if not text:
            sys.exit(0)
        carried = set((tool_input.get("old_string") or "").splitlines())
        lines = [ln for ln in text.splitlines() if ln not in carried]
        block_text = text
        added_lines = set(lines)
        text = "\n".join(lines)
    else:
        text = tool_input.get("content")
        block_text = text
        added_lines = None
    if not text:
        sys.exit(0)

    try:
        with open(os.path.join(root, "scripts", "ci", "smell-rules.json")) as f:
            rules = json.load(f)["rules"]
    except Exception:
        sys.exit(0)

    findings = []
    for rule in rules:
        if not matches_any(rel, rule["path_include"]):
            continue
        if matches_any(rel, rule.get("path_exclude", [])):
            continue
        pat = re.compile(rule["added_regex"])
        skip = re.compile(rule["skip_line_regex"]) if rule.get("skip_line_regex") else None
        for line in text.splitlines():
            if skip and skip.search(line):
                continue
            if pat.search(line):
                findings.append((rule, line.strip()))

    try:
        doc_findings = kdoc_findings(block_text, rel, added_lines)
        doc_findings += prose_findings(block_text, rel, added_lines)
    except Exception:
        doc_findings = []

    if not findings and not doc_findings:
        sys.exit(0)

    lines = ["SMELL GUARD: the content just written violates house rules:"]
    seen = set()
    for rule, line in findings:
        if rule["id"] not in seen:
            lines.append("[{}] {}".format(rule["id"], rule["message"]))
            seen.add(rule["id"])
        lines.append("    {}".format(line[:120]))

    if doc_findings:
        for rule_id, message, snippet in doc_findings:
            if rule_id not in seen:
                lines.append("[{}] {}".format(rule_id, message))
                seen.add(rule_id)
            lines.append("    {}".format(snippet))
        lines.append(
            "Default is ZERO comments. Before you keep any KDoc, it must pass all four:"
        )
        lines.append("    1. Does it describe the declaration, and only what it does?")
        lines.append("    2. Does it add value the code does not already carry?")
        lines.append("    3. Does the name already say it? Then delete it.")
        lines.append("    4. Is it an inline comment disguised as a docblock? Then delete it.")
        lines.append("Deleting is the expected outcome. Rewording is not a fix.")

    lines.append("Fix the flagged lines now; CI enforces the same rules on the PR diff.")
    sys.stderr.write("\n".join(lines) + "\n")
    sys.exit(2)


if __name__ == "__main__":
    main()
