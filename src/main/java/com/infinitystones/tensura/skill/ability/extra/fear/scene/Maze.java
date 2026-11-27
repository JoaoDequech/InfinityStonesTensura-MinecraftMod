package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import java.util.ArrayDeque;
import java.util.Random;

/** Grid maze (depth-first carving plus extra loops). Cell (x, y): {@link #east} / {@link #south} say whether the wall to the next cell is open. */
public final class Maze {
    public final int w, h;
    private final boolean[][] east, south;
    private final boolean wrap;

    public Maze(int w, int h, boolean wrap, int extraOpenings, Random r) {
        this.w = w;
        this.h = h;
        this.wrap = wrap;
        east = new boolean[w][h];
        south = new boolean[w][h];
        boolean[][] seen = new boolean[w][h];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{r.nextInt(w), r.nextInt(h)});
        seen[stack.peek()[0]][stack.peek()[1]] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            int start = r.nextInt(4);
            boolean moved = false;
            for (int i = 0; i < 4 && !moved; i++) {
                int[] d = dirs[(start + i) % 4];
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (wrap) { nx = Math.floorMod(nx, w); ny = Math.floorMod(ny, h); }
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[nx][ny]) continue;
                open(c[0], c[1], d[0], d[1]);
                seen[nx][ny] = true;
                stack.push(new int[]{nx, ny});
                moved = true;
            }
            if (!moved) stack.pop();
        }
        for (int i = 0; i < extraOpenings; i++) {
            int[] d = dirs[r.nextInt(4)];
            int x = r.nextInt(w), y = r.nextInt(h);
            int nx = x + d[0], ny = y + d[1];
            if (!wrap && (nx < 0 || ny < 0 || nx >= w || ny >= h)) continue;
            open(x, y, d[0], d[1]);
        }
    }

    private void open(int x, int y, int dx, int dy) {
        if (dx == 1) east[x][y] = true;
        else if (dx == -1) east[Math.floorMod(x - 1, w)][y] = true;
        else if (dy == 1) south[x][y] = true;
        else south[x][Math.floorMod(y - 1, h)] = true;
    }

    public boolean east(int x, int y) { return east[Math.floorMod(x, w)][Math.floorMod(y, h)]; }

    public boolean south(int x, int y) { return south[Math.floorMod(x, w)][Math.floorMod(y, h)]; }
}
