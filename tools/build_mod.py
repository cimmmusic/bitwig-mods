#!/usr/bin/env python3
"""Build a BML mod on macOS (docs/sdk/building.md steps 1-8, in Python).

usage: build_mod.py <mod source folder> [--install]

Writes the installable folder to ~/CimmBitwigMods/out/<mod-id>; --install also copies it
into the BML install's mods/ folder.
"""
import json, os, re, shutil, subprocess, sys
from pathlib import Path

HOME = Path.home()
JDK = Path("/opt/homebrew/opt/openjdk@25/bin")
BML = HOME / "Downloads/BML-0.36.0-dev.16-macos"
KIT = BML / "modkit"
# The mods are written against Bitwig 6.1.4 (macOS); Patchbay translates them to the installed version at load.
# The mod kit's final check needs the jars of the version the code is written for: a copy of 6.1.4's, taken from its
# installer (build/bitwig-6.1.4), else the installed Bitwig's.
BW = Path(__file__).resolve().parent.parent / "build" / "bitwig-6.1.4"
if not (BW / "bitwig.jar").exists():
    BW = Path("/Applications/Bitwig Studio.app/Contents/Java")
BITWIG_JAR, LIBS_JAR = BW / "bitwig.jar", BW / "libs.jar"
WORKROOT = HOME / "CimmBitwigMods/build"
COMPILE_JAR = WORKROOT / "bitwig614-compile.jar"


def run(*args):
    print("+", " ".join(str(a) for a in args))
    r = subprocess.run([str(a) for a in args], capture_output=True, text=True)
    sys.stdout.write(r.stdout); sys.stderr.write(r.stderr)
    if r.returncode:
        sys.exit(f"step failed ({r.returncode})")
    return r.stdout


def target_id(t, targets):
    for k, v in targets.items():
        if v == t:
            return k
    if t["kind"] == "class":
        base = t["class"]
    else:
        base = f'{t["owner"]}.{t["name"]}'
    base = re.sub(r"[^A-Za-z0-9_.-]", "", base.replace("/", ".").replace("$", "_"))
    tid, n = base, 2
    while tid in targets:
        tid, n = f"{base}-{n}", n + 1
    targets[tid] = t
    return tid


def finish_manifest(src_manifest, hooks):
    m = json.loads(json.dumps(src_manifest))
    targets = m.setdefault("targets", {})
    m.setdefault("hooks", [])
    for h in hooks.get("hooks", []):
        e = {"id": h["id"], "target": target_id(h["target"], targets), "transform": h["transform"]}
        if "method_from" in h:
            e["method"] = {"from_target": target_id(h["method_from"], targets)}
            e["body"] = {"template": h["body"]}
        if "match" in h:
            mt = h["match"]
            if "target" in mt:
                e["match"] = {"opcode": mt["opcode"], "target": target_id(mt["target"], targets),
                              "occurrence": mt["occurrence"]}
            else:
                e["match"] = {k: mt[k] for k in ("opcode", "owner", "name", "descriptor", "occurrence") if k in mt}
        e["bridge"] = h["bridge"]
        m["hooks"].append(e)
    if hooks.get("hook_classes"):
        m["helper"] = {"jar": "helper.jar", "class": hooks["hook_classes"][0]}
    fields = []
    for f in hooks.get("fields", []):
        owner = target_id({"kind": "class", "class": f["owner"]} if isinstance(f["owner"], str) else f["owner"], targets)
        for cls in re.findall(r"L([^;]+);", f["descriptor"]):
            if not cls.startswith("java/"):
                target_id({"kind": "class", "class": cls}, targets)
        fields.append({"id": f["id"], "owner": owner, "name": f["name"], "descriptor": f["descriptor"]})
    if fields:
        m["fields"] = fields
    opens = sorted({target_id(a, targets) for a in hooks.get("access", [])})
    if opens:
        m["opens"] = opens
    order = ["manifest_version", "mod", "built_against", "helper", "targets", "hooks", "fields", "opens"]
    return {k: m[k] for k in order if k in m} | {k: v for k, v in m.items() if k not in order}


def main():
    mod = Path(sys.argv[1]).resolve()
    src_manifest = json.loads((mod / "mod.json").read_text())
    build = json.loads((mod / "build.json").read_text())
    mod_id = src_manifest["mod"]["id"]
    work, out = WORKROOT / "work" / mod_id, HOME / "CimmBitwigMods/out" / mod_id
    shutil.rmtree(work, ignore_errors=True); shutil.rmtree(out, ignore_errors=True)
    (work / "classes").mkdir(parents=True); out.mkdir(parents=True)
    if not COMPILE_JAR.exists():
        run(JDK / "java", "-jar", KIT / "bml-modkit.jar", "compile-jar", BITWIG_JAR, COMPILE_JAR)
    deps = [str(BML / "mods" / d.split("@")[0] / "helper.jar") for d in build.get("classpath_mods", [])]
    cp = ":".join([str(COMPILE_JAR), str(LIBS_JAR), str(KIT / "bml-api.jar")] + deps)
    sources = [str(p) for p in (mod / "src").rglob("*.java")]
    run(JDK / "javac", "--release", str(build.get("release", 21)), "-proc:none", "-encoding", "UTF-8",
        "-implicit:none", "-sourcepath", mod / "src", "-cp", cp, "-d", work / "classes", *sources)
    run(JDK / "java", "-jar", KIT / "bml-modkit.jar", "finish", work / "classes", work / "hooks.json",
        BITWIG_JAR, LIBS_JAR, *deps)
    for p in (mod / "src").rglob("*"):
        if p.is_file() and p.suffix != ".java":
            d = work / "classes" / p.relative_to(mod / "src"); d.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, d)
    run(JDK / "jar", "--create", "--file", out / "helper.jar", "-C", work / "classes", ".")
    hooks = json.loads((work / "hooks.json").read_text())
    (out / "mod.json").write_text(json.dumps(finish_manifest(src_manifest, hooks), indent=2) + "\n")
    shutil.copy2(mod / "README.md", out)
    for extra in ("images", "package"):
        if (mod / extra).exists():
            shutil.copytree(mod / extra, out if extra == "package" else out / extra, dirs_exist_ok=True)
    shutil.copytree(mod / "src", out / "src"); shutil.copy2(mod / "build.json", out)
    print("BUILT", out)
    if "--install" in sys.argv:
        dest = BML / "mods" / mod_id
        shutil.rmtree(dest, ignore_errors=True); shutil.copytree(out, dest)
        print("INSTALLED", dest)


if __name__ == "__main__":
    main()
