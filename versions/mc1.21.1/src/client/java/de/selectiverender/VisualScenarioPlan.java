package de.selectiverender;
/** Fixed, shared visual cases. Mapping-specific adapters live in VisualRegressionSmoke. */
final class VisualScenarioPlan {
    record Case(String id, String description, SelectiveRenderSettings.BoundaryMode mode, int opacity, boolean rainbow, boolean ui) { }
    static final Case[] CASES = {
        new Case("01-normal", "Original stone cut face", SelectiveRenderSettings.BoundaryMode.NORMAL,100,false,false),
        new Case("02-color-0", "Normal + Color, opacity 0%", SelectiveRenderSettings.BoundaryMode.COLOR,0,false,false),
        new Case("03-color-25", "Normal + Color, opacity 25%", SelectiveRenderSettings.BoundaryMode.COLOR,25,false,false),
        new Case("04-color-50", "Normal + Color, opacity 50%", SelectiveRenderSettings.BoundaryMode.COLOR,50,false,false),
        new Case("05-color-75", "Normal + Color, opacity 75%", SelectiveRenderSettings.BoundaryMode.COLOR,75,false,false),
        new Case("06-color-100", "Opaque red cut face; grass decoration retains its own material", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,false),
        new Case("07-texture", "Diamond-block texture on cut faces", SelectiveRenderSettings.BoundaryMode.TEXTURE,100,false,false),
        new Case("08-texture-color", "Texture + Color, opacity 50%", SelectiveRenderSettings.BoundaryMode.COLOR_TEXTURE,50,false,false),
        new Case("09-rainbow", "Global Rainbow across the cut face", SelectiveRenderSettings.BoundaryMode.COLOR,100,true,false),
        new Case("10-culled", "Cut faces removed", SelectiveRenderSettings.BoundaryMode.CULLED,100,false,false),
        new Case("11-only-stone", "Only stone: grass, dirt and glass excluded", SelectiveRenderSettings.BoundaryMode.NORMAL,100,false,false),
        new Case("12-region-split", "Blue regional override on the left; global red on the right", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,false),
        new Case("13-ui-global", "Global palette: all four sliders and preview", SelectiveRenderSettings.BoundaryMode.COLOR,50,false,true),
        new Case("14-ui-rainbow", "Global Rainbow preset and preview", SelectiveRenderSettings.BoundaryMode.COLOR,100,true,true),
        new Case("15-ui-color-off", "Color Off: no color sliders or preview", SelectiveRenderSettings.BoundaryMode.NORMAL,100,false,true),
        new Case("16-ui-region-color", "Regional blue palette and independent opacity", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,true),
        new Case("17-ui-region-off", "Regional Color Off: palette collapsed", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,true),
        new Case("18-ui-reset-before", "Reset beside Delete; one filter and custom region options", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,true),
        new Case("19-ui-reset-confirm", "First click only arms Confirm reset", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,true),
        new Case("20-ui-reset-done", "Second click clears filters and restores inheritance", SelectiveRenderSettings.BoundaryMode.COLOR,100,false,true),
        new Case("21-ui-inherit", "Regional palette follows global values again", SelectiveRenderSettings.BoundaryMode.COLOR,50,false,true)
    };
    private VisualScenarioPlan() { }
}
