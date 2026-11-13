package io.github.n2ray.sill;

/** iOS tour placement on an unfolded phone; coordinates share one screen space. */
final class TourPlacement {
    record Rect(float left, float top, float right, float bottom) {
        float width() { return Math.max(0, right - left); }
        float height() { return Math.max(0, bottom - top); }
        float midX() { return (left + right) / 2; }
        float midY() { return (top + bottom) / 2; }
        boolean intersects(Rect r) { return left < r.right && right > r.left && top < r.bottom && bottom > r.top; }
    }
    record Result(Rect card, int tail, float tipX, boolean coversTargets) { }
    static float width(float screenWidth, float screenHeight, boolean landscape, boolean accessibility, float unit) {
        float preferred = accessibility ? 560 : landscape && screenHeight < 520 * unit ? 480 : 360;
        return Math.max(0, Math.min(preferred * unit, screenWidth - 32 * unit));
    }
    static Rect cutout(Rect target, float screenWidth, float screenHeight, float unit) {
        if (target == null || target.width() <= 0 || target.height() <= 0) return null;
        Rect r = new Rect(Math.max(0, target.left - 4 * unit), Math.max(0, target.top - 4 * unit),
                Math.min(screenWidth, target.right + 4 * unit), Math.min(screenHeight, target.bottom + 4 * unit));
        return r.width() <= 0 || r.height() <= 0 ? null : r;
    }
    static Result place(float screenWidth, float screenHeight, float cardWidth, float wantedHeight,
                        boolean landscape, boolean streamStep, Rect stream, Rect room, Rect target, float unit) {
        float margin = 16 * unit, gap = 12 * unit, minRoom = 200 * unit;
        float top = margin, bottom = Math.max(top, screenHeight - margin);
        Rect half = room != null ? room : stream;
        float pictureBottom = Math.min(bottom, Math.max(top, half.bottom - gap));
        float center = !streamStep && target != null ? target.midX() : stream.midX();
        float x = Math.min(Math.max(center - cardWidth / 2, margin), Math.max(margin, screenWidth - margin - cardWidth));
        float tip = cardWidth < 56 * unit ? x + cardWidth / 2 : Math.min(Math.max(center, x + 28 * unit), x + cardWidth - 28 * unit);
        float y, h, below, space;
        int tail = 0;
        if (streamStep || target == null) {
            float lowest = landscape ? bottom : pictureBottom;
            if (lowest - wantedHeight >= top) {
                y = Math.min(Math.max(stream.midY() - wantedHeight / 2, top), lowest - wantedHeight); h = wantedHeight;
            } else { y = top; h = Math.min(wantedHeight, bottom - top); }
        } else if (landscape) {
            below = target.bottom + gap; space = bottom - below;
            if (wantedHeight <= space || space >= minRoom) { y = below; h = Math.min(wantedHeight, space); tail = -1; }
            else { y = Math.max(top, bottom - wantedHeight); h = Math.min(wantedHeight, bottom - y); }
        } else if (pictureBottom - wantedHeight >= top) {
            y = pictureBottom - wantedHeight; h = wantedHeight;
            if (target.top >= y + h && target.top - (y + h) <= 48 * unit) tail = 1;
        } else {
            float above = Math.min(bottom, Math.max(top, target.top - gap));
            below = target.bottom + gap; space = bottom - below;
            if (wantedHeight > above - top && (wantedHeight <= space || (space > above - top && space >= minRoom))) {
                y = below; h = Math.min(wantedHeight, space); tail = -1;
            } else if (wantedHeight <= above - top || above - top >= minRoom) {
                y = Math.max(top, above - wantedHeight); h = Math.min(wantedHeight, above - top);
                if (target.top >= y + h && target.top - (y + h) <= 48 * unit) tail = 1;
            } else { y = top; h = Math.min(wantedHeight, bottom - top); }
        }
        Rect card = new Rect(x, y, x + cardWidth, y + Math.max(0, h));
        Rect lit = cutout(target, screenWidth, screenHeight, unit);
        boolean covers = !streamStep && tail == 0 && lit != null && card.intersects(lit);
        return new Result(card, tail, tip, covers);
    }
}
