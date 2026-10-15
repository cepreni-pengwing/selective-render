"""Build an offline screenshot gallery and test actual framebuffer colors.
Generated reports/screenshots stay in expiring Actions artifacts, never in Git.
"""
import argparse
import colorsys
import html
import json
import re
from pathlib import Path
from PIL import Image, ImageStat


def mean(image, area=(.45,.45,.55,.55)):
    w,h=image.size
    return ImageStat.Stat(image.convert("RGB").crop(tuple(round(v*n) for v,n in zip(area,(w,h,w,h))))).mean


def foliage_pixels(image):
    w,h=image.size
    crop=image.convert("RGB").crop((round(w*.2),round(h*.1),round(w*.8),round(h*.45)))
    # Native foliage can be green, dry brown or cold-biome blue/green.
    # It remains dark and chromatic; neutral stone, bright sky/clouds and opaque red fail.
    return sum(35<g<180 and r<180 and b<180 and max(r,g,b)-min(r,g,b)>10 for r,g,b in crop.getdata())


def check_world(images):
    checks=[]
    def check(name, passed, detail):
        checks.append({"name":name,"passed":bool(passed),"detail":detail})
    rgb={key:mean(value) for key,value in images.items() if key[:2].isdigit() and int(key[:2])<=12}
    normal=rgb["01-normal"]
    check("Original cut face is neutral stone",max(normal)-min(normal)<25,str([round(v,1) for v in normal]))
    check("0% preserves the original face",max(abs(a-b) for a,b in zip(normal,rgb["02-color-0"]))<18,"Normal versus Color 0%")
    levels=[rgb[key] for key in ["02-color-0","03-color-25","04-color-50","05-color-75","06-color-100"]]
    check("Opacity changes the rendered image at every quarter",all(b[0]>a[0]+8 and b[1]<a[1]-4 for a,b in zip(levels,levels[1:])),str([[round(v,1) for v in value] for value in levels]))
    red=rgb["06-color-100"]
    check("100% Color is opaque red",red[0]>180 and red[1]<35 and red[2]<35,str([round(v,1) for v in red]))
    grass_side=mean(images["06-color-100"],(.38,.38,.58,.40))
    check("Opaque Color covers coplanar grass side overlays",grass_side[0]>180 and grass_side[1]<35 and grass_side[2]<35,str([round(v,1) for v in grass_side]))
    foliage=foliage_pixels(images["06-color-100"])
    only_foliage=foliage_pixels(images["11-only-stone"])
    check("Grass decoration keeps its native material",foliage>150,f"Native foliage pixels: {foliage}")
    check("Only stone removes grass decoration",only_foliage<10,f"Remaining foliage pixels: {only_foliage}")
    texture=rgb["07-texture"]; combined=rgb["08-texture-color"]
    check("Texture selects a cyan diamond-block face",texture[1]>texture[0]+15 and texture[2]>texture[0]+15,str([round(v,1) for v in texture]))
    check("Texture + Color blends both layers",texture[0]+8<combined[0]<red[0]-8 and red[1]+4<combined[1]<texture[1]-4,str([round(v,1) for v in combined]))
    rainbow=images["09-rainbow"].convert("RGB"); w,h=rainbow.size; hues=set()
    for x in range(round(w*.30),round(w*.70),max(1,w//80)):
        for y in range(round(h*.45),round(h*.55),max(1,h//40)):
            pixel=rainbow.getpixel((x,y))
            if max(pixel)-min(pixel)>50:
                hues.add(int(colorsys.rgb_to_hsv(*(v/255 for v in pixel))[0]*12))
    check("Rainbow renders several hues",len(hues)>=3,f"Hue bins: {sorted(hues)}")
    check("Culled removes the central cut face",sum(abs(a-b) for a,b in zip(normal,rgb["10-culled"]))>45,"Original versus Culled")
    check("Only stone preserves the remaining stone face",max(abs(a-b) for a,b in zip(normal,rgb["11-only-stone"]))<22,"Original versus Only stone")
    split=images["12-region-split"]; left=mean(split,(.35,.45,.43,.55)); right=mean(split,(.57,.45,.65,.55))
    check("Regional blue and global red stay independent",left[2]>left[0]+70 and right[0]>right[2]+70,str({"left":[round(v,1) for v in left],"right":[round(v,1) for v in right]}))
    return checks


def grid_text_visible(image):
    w,h=image.size
    crop=image.convert("RGB").crop((round(w*.47),round(h*.247),round(w*.60),round(h*.267)))
    return sum(min(r,g,b)>210 for r,g,b in crop.getdata())>10


def main():
    args=argparse.ArgumentParser()
    args.add_argument("directory",type=Path)
    args.add_argument("--version",required=True)
    args.add_argument("--profile",required=True)
    args.add_argument("--require-visual",action="store_true")
    args.add_argument("--ui-only",action="store_true")
    options=args.parse_args()
    plan=Path(__file__).resolve().parents[2]/"src/client/java/de/selectiverender/VisualScenarioPlan.java"
    scenarios=dict(re.findall(r'new Case\("([^"]+)",\s*"([^"]+)"',plan.read_text()))
    if options.ui_only:
        scenarios={key:value for key,value in scenarios.items() if "-ui-" in key}
    folder=options.directory/"screenshots"
    files=sorted(folder.glob("sr-*.png"))
    images={path.stem.removeprefix("sr-visual-"):Image.open(path) for path in files if path.name.startswith("sr-visual-")}
    checks=[]
    if options.require_visual:
        missing=sorted(set(scenarios)-set(images))
        checks.append({"name":"Complete screenshot series","passed":not missing,"detail":f"{len(images)}/{len(scenarios)}; missing: {missing}"})
        if not missing and not options.ui_only:
            checks.extend(check_world(images))
        if "23-ui-grid-complete" in images:
            checks.append({"name":"Saved grid values remain visible", "passed":grid_text_visible(images["23-ui-grid-complete"]), "detail":"Visible text inside the first saved corner field"})
        log=options.directory/"logs/latest.log"
        checks.append({"name":"In-game widget and policy assertions completed","passed":log.exists() and "[SR visual smoke] PASS:" in log.read_text(errors="replace"),"detail":"Visible widget bounds/overlaps, palette visibility, cut-plane policy, reset confirmation"})
    else:
        log=options.directory/"logs/latest.log"
        completed=log.exists() and "[SR boundary smoke] PASS" in log.read_text(errors="replace")
        checks.append({"name":"Compatibility profile","passed":completed,"detail":"Existing runtime assertions; extended screenshot series runs only on baseline profiles"})
    result={"version":options.version,"profile":options.profile,"passed":all(c["passed"] for c in checks),"checks":checks,"screenshots":len(files)}
    options.directory.mkdir(parents=True,exist_ok=True)
    (options.directory/"visual-results.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
    rows="".join(f'<tr><td>{"PASS" if c["passed"] else "FAIL"}</td><td>{html.escape(c["name"])}</td><td>{html.escape(c["detail"])}</td></tr>' for c in checks)
    cards=[]
    for path in files:
        key=path.stem.removeprefix("sr-visual-")
        label=scenarios.get(key,path.stem)
        cards.append(f'<article><h2>{html.escape(label)}</h2><a href="screenshots/{html.escape(path.name)}"><img loading="lazy" src="screenshots/{html.escape(path.name)}" alt="{html.escape(label)}"></a><small>{html.escape(path.name)}</small></article>')
    document=f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>SR visual checks {html.escape(options.version)}</title>
<style>body{{background:#15191f;color:#eee;font:16px system-ui;margin:24px}}h1{{font-size:26px}}h2{{font-size:17px}}table{{border-collapse:collapse;width:100%}}td{{padding:8px;border:1px solid #444}}.gallery{{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:20px}}img{{width:100%;image-rendering:auto}}article{{background:#222933;padding:12px}}a{{color:#9bd}}small{{color:#aaa}}p{{max-width:900px}}</style>
<h1>Selective Render — {html.escape(options.version)} / {html.escape(options.profile)}</h1><p>Automated result: <strong>{"PASS" if result["passed"] else "FAIL"}</strong>. Open a screenshot for full resolution. This report works offline after extracting the artifact.</p>
<p>These fixtures check standard rendering without an active shader pack. Screenshots help review decoration, clipping and UI appearance; they cannot prove every resource pack, shader or distant camera angle is correct.</p><table>{rows}</table><h1>Screenshots</h1><div class="gallery">{"".join(cards)}</div></html>"""
    (options.directory/"index.html").write_text(document,encoding="utf-8")
    for check in checks:
        print(("PASS" if check["passed"] else "FAIL")+": "+check["name"]+" — "+check["detail"])
    raise SystemExit(0 if result["passed"] else 1)


if __name__=="__main__":
    main()
