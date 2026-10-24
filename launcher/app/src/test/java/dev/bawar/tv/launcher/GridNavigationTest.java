package dev.bawar.tv.launcher;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static dev.bawar.tv.launcher.GridNavigation.Direction.*;

public class GridNavigationTest {
    @Test public void horizontalMovementNeverWrapsRows() {
        assertEquals(3, GridNavigation.move(3, 11, 4, RIGHT));
        assertEquals(4, GridNavigation.move(4, 11, 4, LEFT));
        assertEquals(10, GridNavigation.move(10, 11, 4, RIGHT));
    }
    @Test public void verticalMovementKeepsColumnInFullRows() {
        assertEquals(7, GridNavigation.move(3, 12, 4, DOWN));
        assertEquals(3, GridNavigation.move(7, 12, 4, UP));
        assertEquals(11, GridNavigation.move(7, 12, 4, DOWN));
    }
    @Test public void shortLastRowClampsWithoutSkippingRow() {
        assertEquals(9, GridNavigation.move(7, 10, 4, DOWN));
        assertEquals(5, GridNavigation.move(9, 10, 4, UP));
        assertEquals(9, GridNavigation.move(9, 10, 4, DOWN));
    }
    @Test public void dockUpEntersToolbar() {
        for (int i = 0; i < 4; i++) assertEquals(-1, GridNavigation.move(i, 12, 4, UP));
    }
    @Test public void supportedColumnCountsStayWithinBounds() {
        for (int cols = 4; cols <= 7; cols++)
            for (int count = cols; count < 50; count++)
                for (int pos = 0; pos < count; pos++)
                    for (GridNavigation.Direction dir : GridNavigation.Direction.values()) {
                        int next = GridNavigation.move(pos, count, cols, dir);
                        org.junit.Assert.assertTrue(next >= -1 && next < count);
                        if (next >= 0) org.junit.Assert.assertTrue(Math.abs(next / cols - pos / cols) <= 1);
                    }
    }
    @Test(expected = IllegalArgumentException.class) public void invalidSelectionIsRejected() {
        GridNavigation.move(12, 12, 4, DOWN);
    }
}
