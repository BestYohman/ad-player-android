# A stand-in for the PC (test data only), so GitHub can run the app on an emulator and take screenshots.
import json, os, re, http.server, socketserver, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, "img")

def img(i, kind="Primary"): return f"/api/play/img/{i}/{kind}"

def it(id, type, name, **k):
    d = {"id": id, "type": type, "name": name, "label": k.pop("label", name), "length": "", "played": False, "resume": "",
         "resume_ticks": 0, "has_ad": True, "pct": 0, "overview": "", "year": "", "rating": "", "score": "", "genres": ""}
    d.update(k)
    return d

LOREM = ("Eleven teams race around the world, solving clues and doing tasks in every city they reach. The last team to "
         "arrive at each stop may be sent home, and the first to the finish line wins the prize.")
SERIES = [
    it("s_chase", "Series", "The Great Chase", poster=img("s_chase"), thumb=img("s_chase", "Backdrop"), backdrop=img("s_chase", "Backdrop"),
       year=2001, years="2001 - Present", unplayed=406, score=6.9, rating="TV-PG", genres="Reality", overview=LOREM),
    it("s_harbor", "Series", "Harbor Station", poster=img("s_harbor"), year=2012, years="2012 - Present", unplayed=299),
    it("s_town", "Series", "Quiet Town", poster=img("s_town"), year=1997, years="1997 - Present", unplayed=334),
]
MOVIES = [
    it("m_magic", "Movie", "Moonlit Magic 2", label="Moonlit Magic 2 (2026)", poster=img("m_magic"), thumb=img("m_magic", "Backdrop"),
       backdrop=img("m_magic", "Backdrop"), year=2026, rt="2h 10m", length="2 hours 10 minutes", rating="PG-13", score=7.1, genres="Romance",
       overview="The Owens-like sisters must face an old curse that threatens to pull their family apart once and for all."),
    it("m_west", "Movie", "Dust Riders", poster=img("m_west"), year=1966, played=True),
    it("m_run", "Movie", "The Runner", poster=img("m_run"), year=1987, pct=22, resume="25 minutes"),
    it("m_glad", "Movie", "Arena", poster=img("m_glad"), year=2000, has_ad=False),
]
EPS = [
    it("e1", "Episode", "The Race Begins", label="The Great Chase, season 1 episode 1: The Race Begins", se=1, ep=1, series_name="The Great Chase",
       series_id="s_chase", season_id="se1", thumb=img("e1"), poster=img("s_chase"), aired="Sep 5, 2001", rt="44m", length="44 minutes",
       overview="The teams begin in New York. They need to travel to Johannesburg, South Africa. Cooperation problems surface early on with a few of the couples."),
    it("e2", "Episode", "Divide and Conquer", label="The Great Chase, season 1 episode 2: Divide and Conquer", se=1, ep=2, series_name="The Great Chase",
       series_id="s_chase", season_id="se1", thumb=img("e2"), aired="Sep 12, 2001", rt="44m", pct=35, resume="15 minutes",
       overview="The teams start out in a small village, where they must find the museum without any signs to show the way."),
    it("e3", "Episode", "The Long Night", label="The Great Chase, season 1 episode 3: The Long Night", se=1, ep=3, series_name="The Great Chase",
       series_id="s_chase", season_id="se1", thumb=img("e3"), aired="Sep 19, 2001", rt="43m", has_ad=False,
       overview="An overnight train ride shakes up the order, and one team's map goes missing."),
]
NINE = it("e_nine", "Episode", "The Venue", label="Precinct 99, season 5 episode 6: The Venue", se=5, ep=6, series_name="Precinct 99",
          series_id="s_nine", thumb=img("e_nine"), pct=30, resume="9 minutes")
