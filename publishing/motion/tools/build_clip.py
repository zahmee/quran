"""Build a HyperFrames project for one Natlu explainer clip from a JSON spec.

    python publishing/motion/tools/build_clip.py publishing/motion/specs/ayah.json

writes publishing/motion/<slug>/ (index.html, compositions/film.html, hyperframes.json,
package.json, the font) in the look of page-side/ and full-screen/: a 1080x1920 ivory ground,
the real app recording inside a phone, a camera that pushes in on the part being explained,
short titles in the top band, a gold fingertip on taps, gold rings around details, an end card.

Spec (all positions in SOURCE pixels of the 1080x2400 recording; times in clip seconds):
{
  "slug": "ayah", "duration": 12,
  "media":  [{"src": "ayah.mp4", "start": 0, "dur": 9, "mstart": 0},
             {"src": "settings.png", "start": 9, "dur": 2}],          # files in <slug>/assets/app/
  "camera": [[t, dur, k, sx, sy, tx, ty], ...]  or  [t, dur, "whole"]   # dur 0 = cut
  "titles": [{"start": 0, "dur": 1.4, "kicker": "...", "text": "اضغط *مطولًا*"}],   # *x* = accent
  "taps":   [{"t": 1.2, "x": 540, "y": 900, "hold": 0.6}],              # hold>0 = long press
  "drags":  [{"t": 3, "dur": 1.2, "x": 40, "y": 175, "x2": 40, "y2": 1100}],
  "rings":  [{"start": 2, "end": 3, "x": 0, "y": 120, "w": 80, "h": 100}],
  "swaps":  [9],                                    # blur swap on the screen at these times
  "masks":  [{"start": 6, "end": 9, "x": 520, "y": 1080, "w": 360, "h": 60}],  # cover private text
  "end":    {"start": 10, "title": "سطر *أول*|سطر ثانٍ", "sub": "..."}
}
"""
import html, json, pathlib, re, shutil, sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
FONT = ROOT / "page-side" / "assets" / "fonts" / "Alexandria.ttf"
CLI = "0.9.91"


def words(text, cls_acc="acc"):
    """'اضغط *مطولًا* ← هنا' -> spans; *x* is the accent, ← is gold."""
    out = []
    for part in re.split(r"(\*[^*]+\*)", text):
        if not part:
            continue
        acc = part.startswith("*")
        for w in part.strip("*").split():
            cls = "w"
            if acc:
                cls += " " + cls_acc
            if w in ("←", "·"):
                cls += " gold"
            out.append(f'<span class="{cls}">{html.escape(w)}</span>')
    return " ".join(out)


