# Makes the test pictures for the screenshot run (plain colours and titles).
import os, colorsys
from PIL import Image, ImageDraw, ImageFont
B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
os.makedirs("img", exist_ok=True)
def grad(w, h, hue, title="", size=60, sub=""):
    im = Image.new("RGB", (w, h)); d = ImageDraw.Draw(im)
    for y in range(h):
        r, g, b = colorsys.hsv_to_rgb(hue, 0.65, 0.25 + 0.6 * (1 - y / h))
        d.line([(0, y), (w, y)], fill=(int(r * 255), int(g * 255), int(b * 255)))
    for i in range(0, w + h, 60):
        d.line([(i, 0), (i - h, h)], fill=(255, 255, 255, 30), width=2)
    if title:
        f = ImageFont.truetype(B, size)
        lines = title.split("\n"); y = h * 0.62 - len(lines) * size * 0.6
        for ln in lines:
            tw = d.textlength(ln, font=f); d.text(((w - tw) / 2, y), ln, font=f, fill="white"); y += size * 1.15
    return im
def logo(text, w=600, h=220, col=(250, 200, 40)):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    f = ImageFont.truetype(B, 84); lines = text.split("\n"); y = 10
    for ln in lines:
        d.text((4, y), ln, font=f, fill=col, stroke_width=4, stroke_fill=(0, 0, 0)); y += 100
    im.save(f"img/logo_{text.split()[0].lower()}.png")
items = {   # id: (hue, title)
 "s_chase": (0.58, "THE GREAT\nCHASE"), "s_harbor": (0.08, "HARBOR\nSTATION"), "s_town": (0.33, "QUIET\nTOWN"),
 "m_magic": (0.75, "MOONLIT\nMAGIC"), "m_west": (0.1, "DUST\nRIDERS"), "m_run": (0.0, "THE\nRUNNER"), "m_glad": (0.12, "ARENA"),
 "m_enc": (0.45, "CASA\nVERDE"), "s_nine": (0.62, "PRECINCT\n99"),
 "se1": (0.6, "SEASON\n1"), "se2": (0.85, "SEASON\n2"), "se3": (0.1, "SEASON\n3"),
}
for k, (h, t) in items.items():
    grad(320, 480, h, t, 52).save(f"img/{k}_Primary.jpg", quality=80)
    grad(960, 540, h, "", 0).save(f"img/{k}_Backdrop.jpg", quality=55)
for n, h in [("e1", 0.55), ("e2", 0.15), ("e3", 0.3), ("e_nine", 0.62)]:
    grad(480, 270, h, n.upper(), 40).save(f"img/{n}_Primary.jpg", quality=75)
for n, h, t in [("v1", 0.0, "MOVIES"), ("v2", 0.6, "TV SHOWS"), ("v3", 0.3, "COLLECTIONS")]:
    grad(480, 270, h, t, 44).save(f"img/{n}_Primary.jpg", quality=75)
for n, h in [("p1", 0.05), ("p2", 0.95), ("p4", 0.5)]:
    im = grad(320, 480, h); d = ImageDraw.Draw(im); d.ellipse([90, 90, 230, 230], fill=(235, 200, 170)); d.rectangle([60, 260, 260, 480], fill=(60, 60, 90))
    im.save(f"img/{n}_Primary.jpg", quality=75)
grad(400, 400, 0.13, "THE BANK\nJOB", 48).save("img/book_b1.jpg", quality=80)
grad(400, 400, 0.7, "NIGHT\nTRAIN", 48).save("img/book_b2.jpg", quality=80)
for i, h in enumerate([0.1, 0.4, 0.7]): grad(400, 225, h, f"CH {i+1}", 40).save(f"img/ch_{i}.jpg", quality=70)
logo("THE GREAT\nCHASE"); logo("MOONLIT\nMAGIC", col=(230, 190, 120))
