#!/usr/bin/env python3
"""Refresh the structure-density snapshot the cost model prices structure loot with.

Two facts, both *derived* from a Minecraft server jar rather than curated:

  1. **Density** -- each `worldgen/structure_set` carries its placement, and a
     `random_spread` structure occurs once per `spacing x spacing` chunk region, times
     `frequency` where present. So chunks searched per occurrence = spacing^2 / frequency.
  2. **Membership** -- each `structure/*.nbt` template names the blocks it places, and the
     template's top directory is the structure family. That is what separates an ender
     chest (End city only) from a campfire (villages) from a soul campfire (nowhere).

The curated half stays in the engine: one access multiplier per structure *class*
(overworld surface, overworld deep, ocean, nether, end). Density is how rare a structure
is; access is how hard it is to reach, and only the first is in Mojang's data.

## Why a committed snapshot rather than an ingestion step

Placements have not moved across 1.20 -> 26.2 (20 sets, 0 changed placements, identical
membership per set), so there is nothing for a per-version extraction step to track. And
the data does not exist before 1.20 -- 1.18 and 1.19 ship zero structure sets, because
worldgen was not JSON in the jar yet -- so a step would yield nothing for two of the
versions Seam ingests and would need this fallback regardless.

Mirrors `dump-item-ids.sh`: a committed snapshot, a script to refresh it, and a test that
fails when the newest version disagrees with what is committed.

Usage:
    scripts/dump-structure-density.py            # newest release from Mojang
    scripts/dump-structure-density.py 1.21.4     # a specific version
"""
import gzip
import io
import json
import os
import re
import sys
import urllib.request
import zipfile
from collections import defaultdict

MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

def _resource(name):
    return os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "mc-engine", "src", "main", "resources", "minecraft", name,
    )


DEST = _resource("structure-density.txt")

# Second snapshot, same jar, same run (MCO-527). The script's name is narrower than what it
# now does; it keeps it because one download and one drift check serve both, and splitting
# would mean fetching 60 MB twice to answer two questions about the same file.
NATURAL_DEST = _resource("natural-blocks.txt")

# A template directory is not always spelled like the structure_set that places it. Three
# of these are a judgement rather than a plural: a bastion and a fortress share the
# `nether_complexes` set, `underwater_ruin` templates are placed by `ocean_ruins`, and
# overworld `fossil` templates belong to no structure set at all (fossils are a worldgen
# feature, not a structure) so they are dropped rather than guessed at.
FAMILY_TO_SET = {
    "ancient_city": "ancient_cities",
    "bastion": "nether_complexes",
    "end_city": "end_cities",
    "igloo": "igloos",
    "nether_fossils": "nether_fossils",
    "pillager_outpost": "pillager_outposts",
    "ruined_portal": "ruined_portals",
    "shipwreck": "shipwrecks",
    "trail_ruins": "trail_ruins",
    "trial_chambers": "trial_chambers",
    "underwater_ruin": "ocean_ruins",
    "village": "villages",
    "woodland_mansion": "woodland_mansions",
}


def newest_release():
    manifest = json.load(urllib.request.urlopen(MANIFEST))
    return manifest["latest"]["release"], manifest


def server_jar(version, manifest):
    url = next(v["url"] for v in manifest["versions"] if v["id"] == version)
    meta = json.load(urllib.request.urlopen(url))
    sys.stderr.write(f"downloading server jar for {version} ...\n")
    return urllib.request.urlopen(meta["downloads"]["server"]["url"]).read()


def readers(jar_bytes):
    """The jar, plus the nested META-INF/versions jar where the data actually lives."""
    outer = zipfile.ZipFile(io.BytesIO(jar_bytes))
    nested = [n for n in outer.namelist()
              if re.match(r"META-INF/versions/.*/server-.*\.jar$", n)]
    out = []
    if nested:
        out.append(zipfile.ZipFile(io.BytesIO(outer.read(nested[0]))))
    out.append(outer)
    return out


