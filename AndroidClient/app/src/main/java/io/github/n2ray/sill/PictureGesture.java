package io.github.n2ray.sill;

/** A second finger owns the whole picture stroke until every finger has lifted. */
final class PictureGesture {
    enum Action { DOWN, POINTER_DOWN, OTHER }
    enum Dispatch { TRACKPAD, CANCEL_TRACKPAD, ZOOM }
    private boolean zooming;

    Dispatch dispatch(Action action) {
        if (action == Action.DOWN) zooming = false;
        if (action == Action.POINTER_DOWN && !zooming) {
            zooming = true;
            return Dispatch.CANCEL_TRACKPAD;
        }
        return zooming ? Dispatch.ZOOM : Dispatch.TRACKPAD;
    }
}