def build(spec_path):
    spec = json.loads(pathlib.Path(spec_path).read_text(encoding="utf-8"))
    slug, dur = spec["slug"], spec["duration"]
    proj = ROOT / slug
    (proj / "compositions").mkdir(parents=True, exist_ok=True)
    (proj / "assets" / "fonts").mkdir(parents=True, exist_ok=True)
    (proj / "assets" / "app").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(FONT, proj / "assets" / "fonts" / "Alexandria.ttf")

    media = []
    for i, m in enumerate(spec["media"]):
        src = f"assets/app/{m['src']}"
        common = f'id="m{i}" class="clip" data-start="{m["start"]}" data-duration="{m["dur"]}" data-track-index="3"'
        if m["src"].endswith(".png") or m["src"].endswith(".jpg"):
            media.append(f'<img {common} src="{src}" alt="" />')
        else:
            media.append(f'<video {common} src="{src}" data-media-start="{m.get("mstart", 0)}" muted playsinline></video>')

    titles = []
    for i, t in enumerate(spec.get("titles", [])):
        hook = t["start"] == 0 and not t.get("kicker")
        kicker = f'<p>{html.escape(t["kicker"])}</p>' if t.get("kicker") else ""
        tag = "h1" if hook else "h2"
        size = f' style="font-size: {t["size"]}px"' if t.get("size") else ""
        lines = "<br />".join(words(x) for x in t["text"].split("|"))
        titles.append(
            f'<div id="t{i}" class="band clip" data-start="{t["start"]}" data-duration="{t["dur"]}" data-track-index="5">'
            f"{kicker}<{tag}{size}>{lines}</{tag}></div>")

    rings = "".join(f'<div id="r{i}" class="ring"></div>' for i in range(len(spec.get("rings", []))))
    rings += "".join(f'<div id="k{i}" class="mask"></div>' for i in range(len(spec.get("masks", []))))
    end = spec.get("end")
    end_html = ""
    if end:
        lines = "<br />".join(words(x) for x in end["title"].split("|"))
        end_html = (f'<div id="end" class="clip" data-start="{end["start"]}" data-duration="{round(dur - end["start"], 3)}" data-track-index="6">'
                    f'<div class="end-in"><h1>{lines}</h1><div class="rule" id="rule"></div>'
                    f'<p id="end-sub">{html.escape(end.get("sub", "نتلو"))}</p></div></div>')

    cfg = {k: spec.get(k, []) for k in ("camera", "titles", "taps", "drags", "rings", "swaps", "masks")}
    cfg["end"] = end
    cfg["phoneOut"] = spec.get("phoneOut", end["start"] - 0.2 if end else dur)

    film = TEMPLATE.replace("{{MEDIA}}", "\n          ".join(media)) \
        .replace("{{RINGS}}", rings) \
        .replace("{{TITLES}}", "\n    ".join(titles)) \
        .replace("{{END}}", end_html) \
        .replace("{{CFG}}", json.dumps(cfg, ensure_ascii=False))
    (proj / "compositions" / "film.html").write_text(film, encoding="utf-8", newline="\n")
    (proj / "index.html").write_text(INDEX.replace("{{DUR}}", str(dur)), encoding="utf-8", newline="\n")
    (proj / "hyperframes.json").write_text(json.dumps({
        "$schema": "https://hyperframes.heygen.com/schema/hyperframes.json",
        "registry": "https://raw.githubusercontent.com/heygen-com/hyperframes/main/registry",
        "paths": {"blocks": "compositions", "components": "compositions/components", "assets": "assets"},
        "media": {"autoProxy": True}, "authoringSkill": "hyperframes"}, indent=2), encoding="utf-8")
    (proj / "package.json").write_text(json.dumps({
        "name": slug, "private": True, "type": "module",
        "scripts": {k: f"npx --yes hyperframes@{CLI} {v}" for k, v in
                    (("dev", "preview"), ("check", "check"), ("render", "render"))}}, indent=2), encoding="utf-8")
    print("built", proj)


INDEX = """<!doctype html>
<html lang="ar" dir="rtl" data-resolution="portrait">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1080, height=1920" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
    <style>
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { margin: 0; width: 1080px; height: 1920px; overflow: hidden; background: #f6f0e4; }
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="{{DUR}}" data-width="1080" data-height="1920">
      <div id="film" data-composition-id="film" data-composition-src="compositions/film.html" data-start="0"
        data-duration="{{DUR}}" data-track-index="1" data-track-kind="graphics" data-width="1080" data-height="1920"></div>
    </div>
    <script>
      window.__timelines = window.__timelines || {};
      window.__timelines["main"] = gsap.timeline({ paused: true });
    </script>
  </body>
</html>
"""