def read_jar(jar_bytes):
    """-> ({set_name: placement json}, {template_path: raw nbt})

    `structures/` was singularised to `structure/` at 1.21, the same rename that already
    forced fallbacks in ExtractRelevantMinecraftFilesStep, so both spellings are accepted.
    """
    sets, templates, worldgen = {}, {}, {}
    for z in readers(jar_bytes):
        for name in z.namelist():
            m = re.match(r"data/minecraft/worldgen/structure_set/(.+)\.json$", name)
            if m and m.group(1) not in sets:
                sets[m.group(1)] = json.loads(z.read(name))
            m = re.match(r"data/minecraft/structures?/(.+)\.nbt$", name)
            if m and m.group(1) not in templates:
                templates[m.group(1)] = z.read(name)
            m = re.match(
                r"data/minecraft/worldgen/(noise_settings|configured_feature|placed_feature|biome)/(.+)\.json$",
                name,
            )
            if m and name not in worldgen:
                worldgen[name] = json.loads(z.read(name))
    return sets, templates, worldgen


def chunks_per_occurrence(placement):
    """spacing^2 / frequency, or None for a placement that is not a random spread.

    `strongholds` is `concentric_rings` -- 128 of them on rings around the origin, with no
    spacing at all -- so it gets no derived density and the engine falls back to its
    curated access number alone.
    """
    if placement.get("type") != "minecraft:random_spread":
        return None
    spacing = placement.get("spacing")
    if not spacing:
        return None
    frequency = placement.get("frequency") or 1.0
    return spacing * spacing / frequency


def _nbt_reader(raw):
    """A minimal big-endian NBT reader, enough to walk a structure template.

    Deliberately in this script rather than reusing `mc-nbt`: the snapshot is generated by
    one standalone Python file with one drift check, and reaching into a Kotlin module to
    refresh a committed text file would make regenerating it a build step.
    """
    import struct

    class R:
        def __init__(self, buf):
            self.b, self.i = buf, 0

        def u1(self):
            v = self.b[self.i]; self.i += 1; return v

        def n(self, fmt, size):
            v = struct.unpack_from(fmt, self.b, self.i)[0]; self.i += size; return v

        def string(self):
            ln = self.n(">H", 2)
            v = self.b[self.i:self.i + ln].decode("utf-8", "replace"); self.i += ln; return v

        def payload(self, t):
            if t == 1: return self.n(">b", 1)
            if t == 2: return self.n(">h", 2)
            if t == 3: return self.n(">i", 4)
            if t == 4: return self.n(">q", 8)
            if t == 5: return self.n(">f", 4)
            if t == 6: return self.n(">d", 8)
            if t == 7:
                ln = self.n(">i", 4); self.i += ln; return None
            if t == 8: return self.string()
            if t == 9:
                et, ln = self.u1(), self.n(">i", 4)
                return [self.payload(et) for _ in range(ln)]
            if t == 10:
                out = {}
                while True:
                    tt = self.u1()
                    if tt == 0: return out
                    # Name first, THEN payload, and the two lines are not cosmetic:
                    # `out[self.string()] = self.payload(tt)` reads the payload before the
                    # name, because Python evaluates an assignment's right-hand side first.
                    # That misaligns the stream from the first tag onward and surfaces much
                    # later as "unknown NBT tag 0" somewhere innocent.
                    name = self.string()
                    out[name] = self.payload(tt)
            if t == 11:
                ln = self.n(">i", 4); self.i += 4 * ln; return None
            if t == 12:
                ln = self.n(">i", 4); self.i += 8 * ln; return None
            raise ValueError(f"unknown NBT tag {t}")

    r = R(raw)
    if r.u1() != 10:
        raise ValueError("structure template is not a root compound")
    r.string()
    return r.payload(10)


def template_block_counts(raw):
    """block id -> how many times this one template places it.

    The palette names the blocks; each entry in `blocks` carries a `state` index into it.
    Membership only needed the palette, which is why the original scan was a regex — but a
    count needs the placements, and those are only readable with a real parse (MCO-513).

    Templates may carry `palettes` (several alternative palettes) instead of `palette`; the
    first is taken, since they are cosmetic variants of the same building with the same
    block counts.
    """
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    root = _nbt_reader(raw)

    palette = root.get("palette")
    if palette is None:
        palettes = root.get("palettes") or []
        palette = palettes[0] if palettes else None
    if not palette:
        return {}

    names = [(entry or {}).get("Name") for entry in palette]
    counts = defaultdict(int)
    for entry in root.get("blocks") or []:
        idx = (entry or {}).get("state")
        if idx is None or not (0 <= idx < len(names)):
            continue
        name = names[idx]
        # Air is a placement like any other in the format and a block in no useful sense.
        if name and "/" not in name and not name.endswith("air"):
            counts[name] += 1
    return counts


