package at.robin.metro.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Run with plain Java as well as JUnit: the placement engine has no Android dependencies. */
public final class EngineChecks {
    private static int assertions;
    private static void require(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static Tile tile(String id, Tile.Size size, int column, int row) {
        Tile tile = new Tile(id, "test/" + id); tile.size = size; tile.column = column; tile.row = row; return tile;
    }
    private static void valid(List<Tile> tiles, int columns, int expected) {
        require(tiles.size() == expected, "A tile was lost or duplicated");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i); require(ids.add(tile.id), "Duplicate tile ID");
            require(tile.column >= 0 && tile.column + tile.size.width <= columns && tile.row >= 0, "Tile outside grid");
            for (int j = i + 1; j < tiles.size(); j++) require(!tile.overlaps(tiles.get(j)), "Tiles overlap");
        }
    }
    public static void runAll() {
        List<Tile> tiles = new ArrayList<>();
        Tile first = tile("a", Tile.Size.MEDIUM, 0, 0), second = tile("b", Tile.Size.MEDIUM, 2, 0);
        tiles.add(first); tiles.add(second);
        TileLayout.resize(tiles, first, Tile.Size.WIDE, 4); valid(tiles, 4, 2);
        require(first.row == 0 && first.column == 0, "Resized tile must keep its requested anchor");
        require(second.row >= 2, "Collision must displace the other tile");
        TileLayout.move(tiles, second, 2, 20, 4); require(second.row == 20, "Intentional empty rows must remain");
        TileLayout.move(tiles, first, 99, -5, 4); valid(tiles, 4, 2);
        require(first.column == 0 && first.row == 0, "Moves must clamp coordinates");
        TileLayout.reflow(tiles, 6); valid(tiles, 6, 2);
        TileLayout.reflow(tiles, 4); valid(tiles, 4, 2);

        List<Tile> imported = new ArrayList<>();
        for (int i = 0; i < 30; i++) imported.add(tile("import" + i, Tile.Size.values()[i % 3], 100, -10));
        TileLayout.normalize(imported, 4); valid(imported, 4, 30);

        Tile folder = new Tile("@folder"); folder.label = "work"; folder.children.add(first.copy());
        Tile copy = folder.copy(); copy.children.get(0).row = 100;
        require(first.row == 0 && folder.children.get(0).row == 0, "Folder copies must be independent");
        require(copy.isFolder() && copy.children.size() == 1, "Folder metadata must survive copying");

        require(AppSearch.matches("ÖBB Scotty", "obb"), "Search must ignore diacritics");
        require(AppSearch.matches("Straße", "strasse"), "Search must handle Austrian/German sharp s");
        require(AppSearch.matches("Google Maps", " MAPS "), "Search must ignore case and outer spaces");
        require(!AppSearch.matches("Camera", "mail"), "Search cannot match unrelated apps");
        require("O".equals(AppSearch.section("ÖBB")), "Accented letters must group with the alphabet");
        require("#".equals(AppSearch.section("123 Player")), "Numbers must group under #");
        require("#".equals(AppSearch.section("")), "Empty labels cannot crash indexing");
        require("東".equals(AppSearch.section("東京")), "Non-Latin sections must remain reachable");

        Random random = new Random(810081L);
        for (int seed = 0; seed < 12; seed++) {
            List<Tile> board = new ArrayList<>(); int columns = 4;
            for (int i = 0; i < 40; i++) TileLayout.append(board, tile(seed + ":" + i, Tile.Size.values()[random.nextInt(3)], 0, 0), columns);
            valid(board, columns, 40);
            for (int operation = 0; operation < 300; operation++) {
                Tile chosen = board.get(random.nextInt(board.size()));
                switch (random.nextInt(4)) {
                    case 0: TileLayout.move(board, chosen, random.nextInt(10) - 2, random.nextInt(45) - 2, columns); break;
                    case 1: TileLayout.resize(board, chosen, Tile.Size.values()[random.nextInt(3)], columns); break;
                    case 2: columns = columns == 4 ? 6 : 4; TileLayout.reflow(board, columns); break;
                    default:
                        board.remove(chosen); TileLayout.append(board, chosen, columns); break;
                }
                valid(board, columns, 40);
            }
        }
        boolean rejects = false;
        try { TileLayout.reflow(tiles, 5); } catch (IllegalArgumentException expected) { rejects = true; }
        require(rejects, "Unsupported column counts must fail explicitly");
        System.out.println("PASS: " + assertions + " assertions; 3,600 randomized layout operations; search and folder checks.");
    }
    public static void main(String[] args) { runAll(); }
}