ENC = it("m_enc", "Movie", "Casa Verde", thumb=img("m_enc", "Backdrop"), poster=img("m_enc"), year=2021, pct=60, resume="1 hour")
SEASONS = [it(f"se{n}", "Season", f"Season {n}", poster=img(f"se{n}"), unplayed=u, series_name="The Great Chase", series_id="s_chase")
           for n, u in [(1, 13), (2, 6), (3, 6)]]
PEOPLE = [{"id": "p1", "name": "Phil Host", "role": "Host", "image": img("p1")}, {"id": "p2", "name": "Rachel Runner", "role": "Rachel", "image": img("p2")},
          {"id": "p3", "name": "Andrew Simpson", "role": "Andrew Simpson", "image": ""}, {"id": "p4", "name": "Dana Doe", "role": "Dana", "image": img("p4")}]
BOOKS = [{"type": "Book", "id": "b1", "label": "The Bank Job", "author": "Ben Writer", "narrator": "Sam Voice", "length": "9 hours 12 minutes",
          "chapters": 3, "resume": "2 hours 1 minute", "pct": 22, "poster": "/api/play/books/cover/b1"},
         {"type": "Book", "id": "b2", "label": "Night Train", "author": "Ann Author", "length": "7 hours", "chapters": 3, "poster": "/api/play/books/cover/b2"}]

def info(i):
    if i == "s_chase":
        d = dict(SERIES[0]); d.update(logo="/api/play/img/logo_the/Logo", studio="CBS", season_count=39, seasons=SEASONS, people=PEOPLE,
                                      next_up=EPS[1], similar=SERIES[1:] + MOVIES[:2], favorite=False, series_id="s_chase")
        return d
    if i == "m_magic":
        d = dict(MOVIES[0]); d.update(logo="/api/play/img/logo_moonlit/Logo", video_info="1080p H264", audio_info="AAC 5.1 (Default)",
                                       trailer="https://example.com/trailer", tagline="Everything you've wished for.", directors="Susan Director",
                                       people=PEOPLE, similar=MOVIES[1:], chapters=[{"start": 0, "name": "Opening", "image": "/api/play/chapimg/m/0"},
                                       {"start": 600, "name": "The Return", "image": "/api/play/chapimg/m/1"}, {"start": 1800, "name": "The Curse", "image": "/api/play/chapimg/m/2"}])
        return d
    if i == "se1":
        d = dict(SEASONS[0]); d.update(logo="/api/play/img/logo_the/Logo", backdrop=img("s_chase", "Backdrop"), series_name="The Great Chase", overview=LOREM)
        return d
    for e in EPS + MOVIES + SERIES + [NINE, ENC]:
        if e["id"] == i:
            d = dict(e); d.update(logo="/api/play/img/logo_the/Logo" if e.get("series_id") == "s_chase" else "", chapters=[], people=[])
            return d
    return {"error": "Not found"}