def palette_blocks(raw):
    """The block ids a structure template places.

    A template stores each palette entry's id as an NBT string; scanning the (optionally
    gzipped) bytes for `minecraft:...` runs enumerates them without a full NBT parse,
    which is all the membership question needs. Ids containing a slash are loot-table and
    similar references, not blocks.
    """
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return {m.decode("ascii") for m in re.findall(rb"minecraft:[a-z0-9_/.]+", raw)
            if b"/" not in m}


def block_state_names(node, out):
    """Every block id sitting in a block-state `Name` field, anywhere in the tree.

    Deliberately keyed on `Name` rather than scanning for `minecraft:` strings: worldgen JSON
    is full of ids that are not blocks — rule types (`minecraft:bandlands`), noise ids, biome
    ids, feature references. A regex over the raw text finds 129 "blocks" in noise_settings
    where there are 37.
    """
    if isinstance(node, dict):
        name = node.get("Name")
        if isinstance(name, str) and name.startswith("minecraft:"):
            out.add(name)
        for value in node.values():
            block_state_names(value, out)
    elif isinstance(node, list):
        for value in node:
            block_state_names(value, out)


def natural_blocks(worldgen):
    """-> ({terrain block ids}, {feature block ids})

    Two different claims, kept apart because they answer different questions:

      * **terrain** -- what the ground is *made of*, from the surface rules in
        `noise_settings`. This is where a badlands' terracotta lives, and it is the half
        MCO-527 needs: breaking it is mining, not re-collecting something placed.
      * **features** -- what worldgen scatters into it: ores, disks, vegetation, trees.

    ## Only features a biome actually asks for

    `configured_feature/` holds more than the wild world. It also holds the decoration that
    *structures* place -- `pile_hay` and `pile_pumpkin` are village dressing, not something
    growing in a field. Taking the directory wholesale said hay bales lie around for free,
    which made wheat cost 0.01 min by breaking one and unpacking it: MCO-317's "route a
    common item through an unpack chain" in a new place.

    The discriminator is derived rather than a list of exceptions. **A feature is wild when
    some biome lists it.** Every `biome/*.json` carries a `features` array of placed-feature
    ids; a placed feature names the configured feature it places. Anything not reachable that
    way is only ever put down by a structure, and belongs to structure membership instead --
    where it is priced as "go and find one" rather than "it is lying about".

    On 26.2 that is 208 of 262 placed features, reaching 159 of 227 configured features.

    ## Known gap

    One rule rather than a general failure: `minecraft:bandlands` is a surface-rule type whose
    terracotta palette lives in code, not in the JSON. So plain, white and orange terracotta
    appear here and the four banded colours (red, yellow, brown, light gray) do not. It does
    not block MCO-527's case -- dyed terracotta is crafted from plain terracotta, so pricing
    the plain block prices the rest through the chain.
    """
    biomes = {k: v for k, v in worldgen.items() if "/biome/" in k}
    placed = {k.split("/")[-1][:-5]: v for k, v in worldgen.items() if "/placed_feature/" in k}

    wild_placed = set()
    for biome in biomes.values():
        for step in biome.get("features", []) or []:
            for feature in step:
                if isinstance(feature, str):
                    wild_placed.add(feature.split(":")[-1])

    wild_configured = set()
    for name in wild_placed:
        feature = (placed.get(name) or {}).get("feature")
        if isinstance(feature, str):
            wild_configured.add(feature.split(":")[-1])

    terrain, features = set(), set()
    for path, doc in worldgen.items():
        if "/noise_settings/" in path:
            block_state_names(doc, terrain)
        elif "/configured_feature/" in path:
            if path.split("/")[-1][:-5] in wild_configured:
                block_state_names(doc, features)

    # Air is a block state in the format and a block in no useful sense.
    return (
        {b for b in terrain if not b.endswith("air")},
        {b for b in features if not b.endswith("air")},
    )


