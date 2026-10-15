package de.selectiverender;

import java.util.List;

/** Menu completion uses the same registry/tag selectors as the command suggestions. */
final class FilterCompletion {
    private FilterCompletion() { }

    static List<String> matches(String input, List<String> selectors) {
        int comma = input.lastIndexOf(',');
        String prefix = comma < 0 ? "" : input.substring(0, comma + 1);
        String tail = input.substring(comma + 1);
        String spacing = tail.substring(0, tail.length() - tail.stripLeading().length());
        String query = tail.stripLeading().toLowerCase(java.util.Locale.ROOT);
        return selectors.stream().map(value -> {
            if (query.startsWith("tag:")) return value.startsWith("#") ? "tag:" + value.substring(1) : null;
            if (query.startsWith("id:")) return value.startsWith("#") ? null : "id:" + value;
            return value;
        }).filter(java.util.Objects::nonNull).filter(value -> value.startsWith(query)
                || (!query.contains(":") && !query.startsWith("#") && !value.startsWith("#")
                    && value.substring(value.indexOf(':') + 1).startsWith(query)))
                .distinct().sorted().map(value -> prefix + spacing + value).toList();
    }
}
