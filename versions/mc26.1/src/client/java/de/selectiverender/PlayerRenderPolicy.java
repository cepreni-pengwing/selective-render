package de.selectiverender;

/** Player identity and spatial visibility are independent, combined with AND. */
public final class PlayerRenderPolicy {
    private PlayerRenderPolicy() { }
    public enum Group { EVERYONE, NOBODY, SELF, OTHERS }
    public enum Scope { EVERYWHERE, INSIDE, OUTSIDE, NOWHERE }
    public record Settings(Group group, Scope scope) { }
    public static Settings migrate(SelectiveRenderSettings.PlayerVisibility value) {
        if (value == null) value = SelectiveRenderSettings.PlayerVisibility.EVERYWHERE;
        return switch (value) {
            case NONE -> new Settings(Group.NOBODY, Scope.EVERYWHERE);
            case OWN_ONLY -> new Settings(Group.SELF, Scope.EVERYWHERE);
            case EXCEPT_OWN -> new Settings(Group.OTHERS, Scope.EVERYWHERE);
            case INSIDE -> new Settings(Group.EVERYONE, Scope.INSIDE);
            case OUTSIDE -> new Settings(Group.EVERYONE, Scope.OUTSIDE);
            default -> new Settings(Group.EVERYONE, Scope.EVERYWHERE);
        };
    }
    public static boolean allows(Group group, Scope scope, boolean own, boolean inside) {
        boolean identity = switch (group) { case EVERYONE -> true; case NOBODY -> false; case SELF -> own; case OTHERS -> !own; };
        return identity && switch (scope) { case EVERYWHERE -> true; case INSIDE -> inside; case OUTSIDE -> !inside; case NOWHERE -> false; };
    }
}