def build(version, manifest):
    sets, templates, worldgen = read_jar(server_jar(version, manifest))
    if not sets:
        raise SystemExit(f"{version} ships no worldgen/structure_set data (pre-1.20?)")

    placement = {}
    for name in sorted(sets):
        p = sets[name].get("placement", {})
        placement[name] = (chunks_per_occurrence(p), p.get("type", "").split(":")[-1])

    membership = defaultdict(set)
    # (block, set) -> the most of that block any ONE template of the set places.
    #
    # **Max per template, not a sum and not a mean** (MCO-513). A structure is assembled
    # from a subset of its templates, so summing every template in `village/` counts five
    # biome variants of every house and says a village holds a hundred campfires. A mean
    # is worse in the other direction: it divides the library's bookshelves across the
    # blacksmith and the shepherd's hut, neither of which has any.
    #
    # Max answers the question a player actually asks — "if I go there and find the
    # building with the most of these, how many do I get" — because that is what they do:
    # you raid the library for bookshelves, not an average house. It is also the
    # conservative direction, since a smaller count makes the block dearer, matching the
    # bias already stated for membership at the top of the file.
    counts = defaultdict(int)
    for path, raw in templates.items():
        structure_set = FAMILY_TO_SET.get(path.split("/")[0])
        if structure_set is None:
            continue
        for block in palette_blocks(raw):
            membership[block].add(structure_set)
        try:
            per_template = template_block_counts(raw)
        except Exception as e:  # a template we cannot parse must not lose its membership
            print(f"  warning: could not count {path}: {e}", file=sys.stderr)
            continue
        for block, n in per_template.items():
            key = (block, structure_set)
            if n > counts[key]:
                counts[key] = n

    return version, placement, membership, counts, len(templates), natural_blocks(worldgen)


def write(version, placement, membership, counts, template_count):
    os.makedirs(os.path.dirname(DEST), exist_ok=True)
    with open(DEST, "w") as f:
        f.write(
            "# Structure density and block membership, derived from the Minecraft server jar.\n"
            "# Regenerate with webapp/scripts/dump-structure-density.py -- do not hand-edit.\n"
            "#\n"
            "# Placements have not moved across 1.20 -> 26.2 (20 sets, 0 changed), and 1.18/1.19\n"
            "# ship no structure data at all, so ONE snapshot serves every version Seam ingests.\n"
            "# Pre-1.20 versions using these numbers is an assumption, not a measurement: their\n"
            "# placements were compiled in rather than shipped as data and cannot be read back.\n"
            "#\n"
            "# Membership is NOT version-stable the way placement is -- it grows as structures are\n"
            "# added (trial chambers at 1.21 touched 49 blocks). The newest version is snapshotted\n"
            "# deliberately: a block listed under a structure that an older version lacks is priced\n"
            "# as findable when it is not, which makes it dearer and so is the conservative error.\n"
            f"#\n"
            f"# Seven structure sets are generated in code rather than from templates and so\n"
            f"# contribute no membership: buried_treasures, desert_pyramids, jungle_temples,\n"
            f"# mineshafts, ocean_monuments, strongholds, swamp_huts. A block found only in those\n"
            f"# keeps the unstructured default, which is the behaviour it had before this file.\n"
            f"version={version}\n"
            f"templates={template_count}\n"
            "\n"
            "[placement]\n"
            "# structure_set|chunks_searched_per_occurrence|placement_type\n"
        )
        for name, (chunks, kind) in sorted(placement.items()):
            f.write(f"{name}|{'' if chunks is None else f'{chunks:g}'}|{kind}\n")

        f.write("\n[membership]\n# block_id|structure_sets\n")
        for block in sorted(membership):
            f.write(f"{block}|{','.join(sorted(membership[block]))}\n")

        f.write(
            "\n[counts]\n"
            "# block_id|structure_set:blocks_per_visit,...\n"
            "# The MOST of this block any single template of that set places -- what one visit\n"
            "# to the best building for it yields. Replaces a flat BLOCKS_PER_VISIT=8 for every\n"
            "# block in every structure (MCO-513). A block absent here keeps that default: the\n"
            "# seven code-generated sets listed above place no templates, so a stronghold\n"
            "# library's bookshelves are NOT counted and never will be from this source.\n"
        )
        by_block = defaultdict(dict)
        for (block, structure_set), n in counts.items():
            by_block[block][structure_set] = n
        for block in sorted(by_block):
            pairs = ",".join(f"{s}:{n}" for s, n in sorted(by_block[block].items()))
            f.write(f"{block}|{pairs}\n")

    return DEST


