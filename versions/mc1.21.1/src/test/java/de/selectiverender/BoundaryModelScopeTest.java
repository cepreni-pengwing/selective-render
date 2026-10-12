package de.selectiverender;
import me.pepperbell.continuity.client.model.ModelObjectsContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoundaryModelScopeTest {
    @Test void isolatesActiveTransformAndRestoresTheOuterContext() {
        var outer=ModelObjectsContainer.THREAD_LOCAL.get(); outer.ctmQuadTransform.active=true;
        try (var scope=BoundaryModelScope.open()) {
            assertTrue(scope.nested()); assertNotSame(outer,ModelObjectsContainer.THREAD_LOCAL.get());
            assertFalse(ModelObjectsContainer.THREAD_LOCAL.get().ctmQuadTransform.active); scope.complete();
        }
        assertSame(outer,ModelObjectsContainer.THREAD_LOCAL.get()); assertTrue(outer.ctmQuadTransform.active);
        outer.ctmQuadTransform.active=false;
    }
    @Test void failureRestoresOuterContextAndDiscardsDamagedScratch() {
        var outer=ModelObjectsContainer.THREAD_LOCAL.get(); ModelObjectsContainer scratch;
        try (var scope=BoundaryModelScope.open()) { scratch=ModelObjectsContainer.THREAD_LOCAL.get(); scratch.ctmQuadTransform.active=true; }
        assertSame(outer,ModelObjectsContainer.THREAD_LOCAL.get());
        try (var scope=BoundaryModelScope.open()) {
            assertNotSame(scratch,ModelObjectsContainer.THREAD_LOCAL.get());
            assertFalse(ModelObjectsContainer.THREAD_LOCAL.get().ctmQuadTransform.active); scope.complete();
        }
    }
    @Test void repeatedAndDisabledFeaturesDoNotAccumulateEnableCounts() {
        var outer=ModelObjectsContainer.THREAD_LOCAL.get();
        try {
            for (int i=0;i<100;i++) {
                outer.featureStates.connected.count=i%2;
                try (var scope=BoundaryModelScope.open()) {
                    var scratch=ModelObjectsContainer.THREAD_LOCAL.get();
                    assertEquals(i%2,scratch.featureStates.connected.count); scope.complete();
                }
                assertEquals(i%2,outer.featureStates.connected.count);
            }
        } finally { outer.featureStates.connected.count=1; }
    }
}
