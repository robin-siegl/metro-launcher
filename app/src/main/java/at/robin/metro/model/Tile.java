package at.robin.metro.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Coordinates use the small tile as one cell. Wide means 4 x 2 cells. */
public final class Tile {
    public enum Size {
        SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2);
        public final int width, height;
        Size(int width, int height) { this.width = width; this.height = height; }
        public Size next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public final String id;
    public String app;
    public String label = "";
    public Size size = Size.MEDIUM;
    public int column, row;
    public int color; // 0 uses the global accent.
    public boolean photo = true;
    public boolean live = true;
    public final List<Tile> children = new ArrayList<>();

    public Tile(String app) { this(UUID.randomUUID().toString(), app); }
    public Tile(String id, String app) { this.id = id; this.app = app; }
    public boolean isFolder() { return "@folder".equals(app); }
    public boolean overlaps(Tile other) {
        return column < other.column + other.size.width && column + size.width > other.column
            && row < other.row + other.size.height && row + size.height > other.row;
    }
    public Tile copy() {
        Tile tile = new Tile(id, app);
        tile.label = label; tile.size = size; tile.column = column; tile.row = row;
        tile.color = color; tile.photo = photo; tile.live = live;
        for (Tile child : children) tile.children.add(child.copy());
        return tile;
    }
}
