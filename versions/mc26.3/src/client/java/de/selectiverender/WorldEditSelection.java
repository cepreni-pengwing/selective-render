package de.selectiverender;

import java.util.Arrays;
import java.util.List;

/** Client-thread snapshot of the primary WorldEdit CUI selection, never render geometry. */
public final class WorldEditSelection {
    private String shape;
    private int[] first;
    private int[] second;

    public void clear() {
        shape = null;
        first = second = null;
    }

    public void accept(String message) {
        if (message == null || message.length() > 4096) {
            clear();
            return;
        }
        String[] fields = message.split("\\|", -1);
        if (fields[0].startsWith("+")) return; // Other CUI overlays are not the player's selection.
        accept(fields[0], Arrays.asList(fields).subList(1, fields.length));
    }

    public void accept(String type, List<String> args) {
        try {
            if ("s".equals(type)) {
                clear();
                if (args.size() == 1) shape = args.get(0);
            } else if ("p".equals(type) && "cuboid".equals(shape)) {
                if (args.size() != 5) throw new IllegalArgumentException("Invalid point");
                int id = Integer.parseInt(args.get(0));
                if (id != 0 && id != 1) throw new IllegalArgumentException("Invalid point index");
                int[] point = {coordinate(args.get(1)), coordinate(args.get(2)), coordinate(args.get(3))};
                // An incomplete selector can retain the other old corner on some servers.
                if (Long.parseLong(args.get(4)) < 0) first = second = null;
                if (id == 0) first = point;
                else second = point;
            }
        } catch (IllegalArgumentException exception) {
            clear(); // Never import stale bounds after a malformed update.
        }
    }

    private static int coordinate(String value) {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid block coordinate");
        }
        return (int) number;
    }

    public BlockRegion region() {
        if (!"cuboid".equals(shape) || first == null || second == null) return null;
        return new BlockRegion(Math.min(first[0], second[0]), Math.max(first[0], second[0]),
                Math.min(first[1], second[1]), Math.max(first[1], second[1]),
                Math.min(first[2], second[2]), Math.max(first[2], second[2]));
    }

    public String problem() {
        if (shape != null && !"cuboid".equals(shape)) return "Only cuboid WorldEdit selections are supported. Use //sel cuboid.";
        return "No complete WorldEdit selection received. Select both corners; if needed, run /we cui and try again.";
    }
}
