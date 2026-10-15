package de.selectiverender;

/** Packs measured option widths without overlaps; oversized values retain a tooltip. */
final class OptionRowLayout {
    private OptionRowLayout() { }

    static int end(int available, int[] widths, int start) {
        int used = 0;
        int end = start;
        while (end < widths.length) {
            int next = Math.min(available, Math.max(36, widths[end] + 16));
            if (end > start && used + 6 + next > available) break;
            used += (end == start ? 0 : 6) + next;
            end++;
        }
        return end;
    }
}