def route(path, q):
    if path == "/api/play/login": return {"token": "tok", "message": "Signed in as Yoh."}
    if path == "/api/play/me": return {"signed_in": True, "name": "Yoh", "role": "watch", "server_id": "mock", "home_urls": []}
    if path == "/api/play/devices": return {"devices": [], "other": []}
    if path == "/api/play/together/list": return {"rooms": []}
    if path == "/api/play/home":
        return {"libraries": [{"id": "v1", "name": "Movies", "type": "movies", "image": img("v1")},
                              {"id": "v2", "name": "TV shows", "type": "tvshows", "image": img("v2")},
                              {"id": "v3", "name": "Collections", "type": "boxsets", "image": img("v3")}],
                "resume": [NINE, ENC], "next_up": [EPS[1]],
                "latest": [{"id": "v1", "name": "Movies", "type": "movies", "items": MOVIES},
                           {"id": "v2", "name": "TV shows", "type": "tvshows", "items": SERIES}]}
    if path == "/api/play/info": return info(q.get("id", ""))
    if path == "/api/play/list":
        k = q.get("kind", "")
        if k == "episodes": return {"items": EPS}
        if k == "library": return {"items": MOVIES * 4 if q.get("parent") == "v1" else SERIES * 4}
        if k == "favorites": return {"items": SERIES[:1] + MOVIES[:2]}
        if k == "search": return {"items": SERIES[:1] + MOVIES[:2] + EPS[:1]}
        return {"items": []}
    if path == "/api/play/books/list":
        k = q.get("kind", "")
        if k == "books_chapters": return {"items": [{"type": "Chapter", "id": f"b1|{n}", "label": t, "at": a, "start": s, "book": "b1", "book_title": "The Bank Job"}
                                                    for n, (t, a, s) in enumerate([("Chapter 1", "0 seconds", 0), ("Chapter 2", "41 minutes", 2460), ("Chapter 3", "1 hour 30 minutes", 5400)])]}
        return {"items": BOOKS[:1] if k == "books_continue" else BOOKS}
    if path == "/api/play/video/prepare":
        return {"url": "/media/test.mp4", "tok": "t", "title": "Moonlit Magic 2 (2026)", "has_next": False, "runtime_ticks": 78000000000, "mbps": 6}
    if path in ("/api/play/save", "/api/play/video/stop", "/api/play/played", "/api/play/favorite"): return {"message": "OK"}
    return None

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, f, *a): print("mock:", f % a, flush=True)
    def send(self, code, body, ct):
        self.send_response(code); self.send_header("Content-Type", ct); self.send_header("Content-Length", str(len(body))); self.end_headers()
        self.wfile.write(body)
    def file(self, p, ct):
        if not os.path.isfile(p): return self.send(404, b"no", "text/plain")
        data = open(p, "rb").read()
        rng = self.headers.get("Range")
        if rng and ct.startswith("video"):
            a, _, b = rng.replace("bytes=", "").partition("-"); a = int(a); b = int(b) if b else len(data) - 1
            self.send_response(206); self.send_header("Content-Type", ct); self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Range", f"bytes {a}-{b}/{len(data)}"); self.send_header("Content-Length", str(b - a + 1)); self.end_headers()
            self.wfile.write(data[a:b + 1]); return
        self.send(200, data, ct)
    def do_GET(self):
        u = urllib.parse.urlparse(self.path); q = {k: v[0] for k, v in urllib.parse.parse_qs(u.query).items()}
        m = re.match(r"/api/play/img/(\w+)/(\w+)$", u.path)
        if m:
            i, kind = m.groups()
            if kind == "Logo": return self.file(os.path.join(IMG, f"{i}.png"), "image/png")
            p = os.path.join(IMG, f"{i}_{kind}.jpg")
            if not os.path.isfile(p) and kind in ("Backdrop", "Thumb"): p = os.path.join(IMG, f"{i}_Primary.jpg")
            return self.file(p, "image/jpeg")
        m = re.match(r"/api/play/chapimg/\w+/(\d+)$", u.path)
        if m: return self.file(os.path.join(IMG, f"ch_{m.group(1)}.jpg"), "image/jpeg")
        m = re.match(r"/api/play/books/cover/(\w+)$", u.path)
        if m: return self.file(os.path.join(IMG, f"book_{m.group(1)}.jpg"), "image/jpeg")
        if u.path == "/media/test.mp4": return self.file(os.path.join(HERE, "test.mp4"), "video/mp4")
        r = route(u.path, q)
        if r is None: return self.send(404, b'{"error":"no"}', "application/json")
        self.send(200, json.dumps(r).encode(), "application/json")
    def do_POST(self):
        n = int(self.headers.get("Content-Length") or 0); body = self.rfile.read(n).decode()
        q = {k: v[0] for k, v in urllib.parse.parse_qs(body).items()}
        r = route(urllib.parse.urlparse(self.path).path, q)
        self.send(200, json.dumps(r if r is not None else {"message": "OK"}).encode(), "application/json")

socketserver.ThreadingTCPServer.allow_reuse_address = True
socketserver.ThreadingTCPServer(("0.0.0.0", 8080), H).serve_forever()
