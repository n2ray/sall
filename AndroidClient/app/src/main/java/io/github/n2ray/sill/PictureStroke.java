package io.github.n2ray.sill;

/** Gesture ownership stays fixed until all fingers lift, as in iOS StreamZoom.Stroke. */
final class PictureStroke {
    enum Owner { UNDECIDED, MAC, PICTURE, GESTURE }
    private Owner owner = Owner.UNDECIDED;
    private boolean multiple;

    void begin() { owner = Owner.UNDECIDED; multiple = false; }
    void second(boolean alreadyZoomed) { multiple = true; if (owner == Owner.UNDECIDED && alreadyZoomed) owner = Owner.PICTURE; }
    void third() { multiple = true; owner = Owner.GESTURE; }
    void singleMove(float distance, float slop) {
        if (owner == Owner.UNDECIDED && distance >= slop) owner = Owner.MAC;
    }
    void pairMove(float spanChange, float midpointTravel, float slop, float pinchThreshold) {
        if (owner != Owner.UNDECIDED) return;
        if (spanChange >= pinchThreshold && spanChange > midpointTravel) owner = Owner.PICTURE;
        else if (midpointTravel >= slop)
            owner = spanChange > midpointTravel ? Owner.PICTURE : Owner.MAC;
    }
    Owner owner() { return owner; }
    boolean mayClick() { return owner == Owner.UNDECIDED && !multiple; }
}