TEMPLATE = """<template>
  <style>
    @font-face { font-family: "Alexandria"; src: url("assets/fonts/Alexandria.ttf"); font-weight: 100 900; }
    #root { position: absolute; inset: 0; overflow: hidden; background: #f6f0e4; color: #173126; font-family: "Alexandria"; direction: rtl; }
    .ground { position: absolute; inset: 0; background: radial-gradient(120% 70% at 50% 40%, #fbf7ee 0%, #f6f0e4 55%, #ece3d0 100%); }
    .band { position: absolute; left: 110px; right: 120px; top: 250px; height: 300px; text-align: center; }
    .band h1 { font-size: 108px; font-weight: 800; line-height: 1.2; }
    .band h2 { font-size: 74px; font-weight: 800; line-height: 1.22; }
    .band p { font-size: 44px; font-weight: 500; color: #6d7a6f; margin-bottom: 18px; }
    .w { display: inline-block; }
    .acc { color: #2f7a52; }
    .gold { color: #b39458; }
    #cam { position: absolute; left: 0; top: 0; width: 1080px; height: 1920px; transform-origin: 0 0; }
    #phone { position: absolute; left: 226px; top: 506px; width: 628px; height: 1361px; border-radius: 60px; background: #0e241b; box-shadow: 0 40px 90px rgba(20, 52, 40, .28); }
    #screen { position: absolute; left: 14px; top: 14px; width: 600px; height: 1333px; border-radius: 46px; overflow: hidden; background: #fffdf7; }
    #screen video, #screen img { position: absolute; left: 0; top: 0; width: 600px; height: 1333px; object-fit: cover; }
    #touch { position: absolute; z-index: 4; left: 0; top: 0; width: 34px; height: 34px; margin: -17px 0 0 -17px; border-radius: 50%; background: rgba(179, 148, 88, .55); border: 2px solid #b39458; opacity: 0; }
    .ring { position: absolute; z-index: 3; left: 0; top: 0; border: 3px solid #b39458; border-radius: 14px; opacity: 0; box-shadow: 0 0 0 9999px rgba(23, 49, 38, .14); }
    .mask { position: absolute; z-index: 2; left: 0; top: 0; border-radius: 10px; background: #fbfaf5; opacity: 0; }
    #veil { position: absolute; left: 0; top: 0; width: 1080px; height: 600px; background: linear-gradient(#f6f0e4 0, #f6f0e4 540px, rgba(246, 240, 228, 0) 600px); }
    #end { position: absolute; inset: 0; }
    .end-in { position: absolute; left: 140px; right: 150px; top: 700px; text-align: center; }
    .end-in h1 { font-size: 100px; font-weight: 800; line-height: 1.35; }
    .end-in p { margin-top: 44px; font-size: 46px; font-weight: 500; color: #6d7a6f; }
    .rule { width: 220px; height: 3px; margin: 40px auto 0; background: #b39458; }
  </style>

  <div id="root" data-composition-id="film" data-width="1080" data-height="1920">
    <div class="ground"></div>
    <div id="cam" data-layout-allow-overflow>
      <div id="phone">
        <div id="screen">
          {{MEDIA}}
          {{RINGS}}
          <div id="touch"></div>
        </div>
      </div>
    </div>
    <div id="veil"></div>
    {{TITLES}}
    {{END}}
  </div>

  <script>
    (() => {
      const C = {{CFG}};
      const tl = gsap.timeline({ paused: true });
      // The 1080x2400 recording is drawn 600 px wide at (240, 520) on the canvas.
      const S = 600 / 1080, SX = 240, SY = 520;
      const WHOLE = { scale: 0.92, x: 43, y: 150 };
      const cam = (k, sx, sy, tx, ty) => ({ scale: k, x: tx - k * (SX + sx * S), y: ty - k * (SY + sy * S) });
      const at = (sx, sy) => ({ x: sx * S, y: sy * S });
      const has = (sel) => document.querySelector(sel) !== null;

      tl.set("#cam", WHOLE, 0);
      tl.set("#touch", { opacity: 0 }, 0);
      tl.set(".ring", { opacity: 0 }, 0);
      tl.set("#screen", { filter: "blur(0px)" }, 0);

      // camera
      C.camera.forEach(([t, d, k, sx, sy, tx, ty]) => {
        const to = k === "whole" ? WHOLE : cam(k, sx, sy, tx, ty);
        if (d === 0) tl.set("#cam", to, t);
        else tl.to("#cam", { ...to, duration: d, ease: "power2.inOut" }, t);
      });
      // blur swap on the screen when the picture cuts
      C.swaps.forEach((t) => tl.fromTo("#screen", { filter: "blur(10px)" }, { filter: "blur(0px)", duration: 0.22, ease: "power2.out", immediateRender: false }, t));

      // titles: the hook is on screen at frame 0; the rest blur in word by word and leave with a blur
      C.titles.forEach((t, i) => {
        const id = `#t${i}`;
        if (t.start > 0) {
          if (has(`${id} p`)) tl.fromTo(`${id} p`, { opacity: 0, filter: "blur(8px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2, ease: "power4.out" }, t.start);
          tl.fromTo(`${id} .w`, { opacity: 0, filter: "blur(6px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.16, stagger: 0.08, ease: "power4.out" }, t.start + 0.05);
        }
        tl.to(id, { opacity: 0, filter: "blur(8px)", duration: 0.16, ease: "power3.in" }, t.start + t.dur - 0.17);
      });

      // taps (a hold > 0 is a long press: the fingertip stays down)
      C.taps.forEach((p) => {
        const hold = p.hold || 0;
        tl.set("#touch", at(p.x, p.y), p.t - 0.3);
        tl.fromTo("#touch", { opacity: 0, scale: 1.5 }, { opacity: 1, scale: 1, duration: 0.2, ease: "power2.out", immediateRender: false }, p.t - 0.3);
        tl.to("#touch", { scale: 0.8, duration: 0.1, ease: "power2.in" }, p.t - 0.08);
        if (hold) tl.to("#touch", { scale: 1.25, opacity: 0.7, duration: hold, ease: "power1.out" }, p.t + 0.02);
        tl.to("#touch", { opacity: 0, scale: 1.1, duration: 0.25, ease: "power2.out" }, p.t + 0.1 + hold);
      });
      C.drags.forEach((d) => {
        tl.set("#touch", at(d.x, d.y), d.t - 0.25);
        tl.fromTo("#touch", { opacity: 0, scale: 1.5 }, { opacity: 1, scale: 0.85, duration: 0.2, ease: "power2.out", immediateRender: false }, d.t - 0.25);
        tl.to("#touch", { ...at(d.x2, d.y2), duration: d.dur, ease: "power1.inOut" }, d.t);
        tl.to("#touch", { opacity: 0, duration: 0.2, ease: "power2.out" }, d.t + d.dur + 0.05);
      });

      // gold rings around a detail, sized in source pixels
      C.rings.forEach((r, i) => {
        tl.set(`#r${i}`, { x: r.x * S, y: r.y * S, width: r.w * S, height: r.h * S }, 0);
        tl.fromTo(`#r${i}`, { opacity: 0, scale: 1.25 }, { opacity: 1, scale: 1, duration: 0.4, ease: "expo.out", immediateRender: false }, r.start);
        tl.to(`#r${i}`, { opacity: 0, duration: 0.15, ease: "power3.in" }, r.end);
      });

      // masks: cover private text (an account name) while it is on screen
      tl.set(".mask", { opacity: 0 }, 0);
      C.masks.forEach((m, i) => {
        tl.set(`#k${i}`, { x: m.x * S, y: m.y * S, width: m.w * S, height: m.h * S, opacity: 1 }, m.start);
        tl.set(`#k${i}`, { opacity: 0 }, m.end);
      });

      // end card
      tl.to("#phone", { opacity: 0, filter: "blur(12px)", duration: 0.22, ease: "power3.in" }, C.phoneOut);
      if (C.end) {
        tl.fromTo("#end .w", { opacity: 0, filter: "blur(6px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.16, stagger: 0.08, ease: "power4.out" }, C.end.start + 0.05);
        tl.fromTo("#rule", { scaleX: 0 }, { scaleX: 1, duration: 0.5, ease: "expo.out" }, C.end.start + 0.35);
        tl.fromTo("#end-sub", { opacity: 0, filter: "blur(6px)" }, { opacity: 1, filter: "blur(0px)", duration: 0.2, ease: "power4.out" }, C.end.start + 0.5);
      }
      window.__timelines["film"] = tl;
    })();
  </script>
</template>
"""

if __name__ == "__main__":
    for p in sys.argv[1:]:
        build(p)
