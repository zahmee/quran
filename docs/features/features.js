/* شروحات نتلو — سكربت مشترك صغير بلا مكتبات:
   ١) العرض المتحرك للهاتف يتبع الخطوات المرقّمة
   ٢) شريط المشاركة أسفل كل صفحة
   ٣) البحث في صفحة الشروحات
   ٤) السابق والتالي بين الشروحات
   الصفحات تعمل كاملة دون هذا السكربت؛ هو تحسين فقط. */

(() => {
  "use strict";

  /* ترتيب الشروحات — يُستعمل للسابق والتالي. أضف الصفحة الجديدة هنا أيضًا. */
  const PAGES = [
    ["ayah.html", "نافذة الآية"],
    ["search.html", "البحث في القرآن"],
    ["tafsir.html", "التفسير والمصادر"],
    ["library.html", "مكتبة التفاسير"],
    ["surah-info.html", "عن السورة وفوائد الصفحة"],
    ["listen.html", "الاستماع للتلاوة"],
    ["memorize.html", "التكرار والحفظ"],
    ["offline-audio.html", "تنزيل التلاوة"],
    ["share.html", "مشاركة الآيات صورةً وصوتًا وفيديو"],
    ["progress.html", "الإحصائيات وخريطة الختمة"],
    ["backup.html", "النسخ الاحتياطي"],
    ["sync.html", "المزامنة بين أجهزتك"],
    ["customize.html", "المظهر وتخصيص الشاشة"],
    ["fullscreen.html", "ملء الشاشة: توسيع مساحة القراءة"],
    ["page-side.html", "صفحة يمين أم يسار؟"],
  ];

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- ١) العرض المتحرك ---------- */

  const LINES = 15;

  function buildPhone(host) {
    const title = host.dataset.title || "سورة البقرة";
    const buttons = (host.dataset.buttons || "⚙|⌕|◐|⋮").split("|");
    host.innerHTML = "";
    host.setAttribute("aria-hidden", "true");

    const bar = el("div", "pd-bar");
    bar.append(el("span", "t", title));
    const btns = el("span", "btns");
    buttons.forEach((b) => btns.append(el("span", "b", b)));
    bar.append(btns);

    const page = el("div", "pd-page");
    page.append(el("span", "frame"));
    for (let i = 0; i < LINES; i++) page.append(el("span", "pd-line"));

    const sheet = el("div", "pd-sheet");
    sheet.append(el("div", "grip"), el("div", "title"), el("div", "pd-chips"), el("div", "pd-rows"));

    const drawer = el("div", "pd-drawer");
    drawer.append(el("span", "dot"), el("span", "txt"));

    host.append(bar, page, sheet, drawer, el("div", "pd-toast"), el("span", "pd-touch"));
  }

  function render(host, step) {
    const d = step.dataset;
    const q = (s) => host.querySelector(s);
    const lines = host.querySelectorAll(".pd-line");
    const barBtns = host.querySelectorAll(".pd-bar .b");

    lines.forEach((l) => l.classList.remove("sel", "play", "flash"));
    nums(d.sel).forEach((n) => lines[n - 1] && lines[n - 1].classList.add("sel"));
    nums(d.play).forEach((n) => lines[n - 1] && lines[n - 1].classList.add("play"));
    nums(d.flash).forEach((n) => {
      const l = lines[n - 1];
      if (!l) return;
      void l.offsetWidth; // إعادة تشغيل الوميض
      l.classList.add("flash");
    });

    barBtns.forEach((b, i) => b.classList.toggle("pick", String(i + 1) === d.pickbar));

    const sheet = q(".pd-sheet");
    const chips = q(".pd-chips");
    const rows = q(".pd-rows");
    chips.innerHTML = "";
    rows.innerHTML = "";
    if (d.sheet !== undefined) {
      q(".pd-sheet .title").textContent = d.sheet;
      split(d.chips).forEach((c, i) => chips.append(el("span", "pd-chip" + (String(i + 1) === d.pick ? " pick" : ""), c)));
      split(d.rows).forEach((r, i) => rows.append(el("span", "pd-row" + (String(i + 1) === d.pick ? " pick" : ""), r)));
      sheet.classList.add("up");
    } else {
      sheet.classList.remove("up");
    }

    const drawer = q(".pd-drawer");
    drawer.classList.toggle("show", d.drawer !== undefined);
    if (d.drawer !== undefined) q(".pd-drawer .txt").textContent = d.drawer;

    const toast = q(".pd-toast");
    toast.classList.toggle("show", d.toast !== undefined);
    if (d.toast !== undefined) toast.textContent = d.toast;

    touch(host, d.touch);
  }

  /* data-touch="press:3" ضغط مطوّل على السطر ٣، "tap:5" لمس السطر ٥،
     "tap:bar" لمس زر الشريط المختار، "tap:pick" لمس العنصر المختار في النافذة. */
  function touch(host, spec) {
    const dot = host.querySelector(".pd-touch");
    dot.classList.remove("press", "tap");
    if (!spec) return;
    const [kind, where] = spec.split(":");
    let target = null;
    if (where === "bar") target = host.querySelector(".pd-bar .b.pick");
    else if (where === "pick") target = host.querySelector(".pd-chip.pick, .pd-row.pick");
    else target = host.querySelectorAll(".pd-line")[Number(where) - 1];
    if (!target) return;

    // النافذة تنزلق خلال ٤٥٠ جزءًا من الثانية؛ انتظرها قبل قياس موضع العنصر
    const delay = where === "pick" ? 480 : 40;
    setTimeout(() => {
      const box = host.getBoundingClientRect();
      const r = target.getBoundingClientRect();
      // العناصر المطلقة تُقاس من داخل إطار الهاتف، فيُطرح سُمكه
      const x = r.left - box.left - host.clientLeft + r.width * (where === "bar" || where === "pick" ? 0.5 : 0.35);
      dot.style.left = x + "px";
      dot.style.top = r.top - box.top - host.clientTop + r.height / 2 + "px";
      void dot.offsetWidth;
      dot.classList.add(kind === "press" ? "press" : "tap");
    }, delay);
  }

  /* مقطع حقيقي من التطبيق: كل خطوة لها data-t بالثواني، فتتلوّن الخطوة الجارية مع الفيديو،
     ولمس خطوة ينقل الفيديو إليها. يعمل الفيديو وحده حين يظهر في الشاشة ويتوقف حين يغيب. */
  function setupVideo(figure) {
    const video = figure.querySelector("video");
    const list = document.querySelector(figure.dataset.steps || ".steps");
    if (!video || !list) return;
    const steps = Array.from(list.querySelectorAll("li"));
    const times = steps.map((s) => Number(s.dataset.t || 0));

    function mark() {
      let k = 0;
      times.forEach((t, j) => { if (video.currentTime + 0.05 >= t) k = j; });
      steps.forEach((s, j) => {
        s.classList.toggle("on", j === k);
        s.setAttribute("aria-current", j === k ? "step" : "false");
      });
    }
    video.addEventListener("timeupdate", mark);
    video.addEventListener("seeked", mark);

    steps.forEach((s, j) => {
      s.tabIndex = 0;
      const go = () => { video.currentTime = times[j]; video.play().catch(() => {}); };
      s.addEventListener("click", go);
      s.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(); } });
    });

    if (reduceMotion) {
      video.controls = true; // لا تشغيل تلقائي لمن طلب تقليل الحركة
    } else {
      new IntersectionObserver((entries) => {
        if (entries[0].isIntersecting) video.play().catch(() => { video.controls = true; });
        else video.pause();
      }, { threshold: 0.4 }).observe(video);
    }
    // زر صغير للإيقاف والتشغيل، مثل العرض التوضيحي
    const controls = el("div", "demo-controls");
    const toggle = button("", "");
    const label = () => { toggle.textContent = video.paused ? "▶ تشغيل" : "❚❚ إيقاف"; };
    toggle.onclick = () => { if (video.paused) video.play().catch(() => {}); else video.pause(); };
    video.addEventListener("play", label);
    video.addEventListener("pause", label);
    controls.append(toggle);
    video.after(controls);
    label();
    mark();
  }

  function setupDemo(figure) {
    if (figure.classList.contains("demo-video")) return setupVideo(figure);
    const host = figure.querySelector(".phone-demo");
    const list = document.querySelector(figure.dataset.steps || ".steps");
    if (!host || !list) return;
    const steps = Array.from(list.querySelectorAll("li"));
    if (!steps.length) return;

    buildPhone(host);
    let i = 0;
    let timer = null;
    let visible = false;
    let playing = !reduceMotion;

    const controls = el("div", "demo-controls");
    const prev = button("› السابق", "الخطوة السابقة");
    const toggle = button("", "");
    const next = button("التالي ‹", "الخطوة التالية");
    controls.append(prev, toggle, next); // من اليمين: السابق ثم الإيقاف ثم التالي، كاتجاه القراءة
    // على الجوال تكون الخطوات تحت الهاتف، فيُكتب اسم الخطوة الجارية تحته
    const now = el("p", "demo-now");
    host.after(controls, now);
    const digits = (n) => String(n).replace(/\d/g, (c) => "٠١٢٣٤٥٦٧٨٩"[c]);

    function show(n) {
      i = (n + steps.length) % steps.length;
      steps.forEach((s, k) => {
        s.classList.toggle("on", k === i);
        s.setAttribute("aria-current", k === i ? "step" : "false");
      });
      render(host, steps[i]);
      const name = steps[i].querySelector("strong");
      now.textContent = `${digits(i + 1)} من ${digits(steps.length)}: ${name ? name.textContent : ""}`;
    }
    function label() {
      toggle.textContent = playing ? "❚❚ إيقاف" : "▶ تشغيل";
      toggle.setAttribute("aria-label", playing ? "إيقاف العرض" : "تشغيل العرض");
    }
    function schedule() {
      clearTimeout(timer);
      if (playing && visible) timer = setTimeout(() => { show(i + 1); schedule(); }, Number(steps[i].dataset.ms || 3200));
    }
    function pause() { playing = false; label(); schedule(); }

    prev.onclick = () => { pause(); show(i - 1); };
    next.onclick = () => { pause(); show(i + 1); };
    toggle.onclick = () => { playing = !playing; label(); schedule(); };
    steps.forEach((s, k) => {
      s.tabIndex = 0;
      const go = () => { pause(); show(k); };
      s.addEventListener("click", go);
      s.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(); } });
    });

    new IntersectionObserver((entries) => {
      visible = entries[0].isIntersecting;
      schedule();
    }, { threshold: 0.35 }).observe(host);

    label();
    show(0);
  }

  /* ---------- ٢) المشاركة ---------- */

  function setupShare(bar) {
    const url = (document.querySelector('link[rel="canonical"]') || {}).href || location.href;
    const title = document.title;
    const text = (document.querySelector('meta[name="description"]') || {}).content || "";
    const status = bar.querySelector(".share-status");
    const say = (m) => { if (status) { status.textContent = m; setTimeout(() => (status.textContent = ""), 3500); } };

    const native = bar.querySelector("[data-share]");
    if (native) {
      if (!navigator.share) native.hidden = true;
      native.addEventListener("click", async () => {
        try { await navigator.share({ title, text, url }); } catch (e) { /* أغلق القارئ نافذة المشاركة */ }
      });
    }
    const copy = bar.querySelector("[data-copy]");
    if (copy) copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(url);
        say("نُسخ الرابط.");
      } catch (e) {
        window.prompt("انسخ الرابط:", url);
      }
    });
    const wa = bar.querySelector("[data-wa]");
    if (wa) wa.href = "https://wa.me/?text=" + encodeURIComponent(title + "\n" + url);
    const tg = bar.querySelector("[data-tg]");
    if (tg) tg.href = "https://t.me/share/url?url=" + encodeURIComponent(url) + "&text=" + encodeURIComponent(title);
  }

  /* ---------- ٣) البحث ---------- */

  // طيّ عربي بسيط: بلا تشكيل ولا تطويل، والهمزات والألف المقصورة والتاء المربوطة موحّدة
  function fold(s) {
    return (s || "")
      .replace(/[ً-ٰٟۖ-ۭـ]/g, "")
      .replace(/[أإآٱ]/g, "ا")
      .replace(/ى/g, "ي")
      .replace(/ة/g, "ه")
      .replace(/ؤ/g, "و")
      .replace(/ئ/g, "ي")
      .toLowerCase()
      .trim();
  }

  // البحث وأزرار الموضوع معًا: تظهر البطاقة إن طابقت الكلمات والموضوع المختار
  function setupSearch(input) {
    const cards = Array.from(document.querySelectorAll(".card"));
    const chips = Array.from(document.querySelectorAll(".chip"));
    const chipBar = document.querySelector(".chips");
    const empty = document.querySelector(".search-empty");
    const hay = cards.map((c) => fold(c.textContent + " " + (c.dataset.k || "")));
    let group = "";

    function apply() {
      const words = fold(input.value).split(/\s+/).filter(Boolean);
      let shown = 0;
      cards.forEach((c, k) => {
        const ok = (!group || c.dataset.g === group) && words.every((w) => hay[k].includes(w));
        c.classList.toggle("hidden", !ok);
        if (ok) shown++;
      });
      if (empty) empty.classList.toggle("show", shown === 0);
    }

    if (chipBar) chipBar.hidden = false; // الأزرار لا تعمل دون السكربت، فلا تظهر دونه
    chips.forEach((chip) => chip.addEventListener("click", () => {
      group = chip.dataset.g;
      chips.forEach((c) => c.setAttribute("aria-pressed", String(c === chip)));
      apply();
    }));
    input.addEventListener("input", apply);
  }

  /* ---------- ٤) السابق والتالي ---------- */

  function setupPager(nav) {
    const here = location.pathname.split("/").pop() || "index.html";
    const k = PAGES.findIndex((p) => p[0] === here);
    if (k < 0) return;
    const link = (p, cls, cap) => {
      if (!p) return el("span", "empty");
      const a = el("a", cls);
      a.href = p[0];
      a.append(el("small", "", cap), el("strong", "", p[1]));
      return a;
    };
    nav.append(link(PAGES[k - 1], "prev", "› الشرح السابق"), link(PAGES[k + 1], "next", "الشرح التالي ‹"));
  }

  /* ---------- أدوات ---------- */

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  }
  function button(text, aria) {
    const b = el("button", "", text);
    b.type = "button";
    if (aria) b.setAttribute("aria-label", aria);
    return b;
  }
  const split = (s) => (s ? s.split("|") : []);
  const nums = (s) => (s ? s.split(",").map(Number) : []);

  document.querySelectorAll(".demo").forEach(setupDemo);
  document.querySelectorAll(".share-bar").forEach(setupShare);
  document.querySelectorAll("#q").forEach(setupSearch);
  document.querySelectorAll(".pager").forEach(setupPager);
})();
