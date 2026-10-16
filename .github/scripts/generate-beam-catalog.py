"""Generate an ID-only structural-block catalog; never copy models or textures into SR.
Usage: python .github/scripts/generate-beam-catalog.py --conquest JAR --architects JAR
"""
import argparse
import hashlib
import json
import re
import zipfile
from functools import lru_cache
from pathlib import Path

FAMILY = re.compile(r"(?:beam|lintel|pole|branch|shelf|shelves|flange|door|corbel|railing|fence|gate|pipe|window|balustrade|handrail|lattice|trellis|brace|joist|truss|strut|platform|(?:^|_)wall$|(?:^|_)(?:diagonal|steeper_diagonal|half_diagonal|thin)_(?:.*board|stripped_.*))")
EXCLUDED = re.compile(r"(?:stair|slab|quarter|eighth|layer|small_?block|full_?block|(?:^|_)(?:cube|carpet|cover|bit)(?:_|$))")

def full_box(element):
    a,b=element.get('from',[]),element.get('to',[])
    return len(a)==len(b)==3 and all(lo<=0 and hi>=16 for lo,hi in zip(a,b))

def generate(jars):
    models={};states={}
    for namespace,path in jars.items():
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith('assets/') and '/models/' in name and name.endswith('.json'):
                    _,ns,_,model=name.split('/',3);models[ns+':'+model[:-5]]=archive.read(name)
                if name.startswith('assets/'+namespace+'/blockstates/') and name.endswith('.json'):
                    states[namespace+':'+Path(name).stem]=archive.read(name)
    @lru_cache(None)
    def rejected_model(name,depth=0):
        if depth>32:return True
        namespace,path=(name.split(':',1) if ':' in name else ('minecraft',name))
        if namespace=='minecraft' and path.rsplit('/',1)[-1].startswith('cube'):return True
        if EXCLUDED.search(path.rsplit('/',1)[-1]):return True
        raw=models.get(namespace+':'+path)
        if raw is None:return False
        data=json.loads(raw)
        if any(full_box(element) for element in data.get('elements',[])):return True
        return bool(data.get('parent')) and rejected_model(data['parent'],depth+1)
    accepted=[];rejected=[]
    for id,raw in sorted(states.items()):
        path=id.split(':')[1]
        if not FAMILY.search(path):continue
        refs=re.findall(r'"model"\s*:\s*"([^"]+)"',raw.decode())
        if EXCLUDED.search(path) or any(rejected_model(model) for model in refs):rejected.append(id)
        else:accepted.append(id)
    return accepted,rejected

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--conquest',required=True,type=Path);parser.add_argument('--architects',required=True,type=Path)
    options=parser.parse_args();jars={'conquest':options.conquest,'architects':options.architects};ids,rejected=generate(jars)
    root=Path(__file__).resolve().parents[2]
    # Chunk the literal to stay below the JVM's per-constant UTF-8 limit.
    chunks=[' '.join(ids[i:i+100]) for i in range(0,len(ids),100)]
    literals=',\n'.join('            "'+chunk+'"' for chunk in chunks)
    source='''package de.selectiverender;

/** Generated structural IDs from Conquest 1.7.0 / Architects 1.0.3; excludes volume-building shapes.
 * Regenerate with .github/scripts/generate-beam-catalog.py; only identifiers are shipped. */
final class BeamBlockCatalog {
    private static final java.util.Set<String> IDS = load();
    private BeamBlockCatalog() { }
    static boolean contains(String id) { return IDS.contains(id); }
    static int size() { return IDS.size(); }
    private static java.util.Set<String> load() {
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(String group:new String[]{
LITERALS
        }) java.util.Collections.addAll(ids,group.split(" "));
        return java.util.Set.copyOf(ids);
    }
}
'''.replace('LITERALS',literals)
    (root/'shared/common/src/client/java/de/selectiverender/BeamBlockCatalog.java').write_text(source,encoding='utf-8')
    report={'included':ids,'excluded':rejected,'inputs':{ns:{'filename':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for ns,p in jars.items()}}
    (root/'build').mkdir(exist_ok=True)
    (root/'build/beam-catalog-audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(f'{len(ids)} structural IDs included; {len(rejected)} candidate IDs rejected')

if __name__=='__main__':main()
