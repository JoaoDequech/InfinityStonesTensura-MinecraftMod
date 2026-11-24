"""Geometry for the giant hands. Coordinates are Blockbench px (Y up). Pose frame of the mod:
origin = knuckle line (y = 24 in Blockbench), +Y towards the arm, +Z palm side, +X thumb.
All cube coordinates are integers so box UV stays on the pixel grid."""

Y0 = 24


class Cube:
    def __init__(self, name, f, t, mat):
        self.name, self.f, self.t, self.mat = name, f, t, mat
        self.uv = None

    @property
    def size(self):
        return [self.t[i] - self.f[i] for i in range(3)]


class Grp:
    def __init__(self, name, origin, rot=(0, 0, 0)):
        self.name, self.origin, self.rot = name, origin, rot
        self.kids, self.cubes = [], []

    def group(self, name, origin, rot=(0, 0, 0)):
        g = Grp(name, origin, rot)
        self.kids.append(g)
        return g

    def box(self, x0, y0, z0, x1, y1, z1, mat, b=0):
        n = len(ALL) + 1
        if b == 0 or min(x1 - x0, y1 - y0, z1 - z0) <= 2 * b:
            self._add(f"b{n}", x0, y0, z0, x1, y1, z1, mat)
            return
        self._add(f"b{n}a", x0, y0 + b, z0 + b, x1, y1 - b, z1 - b, mat)
        self._add(f"b{n}b", x0 + b, y0, z0 + b, x1 - b, y1, z1 - b, mat)
        self._add(f"b{n}c", x0 + b, y0 + b, z0, x1 - b, y1 - b, z1, mat)

    def _add(self, name, x0, y0, z0, x1, y1, z1, mat):
        c = Cube(name, [x0, y0 + Y0, z0], [x1, y1 + Y0, z1], mat)
        self.cubes.append(c)
        ALL.append(c)


ALL = []

FINGER_XL = [-16, -8, 0, 8]
FINGER_M = [0.76, 0.92, 1.0, 0.95]
ARM_TILE = 40
WRIST_Y = 35
ARM_Y = 46

# gem centres (x, y, size) on the back of the hand, stone order mind, power, space, reality, soul, time
GEM_SPECS = [("mind", -12, 9, 6), ("power", -4, 9, 6), ("space", 4, 9, 6), ("reality", 12, 9, 6),
             ("soul", 0, 24, 8), ("time", 18, 16, 6)]
GEM_BACK_Z = -12


