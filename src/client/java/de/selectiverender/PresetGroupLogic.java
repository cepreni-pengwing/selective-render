package de.selectiverender;

import java.util.Collection;
import java.util.Set;

final class PresetGroupLogic {
    private PresetGroupLogic() {
    }

    static boolean toggleAll(Set<String> active, Collection<String> available) {
        boolean disabling = !active.isEmpty();
        if (disabling) active.clear();
        else active.addAll(available);
        return disabling;
    }

    static void replaceMembership(Set<String> group, String oldName, String newName) {
        if (group.remove(oldName)) group.add(newName);
    }
}
