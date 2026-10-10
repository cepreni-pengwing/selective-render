package de.selectiverender;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Shared colors for command feedback and settings labels. */
public final class UiStyle {
    public static final int TEXT = 0xE2E8F0;
    public static final int MUTED = 0x94A3B8;
    public static final int ACCENT = 0x67E8D4;
    public static final int SUCCESS = 0x86EFAC;
    public static final int ERROR = 0xFDA4AF;

    private UiStyle() { }

    public static MutableComponent label(String value) {
        int split = value.indexOf(": ");
        if (split < 0) return Component.literal(value).withStyle(style -> style.withColor(TEXT));
        return Component.literal(value.substring(0, split + 2)).withStyle(style -> style.withColor(TEXT))
                .append(Component.literal(value.substring(split + 2)).withStyle(style -> style.withColor(ACCENT)));
    }
}