def build(kind):
    """kind = 'stone' or 'gold'. Returns the list of root groups."""
    ALL.clear()
    gold = kind == "gold"
    hand = Grp("hand", (0, 0 + Y0, 0))
    wrist = Grp("wrist", (0, WRIST_Y + Y0, 0))
    arm = Grp("arm", (0, ARM_Y + Y0, 0))

    # palm
    hand.box(-16, 0, -9, 16, 14, 9, "body", 3)
    hand.box(-15, 12, -9, 15, 26, 9, "body", 3)
    hand.box(-13, 24, -8, 13, 35, 8, "body", 2)
    for xl in FINGER_XL:
        hand.box(xl, 0, 9, xl + 7, 6, 11, "body", 1)
        if not gold:
            hand.box(xl, -3, -7, xl + 7, 4, -3, "body", 1)
    hand.box(8, 8, 9, 16, 22, 12, "body", 1)
    hand.box(-16, 10, 9, -8, 24, 11, "body", 1)
    hand.box(13, 3, -6, 19, 22, 8, "body", 2)
    if gold:
        hand.box(-14, 3, -12, 14, 33, -9, "panel", 2)
        hand.box(-17, -3, -12, 17, 3, -3, "trim", 2)
        for xl in FINGER_XL:
            hand.box(xl + 1, -2, -14, xl + 6, 2, -12, "plate", 1)
            hand.box(xl + 2, -1, -16, xl + 5, 1, -14, "plate", 0)
        hand.box(-6, 30, -14, 6, 34, -12, "trim", 1)
        for name, gx, gy, s in GEM_SPECS:
            h = s // 2
            gz = GEM_BACK_Z if name != "time" else -7
            hand.box(gx - h - 2, gy - h - 2, gz - 1, gx + h + 2, gy + h + 2, gz, "trim", 1)
            gg = hand.group("gem_" + name, (gx, gy + Y0, gz))
            gg.box(gx - h, gy - h, gz - 3, gx + h, gy + h, gz - 1, "gem:" + name, 0)
            gg.box(gx - h + 1, gy - h + 1, gz - 5, gx + h - 1, gy + h - 1, gz - 3, "gem:" + name, 0)
    else:
        hand.box(-12, 7, -11, 12, 31, -9, "panel", 1)
        hand.box(-14, 5, -12, -12, 32, -9, "trim", 0)
        hand.box(12, 5, -12, 14, 32, -9, "trim", 0)
        hand.box(-14, 31, -12, 14, 32, -9, "trim", 0)
        hand.box(-14, 5, -12, 14, 7, -9, "trim", 0)

    # fingers
    for i, xl in enumerate(FINGER_XL):
        m = FINGER_M[i]
        l1, l2, l3 = round(18 * m), round(14 * m), round(11 * m)
        f1 = hand.group(f"finger{i}", (xl + 3.5, 0 + Y0, 1))
        f1.box(xl, -l1, -3, xl + 7, 0, 5, "body", 1)
        if gold:
            f1.box(xl, -l1 + 2, -4, xl + 7, -1, -3, "plate", 0)
        else:
            f1.box(xl, -3, -4, xl + 7, -1, 5, "trim", 0)
        f2 = f1.group(f"finger{i}_mid", (xl + 3.5, -l1 + Y0, 1))
        f2.box(xl, -l1 - 3, -5, xl + 7, -l1 + 3, -2, "plate" if gold else "trim", 1)
        f2.box(xl + 1, -l1 - l2, -2, xl + 6, -l1, 4, "body", 1)
        if gold:
            f2.box(xl + 1, -l1 - l2 + 2, -3, xl + 6, -l1 - 1, -2, "plate", 0)
        else:
            f2.box(xl + 1, -l1 - 2, -3, xl + 6, -l1, 4, "trim", 0)
        f3 = f2.group(f"finger{i}_tip", (xl + 3.5, -l1 - l2 + Y0, 1))
        y3 = -l1 - l2
        f3.box(xl + 1, y3 - 3, -4, xl + 6, y3 + 2, -2, "plate" if gold else "trim", 1)
        f3.box(xl + 1, y3 - l3, -2, xl + 6, y3, 4, "body", 1)
        if gold:
            f3.box(xl + 1, y3 - l3 + 3, -3, xl + 6, y3 - 1, -2, "plate", 0)
            f3.box(xl + 2, y3 - l3 - 3, -1, xl + 5, y3 - l3, 3, "plate", 0)
        else:
            f3.box(xl + 2, y3 - l3 + 1, -3, xl + 5, y3 - 3, -2, "nail", 0)

    # thumb
    th = hand.group("thumb", (14, 12 + Y0, 1), (0, 0, 28))
    th.box(10, -6, -3, 18, 12, 5, "body", 1)
    th.box(10, 8, -4, 18, 11, 5, "plate" if gold else "trim", 0)
    th.box(10, -9, -5, 18, -3, -3, "plate" if gold else "trim", 1)
    tt = th.group("thumb_tip", (14, -6 + Y0, 1))
    tt.box(11, -18, -2, 17, -6, 4, "body", 1)
    if gold:
        tt.box(11, -16, -3, 17, -7, -2, "plate", 0)
        tt.box(12, -21, -1, 16, -18, 3, "plate", 0)
    else:
        tt.box(12, -17, -3, 16, -9, -2, "nail", 0)

    # wrist and forearm
    wrist.box(-19, WRIST_Y, -14, 19, 41, 14, "trim", 2)
    wrist.box(-17, 41, -13, 17, ARM_Y, 13, "body", 2)
    if gold:
        for s in (-1, 1):
            a, b = sorted((s * 19, s * 23))
            wrist.box(a, 37, -5, b, 45, 5, "plate", 1)
            a, b = sorted((s * 23, s * 26))
            wrist.box(a, 39, -3, b, 43, 3, "plate", 1)
        wrist.box(-8, WRIST_Y + 1, 14, 8, 44, 16, "trim", 1)
    t0, t1 = ARM_Y, ARM_Y + ARM_TILE
    arm.box(-15, t0, -11, 15, t1, 11, "panel", 2)
    for sx in (-1, 1):
        for sz in (-1, 1):
            xa, xb = sorted((sx * 13, sx * 17))
            za, zb = sorted((sz * 9, sz * 13))
            arm.box(xa, t0, za, xb, t1, zb, "trim", 1)
    arm.box(-18, t0 + 18, -14, 18, t0 + 22, 14, "trim", 1)
    return [hand, wrist, arm]


def walk(groups):
    for g in groups:
        yield g
        yield from walk(g.kids)


def cubes_of(groups):
    for g in walk(groups):
        yield from g.cubes

