package io.github.n2ray.sill;

public final class PhoneStreamLayoutTest {
    public static void main(String[] args) {
        assert PhoneStreamLayout.pictureHeight(400, 850, 200, 120) == 250;
        assert PhoneStreamLayout.pictureHeight(320, 500, 200, 120) == 180;
        assert PhoneStreamLayout.pictureHeight(320, 200, 200, 120) == 0;
        assert PhoneStreamLayout.pictureHeight(0, 600, 200, 120) == 0;

        PhoneStreamLayout normal = new PhoneStreamLayout(440, 894, null);
        assert normal.picture.equals(new PhoneStreamLayout.Rect(8, 8, 424, 265));
        assert normal.strip.height() == 62;
        assert normal.pane(1000, 2000).equals(normal.picture);
        PhoneStreamLayout large = PhoneStreamLayout.forSource(440, 894, 320.0, false, true, PhoneStreamLayout.Streaming.WINDOW);
        assert large.largerPicture;
        assert large.picture.equals(new PhoneStreamLayout.Rect(8, 8, 424, 422));
        assert large.row1.y() == 454 && large.keys.y() == 520 && large.keys.bottom() == 564;
        assert large.strip.height() == 0 && large.trackpad.equals(new PhoneStreamLayout.Rect(14, 574, 412, 304));
        assert large.pane(0, 0).equals(new PhoneStreamLayout.Rect(8, 8, 424, 265));
        assert large.pane(1000, 1000).equals(new PhoneStreamLayout.Rect(9, 8, 422, 422));
        assert large.pane(1000, 2000).equals(new PhoneStreamLayout.Rect(114, 8, 211, 422));
        assert large.trackpadSpan(1000, 2000) == 824;
        assert large.picture.height() != large.pane(0, 0).height(); // viewport stays the full room
        assert !PhoneStreamLayout.offersLargerPicture(320, 700, 320, false);
        assert !PhoneStreamLayout.offersLargerPicture(331, 900, 320, false);
        assert PhoneStreamLayout.offersLargerPicture(332, 900, 320, false);
        assert !PhoneStreamLayout.offersLargerPicture(400, 710, 320, false);
        assert PhoneStreamLayout.offersLargerPicture(400, 713, 320, false);
        assert PhoneStreamLayout.offersLargerPicture(713, 400, 320, false);
        assert !PhoneStreamLayout.offersLargerPicture(710, 400, 320, false);
        assert !PhoneStreamLayout.forSource(894, 440, 320.0, false, true, PhoneStreamLayout.Streaming.WINDOW).largerPicture;
        assert !PhoneStreamLayout.offersLargerPicture(440, 894, 0, false);
        assert !PhoneStreamLayout.forSource(440, 894, 320.0, false, true, PhoneStreamLayout.Streaming.DESKTOP).largerPicture;
        assert !PhoneStreamLayout.forSource(440, 894, 320.0, false, true, PhoneStreamLayout.Streaming.NOTHING).largerPicture;
        assert !PhoneStreamLayout.forSource(440, 894, null, false, true, PhoneStreamLayout.Streaming.WINDOW).largerPicture;
        assert !PhoneStreamLayout.forSource(440, 894, 320.0, true, true, PhoneStreamLayout.Streaming.WINDOW).largerPicture;
        assert !PhoneStreamLayout.forSource(440, 894, 320.0, false, false, PhoneStreamLayout.Streaming.WINDOW).largerPicture;
        System.out.println("Phone stream layout checks passed");
    }
}
