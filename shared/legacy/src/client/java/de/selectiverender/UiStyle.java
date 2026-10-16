package de.selectiverender;

import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

/** Shared colors for command feedback and settings labels. */
public final class UiStyle {
    public static final int TEXT = 0xE2E8F0;
    public static final int MUTED = 0x94A3B8;
    public static final int ACCENT = 0x36D9C5;
    public static final int SUCCESS = 0x55DB80;
    public static final int ERROR = 0xFF6978;

    private UiStyle() { }

    public static MutableText label(String value) {
        int split = value.indexOf(": ");
        if (split < 0) return Text.literal(value).styled(style -> style.withColor(TEXT));
        return Text.literal(value.substring(0, split + 2)).styled(style -> style.withColor(TEXT))
                .append(Text.literal(value.substring(split + 2)).styled(style -> style.withColor(ACCENT)));
    }
}
