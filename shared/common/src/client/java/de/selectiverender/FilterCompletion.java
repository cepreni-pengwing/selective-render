package de.selectiverender;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class FilterCompletion {
    private FilterCompletion() { }

    private record Match(String value, int rank) { }

    static List<String> matches(String input, List<String> selectors) {
        int comma = input.lastIndexOf(',');
        String prefix = comma < 0 ? "" : input.substring(0, comma + 1);
        String tail = input.substring(comma + 1);
        String spacing = tail.substring(0, tail.length() - tail.stripLeading().length());
        String query = tail.strip().toLowerCase(Locale.ROOT);
        return selectors.stream().map(value -> {
            if (query.startsWith("tag:")) return value.startsWith("#") ? "tag:" + value.substring(1) : null;
            if (query.startsWith("id:")) return value.startsWith("#") ? null : "id:" + value;
            if (query.startsWith("#")) return value.startsWith("#") ? value : null;
            return value;
        }).filter(Objects::nonNull).distinct().map(value -> new Match(value, rank(query, value)))
                .filter(match -> match.rank() >= 0)
                .sorted(Comparator.comparingInt(Match::rank)
                        .thenComparing(match -> match.value().startsWith("#") || match.value().startsWith("tag:"))
                        .thenComparing(Match::value))
                .map(match -> prefix + spacing + match.value()).toList();
    }

    static int rank(String query, String value) {
        if (value.startsWith(query)) return 0;
        String bare = strip(value);
        String typed = strip(query);
        if (typed.isEmpty()) return -1;
        if (typed.contains(":")) return bare.contains(typed) ? 3 : -1;
        String path = bare.substring(bare.indexOf(':') + 1);
        String[] words = typed.split("[\\s_]+");
        if (path.startsWith(String.join("_", words))) return 1;
        int rank = 2;
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (wordStart(bare, word)) continue;
            if (!bare.contains(word)) return -1;
            rank = 3;
        }
        return rank;
    }

    private static String strip(String value) {
        if (value.startsWith("tag:")) return value.substring(4);
        if (value.startsWith("id:")) return value.substring(3);
        if (value.startsWith("#")) return value.substring(1);
        return value;
    }

    private static boolean wordStart(String value, String word) {
        for (int index = value.indexOf(word); index >= 0; index = value.indexOf(word, index + 1)) {
            if (index == 0 || "_:/.-".indexOf(value.charAt(index - 1)) >= 0) return true;
        }
        return false;
    }
}