def write_natural(version, terrain, features):
    """Write the natural-blocks snapshot (MCO-527)."""
    os.makedirs(os.path.dirname(NATURAL_DEST), exist_ok=True)
    with open(NATURAL_DEST, "w") as f:
        f.write(
            "# Blocks that world generation places, derived from the Minecraft server jar.\n"
            "# Regenerate with webapp/scripts/dump-structure-density.py -- do not hand-edit.\n"
            "#\n"
            "# Answers one question: is breaking this block *mining*, or is it re-collecting\n"
            "# something a player put down? A beacon is only ever placed. Terracotta is what a\n"
            "# badlands is made of. Before this file the model could not tell them apart and\n"
            "# treated any craftable block as re-collection, which removed mining terracotta as\n"
            "# an option entirely (MCO-527).\n"
            "#\n"
            "# [terrain]  the surface rules -- what the ground is made of.\n"
            "# [feature]  what worldgen puts into it: ores, disks, vegetation, trees.\n"
            "# Both count as natural; they are kept apart because they are different claims and\n"
            "# a later reader may want only one.\n"
            "#\n"
            "# KNOWN GAP, and it is one rule rather than a general failure: `minecraft:bandlands`\n"
            "# is a surface-rule type whose terracotta palette lives in code, not in the JSON. So\n"
            "# plain, white and orange terracotta are listed and the four banded colours (red,\n"
            "# yellow, brown, light gray) are not. Dyed terracotta is crafted from plain, so the\n"
            "# chain prices them correctly anyway -- but a reader looking for red_terracotta here\n"
            "# and not finding it is seeing a limit of the data, not a bug in the extraction.\n"
            "#\n"
            "# Structure-placed blocks are NOT here: they are membership in structure-density.txt,\n"
            "# where they carry a rarity as well. A block in a structure and nowhere else is\n"
            "# findable but not natural, and the two are priced differently.\n"
            f"version={version}\n"
        )
        f.write("\n[terrain]\n")
        for block in sorted(terrain):
            f.write(f"{block}\n")
        f.write("\n[feature]\n")
        for block in sorted(features - terrain):
            f.write(f"{block}\n")
    return NATURAL_DEST


def committed_placements():
    """The [placement] section of the snapshot on disk, as {set: (chunks, kind)}."""
    out, section = {}, ""
    with open(DEST) as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("["):
                section = line.strip("[]")
                continue
            if section == "placement":
                set_name, chunks, kind = line.split("|")
                out[set_name] = (float(chunks) if chunks else None, kind)
    return out


def check(version, manifest):
    """Exit non-zero when a jar disagrees with what is committed.

    Run this when a new Minecraft version is ingested. It is the half of the scheme a unit
    test cannot do cheaply, and it is what catches a structure set being *added* -- which is
    the real maintenance risk, since no placement has changed since 1.20 but trial chambers
    did appear at 1.21.
    """
    _, fresh, _, _, _, _ = build(version, manifest)
    old = committed_placements()

    added = sorted(set(fresh) - set(old))
    removed = sorted(set(old) - set(fresh))
    changed = sorted(s for s in set(fresh) & set(old) if fresh[s] != old[s])

    if not (added or removed or changed):
        print(f"{version} agrees with the committed snapshot ({len(old)} structure sets)")
        return 0

    for s in added:
        print(f"  ADDED    {s}: {fresh[s]}")
    for s in removed:
        print(f"  REMOVED  {s}: was {old[s]}")
    for s in changed:
        print(f"  CHANGED  {s}: {old[s]} -> {fresh[s]}")
    print(f"\n{version} disagrees with the snapshot. Re-run without --check to refresh it.")
    return 1


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--check"]
    checking = "--check" in sys.argv

    requested = args[0] if args else None
    latest, manifest = newest_release()
    version = requested or latest
    if requested and requested != latest:
        sys.stderr.write(f"note: {latest} is the newest release; using {requested}\n")

    if checking:
        raise SystemExit(check(version, manifest))

    version, placement, membership, counts, templates, natural = build(version, manifest)
    path = write(version, placement, membership, counts, templates)
    natural_path = write_natural(version, *natural)
    derived = sum(1 for c, _ in placement.values() if c is not None)
    counted = len({block for block, _ in counts})
    print(f"version {version}: {len(placement)} structure sets ({derived} with a derived density), "
          f"{len(membership)} blocks from {templates} templates, {counted} with a counted "
          f"blocks-per-visit")
    terrain, features = natural
    print(f"natural blocks: {len(terrain)} terrain, {len(features)} from features, "
          f"{len(terrain | features)} distinct")
    print(f"wrote {os.path.relpath(path)}")
    print(f"wrote {os.path.relpath(natural_path)}")
