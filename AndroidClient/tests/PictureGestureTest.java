package io.github.n2ray.sill;

public class PictureGestureTest {
    public static void main(String[] args) {
        PictureGesture g = new PictureGesture();
        assert g.dispatch(PictureGesture.Action.DOWN) == PictureGesture.Dispatch.TRACKPAD;
        assert g.dispatch(PictureGesture.Action.OTHER) == PictureGesture.Dispatch.TRACKPAD;
        assert g.dispatch(PictureGesture.Action.POINTER_DOWN) == PictureGesture.Dispatch.CANCEL_TRACKPAD;
        assert g.dispatch(PictureGesture.Action.OTHER) == PictureGesture.Dispatch.ZOOM;
        assert g.dispatch(PictureGesture.Action.POINTER_DOWN) == PictureGesture.Dispatch.ZOOM;
        assert g.dispatch(PictureGesture.Action.OTHER) == PictureGesture.Dispatch.ZOOM;
        assert g.dispatch(PictureGesture.Action.DOWN) == PictureGesture.Dispatch.TRACKPAD;
        assert g.dispatch(PictureGesture.Action.OTHER) == PictureGesture.Dispatch.TRACKPAD;
        System.out.println("Picture gesture checks passed");
    }
}
