package de.selectiverender;

public final class VirtualLightPropagation {
    private VirtualLightPropagation() { }


    /**
     * Enqueue only direct-light cells that can brighten a neighbor. All direct seeds must
     * already be populated. Omitted cells cannot become useful later because light only
     * increases; a subsequently brightened cell is enqueued by the normal propagation loop.
     * Every propagation edge attenuates by at least one, including downward edges.
     */
    public static int seedFrontier(byte[] light, byte[] queued, int[] queue,
                                   int sizeX, int sizeY, int sizeZ) {
        int plane = sizeX * sizeZ;
        int count = 0;
        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, index++) {
                    int value = Byte.toUnsignedInt(light[index]);
                    if (value <= 1) continue;
                    if ((x > 0 && canImprove(value, Byte.toUnsignedInt(light[index - 1])))
                            || (x + 1 < sizeX && canImprove(value, Byte.toUnsignedInt(light[index + 1])))
                            || (z > 0 && canImprove(value, Byte.toUnsignedInt(light[index - sizeX])))
                            || (z + 1 < sizeZ && canImprove(value, Byte.toUnsignedInt(light[index + sizeX])))
                            || (y > 0 && canImprove(value, Byte.toUnsignedInt(light[index - plane])))
                            || (y + 1 < sizeY && canImprove(value, Byte.toUnsignedInt(light[index + plane])))) {
                        queue[count++] = index;
                        queued[index] = 1;
                    }
                }
            }
        }
        return count;
    }

    public static boolean canImprove(int currentLight, int existingNeighborLight) {
        return currentLight > 1 && existingNeighborLight < currentLight - 1;
    }

    /** Shape checks can only increase attenuation, never reduce block opacity. */
    public static boolean canPass(int currentLight, int existingNeighborLight, int opacity) {
        return currentLight - Math.max(1, opacity) > existingNeighborLight;
    }
}
