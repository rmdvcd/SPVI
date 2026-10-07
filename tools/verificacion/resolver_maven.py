"""Resolvedor Maven mínimo (POM + Gradle module metadata, variante Android/JVM). Uso: resolver_maven.py raices.txt dir_salida
Descarga cada artefacto (jar, o classes.jar de un aar) en dir_salida. Solo para la verificación sin Gradle."""
import sys, os, json, re, urllib.request, xml.etree.ElementTree as ET, zipfile, io
REPOS=["https://dl.google.com/android/maven2","https://repo1.maven.org/maven2"]
OUT=sys.argv[2]; os.makedirs(OUT,exist_ok=True)
cache={}
def fetch(path):
    if path in cache: return cache[path]
    for r in REPOS:
        try:
            d=urllib.request.urlopen(f"{r}/{path}",timeout=30).read(); cache[path]=d; return d
        except Exception: pass
    cache[path]=None; return None
def vkey(v):
    return [int(x) if x.isdigit() else x for x in re.split(r'[.\-]',v)]
def newer(a,b):
    try: return vkey(a)>vkey(b)
    except TypeError: return a>b
best={}   # (g,a)->version
def base(g,a,v): return f"{g.replace('.','/')}/{a}/{v}/{a}-{v}"
def deps_of(g,a,v):
    """returns (list of (g,a,v)), artifact file ext or None"""
    m=fetch(base(g,a,v)+".module")
    if m:
        mod=json.loads(m)
        vs=mod.get("variants",[])
        # prefer android release / jvm api/runtime
        def score(var):
            at=var.get("attributes",{}); n=var["name"]
            s=0
            if at.get("org.gradle.usage") in ("java-api","java-runtime"): s+=1
            if at.get("org.jetbrains.kotlin.platform.type")=="androidJvm": s+=4
            elif at.get("org.jetbrains.kotlin.platform.type")=="jvm": s+=2
            if "release" in n.lower(): s+=1
            if "Runtime" in n or "runtime" in at.get("org.gradle.usage",""): s+=0.5
            if at.get("org.gradle.category")=="documentation" or "sources" in n.lower(): s-=100
            if at.get("org.gradle.category")=="platform" or at.get("org.gradle.category")=="enforced-platform": s-=100
            if "debug" in n.lower(): s-=3
            return s
        if vs:
            var=max(vs,key=score)
            if "available-at" in var:
                av=var["available-at"]; return [(av["group"],av["module"],av["version"])], None
            ds=[(d["group"],d["module"],d.get("version",{}).get("requires") or d.get("version",{}).get("strictly") or d.get("version",{}).get("prefers")) for d in var.get("dependencies",[])]
            files=[f["url"] for f in var.get("files",[])]
            ext=None
            for f in files:
                if f.endswith(".aar"): ext="aar"
                elif f.endswith(".jar"): ext="jar"
            return [d for d in ds if d[2]], ext
    p=fetch(base(g,a,v)+".pom")
    if not p: print("NOPOM",g,a,v,file=sys.stderr); return [],None
    t=ET.fromstring(re.sub(rb'xmlns="[^"]+"',b'',p,count=1))
    pk=(t.findtext("packaging") or "jar")
    pe=t.find("properties")
    props={e.tag:e.text for e in (pe if pe is not None else [])}
    props["project.version"]=v
    ds=[]
    for d in t.findall("dependencies/dependency"):
        sc=d.findtext("scope") or "compile"
        if sc not in ("compile","runtime") or d.findtext("optional")=="true": continue
        dv=d.findtext("version") or ""
        dv=re.sub(r"\$\{([^}]+)\}",lambda m: props.get(m.group(1),""),dv).strip("[]")
        if "," in dv: dv=dv.split(",")[0]
        if dv: ds.append((d.findtext("groupId"),d.findtext("artifactId"),dv))
    return ds, ("aar" if pk=="aar" else ("jar" if pk in ("jar","bundle") else None))
info={}
def walk(roots):
    q=list(roots)
    while q:
        g,a,v=q.pop()
        k=(g,a)
        if k in best and not newer(v,best[k]): continue
        best[k]=v
        ds,ext=deps_of(g,a,v); info[k]=(v,ext)
        for d in ds: q.append(d)
roots=[l.split(":") for l in open(sys.argv[1]).read().split() if l]
walk(roots)
skip={"org.jetbrains.kotlin","org.jetbrains.kotlinx"}
for (g,a),v in sorted(best.items()):
    if g in ("org.jetbrains.kotlin",) : continue
    ds,ext=deps_of(g,a,v)
    if ext is None:
        continue
    fn=f"{OUT}/{g}_{a}-{v}.jar"
    if os.path.exists(fn): continue
    d=fetch(base(g,a,v)+"."+ext)
    if d is None: print("NOFILE",g,a,v,ext,file=sys.stderr); continue
    if ext=="aar":
        z=zipfile.ZipFile(io.BytesIO(d))
        if "classes.jar" in z.namelist(): open(fn,"wb").write(z.read("classes.jar"))
        for n in z.namelist():
            if n.startswith("libs/") and n.endswith(".jar"): open(fn[:-4]+"_"+os.path.basename(n),"wb").write(z.read(n))
    else: open(fn,"wb").write(d)
print(len(best))
