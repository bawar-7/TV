package dev.bawar.tv.launcher;

/** Grid index navigation. -1 means move from the dock to the shortcut bar. */
final class GridNavigation {
    enum Direction { LEFT, RIGHT, UP, DOWN }
    static int move(int selected, int count, int columns, Direction direction) {
        if (columns < 1 || count < 1 || selected < 0 || selected >= count)
            throw new IllegalArgumentException("Invalid grid position");
        int row = selected / columns, col = selected % columns;
        switch (direction) {
            case LEFT: return col > 0 ? selected - 1 : selected;
            case RIGHT: return col < columns - 1 && selected + 1 < count ? selected + 1 : selected;
            case UP: return row == 0 ? -1 : selected - columns;
            case DOWN: return (row + 1) * columns < count ? Math.min(selected + columns, count - 1) : selected;
            default: return selected;
        }
    }
}
