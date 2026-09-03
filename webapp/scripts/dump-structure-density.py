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

DEST = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "mc-engine", "src", "main", "resources", "minecraft", "structure-density.txt",
)

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
    sets, templates = {}, {}
    for z in readers(jar_bytes):
        for name in z.namelist():
            m = re.match(r"data/minecraft/worldgen/structure_set/(.+)\.json$", name)
            if m and m.group(1) not in sets:
                sets[m.group(1)] = json.loads(z.read(name))
            m = re.match(r"data/minecraft/structures?/(.+)\.nbt$", name)
            if m and m.group(1) not in templates:
                templates[m.group(1)] = z.read(name)
    return sets, templates


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


def build(version, manifest):
    sets, templates = read_jar(server_jar(version, manifest))
    if not sets:
        raise SystemExit(f"{version} ships no worldgen/structure_set data (pre-1.20?)")

    placement = {}
    for name in sorted(sets):
        p = sets[name].get("placement", {})
        placement[name] = (chunks_per_occurrence(p), p.get("type", "").split(":")[-1])

    membership = defaultdict(set)
    for path, raw in templates.items():
        structure_set = FAMILY_TO_SET.get(path.split("/")[0])
        if structure_set is None:
            continue
        for block in palette_blocks(raw):
            membership[block].add(structure_set)

    return version, placement, membership, len(templates)


def write(version, placement, membership, template_count):
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

    return DEST


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
    _, fresh, _, _ = build(version, manifest)
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

    version, placement, membership, templates = build(version, manifest)
    path = write(version, placement, membership, templates)
    derived = sum(1 for c, _ in placement.values() if c is not None)
    print(f"version {version}: {len(placement)} structure sets ({derived} with a derived density), "
          f"{len(membership)} blocks from {templates} templates")
    print(f"wrote {os.path.relpath(path)}")
