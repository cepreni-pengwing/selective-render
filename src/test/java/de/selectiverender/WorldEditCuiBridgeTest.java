package de.selectiverender;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditCuiBridgeTest {
    public record LegacyPacket(String text) { }
    public record SharedPacket(boolean multi, String eventType, List<String> args) { }

    @Test void readsWorldEditLegacyPayloadWithoutMappedMinecraftMembers() throws Exception {
        assertEquals("s|cuboid", WorldEditCuiBridge.packetText(new LegacyPacket("s|cuboid")));
    }

    @Test void readsSharedProtocolPayload() throws Exception {
        assertEquals("p|0|1|2|3|1", WorldEditCuiBridge.packetText(
                new SharedPacket(false, "p", List.of("0", "1", "2", "3", "1"))));
    }

    @Test void skipsSharedMultiSelectionOverlays() throws Exception {
        assertEquals("+", WorldEditCuiBridge.packetText(new SharedPacket(true, "s", List.of("cuboid"))));
    }

    @Test void unknownPayloadDoesNotPretendToBeASelection() {
        assertThrows(ReflectiveOperationException.class, () -> WorldEditCuiBridge.packetText(new Object()));
    }
}
