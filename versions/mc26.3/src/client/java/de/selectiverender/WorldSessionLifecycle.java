package de.selectiverender;

import java.util.function.Consumer;

/** Makes setWorld/JOIN/DISCONNECT notifications idempotent by world identity. */
final class WorldSessionLifecycle<W> {
    private W current;

    void switchTo(W next, Runnable leave, Consumer<W> enter) {
        if (current == next) return;
        if (current != null) leave.run();
        current = next;
        if (next != null) enter.accept(next);
    }
}
