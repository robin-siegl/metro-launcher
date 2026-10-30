package at.robin.metro.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic, platform-independent collision handling. Empty cells are intentional. */
public final class TileLayout {
    public static final int MAX_TILES = 512;
    public static final int MAX_ROW = 4096;
    private TileLayout() { }

    public static void normalize(List<Tile> tiles, int columns) {
        checkColumns(columns);
        List<Tile> placed = new ArrayList<>();
        for (Tile tile : tiles) {
            tile.column = Math.max(0, Math.min(tile.column, columns - tile.size.width));
            tile.row = Math.max(0, Math.min(tile.row, MAX_ROW));
            if (!free(tile, placed)) firstFree(tile, placed, columns, tile.row * columns);
            placed.add(tile);
        }
    }

    public static void append(List<Tile> tiles, Tile tile, int columns) {
        checkColumns(columns);
        if (tiles.size() >= MAX_TILES) throw new IllegalStateException("Too many tiles");
        firstFree(tile, tiles, columns, 0);
        tiles.add(tile);
    }

    public static void move(List<Tile> tiles, Tile tile, int column, int row, int columns) {
        checkColumns(columns);
        if (!tiles.contains(tile)) throw new IllegalArgumentException("Tile is not on this board");
        tile.column = Math.max(0, Math.min(column, columns - tile.size.width));
        tile.row = Math.max(0, Math.min(row, MAX_ROW));
        List<Tile> others = new ArrayList<>(tiles);
        others.remove(tile);
        others.sort(Comparator.comparingInt((Tile t) -> t.row).thenComparingInt(t -> t.column));
        List<Tile> placed = new ArrayList<>();
        placed.add(tile);
        for (Tile other : others) {
            if (!free(other, placed)) firstFree(other, placed, columns, other.row * columns + other.column);
            placed.add(other);
        }
    }

    public static void resize(List<Tile> tiles, Tile tile, Tile.Size size, int columns) {
        tile.size = size;
        move(tiles, tile, tile.column, tile.row, columns);
    }

    /** Retain ordering while making a denser or wider Start screen. */
    public static void reflow(List<Tile> tiles, int columns) {
        checkColumns(columns);
        tiles.sort(Comparator.comparingInt((Tile t) -> t.row).thenComparingInt(t -> t.column));
        List<Tile> placed = new ArrayList<>();
        for (Tile tile : tiles) {
            firstFree(tile, placed, columns, 0);
            placed.add(tile);
        }
    }

    public static int rows(List<Tile> tiles) {
        int result = 0;
        for (Tile tile : tiles) result = Math.max(result, tile.row + tile.size.height);
        return result;
    }

    public static boolean free(Tile candidate, List<Tile> placed) {
        for (Tile tile : placed) if (tile != candidate && candidate.overlaps(tile)) return false;
        return true;
    }

    private static void firstFree(Tile tile, List<Tile> placed, int columns, int start) {
        // At most 512 tiles: scanning beyond the last occupied row always finds room.
        int limit = Math.max(rows(placed) + 4, MAX_ROW + 4) * columns;
        for (int cell = Math.max(0, start); cell < limit; cell++) {
            tile.column = cell % columns; tile.row = cell / columns;
            if (tile.column + tile.size.width <= columns && free(tile, placed)) return;
        }
        tile.column = 0; tile.row = rows(placed);
    }
    private static void checkColumns(int columns) {
        if (columns != 4 && columns != 6) throw new IllegalArgumentException("Expected 4 or 6 columns");
    }
}
