"""The SEAM Design System's tokens in Claude, built from design-tokens.css.

    python3 webapp/scripts/design-tokens.py        > tokens.json
    python3 webapp/scripts/design-tokens.py css    > tokens.css

The values live in design-tokens.css, each colour with its usage note as a comment on the same
line. The rules live in the docs-product skill, which is published unchanged as the system's
README. The artifact is derived from those two and is never edited by hand; the recipe is in the
claude-design skill, "The design system".

The type scale is docs-product's table, so its sizes are read from the CSS and its roles are
written here. Sizes, spacing and radius are read from the CSS too; only their usage words are here.
"""
import json
import os
import re
import subprocess
import sys

# Paths are relative to webapp/, so the script runs from anywhere.
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

CSS = "mc-web/src/main/resources/static/styles/design-tokens.css"
DECL = re.compile(r"^\s*--([a-z0-9-]+):\s*([^;]+?)\s*;\s*(?:/\*\s*(.*?)\s*\*/)?\s*$")

COLORS = [
    "bg-base", "bg-surface", "bg-raised", "border", "border-strong",
    "text-primary", "text-muted", "text-disabled",
    "accent", "accent-hover", "accent-muted", "on-accent",
    "green", "green-bg", "progress", "amber", "red", "red-hover", "red-bg",
]

SPACING_USAGE = {
    "space-1": "Between an icon and its text.",
    "space-2": "Tight gaps inside a row: badges, chips.",
    "space-3": "Between rows of a list, inside a card's header.",
    "space-4": "Card padding, and the mobile side gutter.",
    "space-5": "Between cards, and main's top padding.",
    "space-6": "Between page sections on desktop.",
    "space-8": "Above a page's footer, around empty states.",
    "space-10": "Hero and landing spacing only.",
}

RADIUS_USAGE = {
    "radius": "Everything: buttons, cards, inputs, modals. Buttons are never pill.",
    "radius-pill": "Badges, chips and tabs only.",
}

SIZE_USAGE = {
    "text-xs": "Metadata, timestamps.",
    "text-sm": "Table cells, secondary labels.",
    "text-base": "Body text.",
    "text-ui": "Mono badges, tags, button text.",
    "text-label": "Mono uppercase section headers.",
    "text-lg": "Prominent inline figures.",
    "text-xl": "In-page view titles.",
}

SHADOW_USAGE = {
    "shadow-card": "A resting card: a hairline lift of the same ink.",
    "shadow-pop": "A layer above the page: dropdown, popover, modal.",
}

# docs-product → Typography. (style, size token, family, weight, line height)
TYPE_STYLES = [
    ("UI", [
        ("page-title", None, "24px", 600, "1.25"),
        ("view-title", "text-xl", None, 600, "1.25"),
        ("figure", "text-lg", None, 600, "1.3"),
        ("ui", "text-ui", None, 500, "1.4"),
        ("label", "text-label", None, 500, "1.4"),
    ]),
    ("Body", [
        ("base", "text-base", None, 400, "1.5"),
        ("sm", "text-sm", None, 400, "1.5"),
        ("xs", "text-xs", None, 400, "1.4"),
    ]),
]


def declarations(css):
    out = {}
    for line in css.splitlines():
        m = DECL.match(line)
        if m:
            out[m.group(1)] = (m.group(2), m.group(3) or "")
    return out


def commit():
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def build():
    decls = declarations(open(CSS, encoding="utf-8").read())

    def need(name):
        if name not in decls:
            sys.exit(f"--{name} is not declared in {CSS}")
        return decls[name]

    colors = []
    for name in COLORS:
        value, usage = need(name)
        if not usage:
            sys.exit(f"--{name} has no usage note on its line in {CSS}")
        colors.append({"name": name, "value": value, "usage": usage})

    def family(name):
        return need(name)[0]

    groups = []
    for group, styles in TYPE_STYLES:
        fam = "ui" if group == "UI" else "body"
        groups.append({
            "name": group,
            "family": fam,
            "styles": [
                {"name": n, "fontSize": size or need(token)[0], "lineHeight": lh, "fontWeight": w}
                for n, token, size, w, lh in styles
            ],
        })

    sha = commit()
    return {
        "name": "SEAM",
        "version": 1,
        "meta": {"source": f"webapp/{CSS} via webapp/scripts/design-tokens.py" + (f" @ {sha}" if sha else "")},
        "color": {"themes": [{"id": "light", "name": "Daylight"}], "tokens": colors},
        "shadow": {"tokens": [{"name": n, "value": need(n)[0], "usage": u} for n, u in SHADOW_USAGE.items()]},
        "type": {
            "fonts": [],
            "families": {"ui": family("font-ui"), "body": family("font-body")},
            "groups": groups,
        },
        "fontSize": {"tokens": [{"name": n, "value": need(n)[0], "usage": u} for n, u in SIZE_USAGE.items()]},
        "spacing": {"tokens": [{"name": n, "value": need(n)[0], "usage": u} for n, u in SPACING_USAGE.items()]},
        "radius": {"tokens": [{"name": n, "value": need(n)[0], "usage": u} for n, u in RADIUS_USAGE.items()]},
    }


def as_css(tokens):
    lines = ["/* SEAM — generated from tokens.json */", ":root {"]
    for fam in ("color", "shadow", "fontSize", "spacing", "radius"):
        lines += [f"  --{t['name']}: {t['value']};" for t in tokens[fam]["tokens"]]
    lines += [f"  --font-{k}: {v};" for k, v in tokens["type"]["families"].items()]
    lines.append("}")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    t = build()
    sys.stdout.write(as_css(t) if sys.argv[1:] == ["css"] else json.dumps(t, indent=2, ensure_ascii=False) + "\n")
