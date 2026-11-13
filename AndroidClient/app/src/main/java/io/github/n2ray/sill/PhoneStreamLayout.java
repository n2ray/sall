package io.github.n2ray.sill;

/** Portrait geometry in density-independent units, matching iOS PhonePortraitLayout. */
final class PhoneStreamLayout {
    record Rect(double x, double y, double width, double height) {
        double right() { return x + width; }
        double bottom() { return y + height; }
    }
    /** Complete physical row parameters: equal values must not request another layout. */
    record RowParams(int width, int height, float weight, int gravity,
                     int left, int top, int right, int bottom) {}
    enum Streaming { NOTHING, DESKTOP, WINDOW }
    record Ruler(Rect frame, double step) {}

    final boolean largerPicture;
    final Rect picture, row1, strip, menus, stripBesideMenus, keys, trackpad, drawer, settings;
    final Rect[] buttons, buttonsBesideMenus, caps, dim;
    final Ruler ruler, rulerBesideMenus;
    final double trackpadSpan;

    // Kept for the existing caller until the full geometry is connected to the UI.
    static int pictureHeight(int width, int height, int fixedHeight, int minTrackpad) {
        if (width <= 0 || height <= 0) return 0;
        int natural = (int) Math.floor(width / 1.6);
        return Math.min(natural, Math.max(0, height - Math.max(0, fixedHeight) - Math.max(0, minTrackpad)));
    }

    static boolean largerPicture(boolean enabled, Streaming streaming) {
        return enabled && streaming == Streaming.WINDOW;
    }

    /** An unknown keyboard height cannot safely reserve space above the IME. */
    static boolean offersLargerPicture(double width, double height, double keyboard, boolean foldable) {
        if (width <= 0 || height <= 0 || keyboard <= 0 || foldable) return false;
        double uprightWidth = Math.min(width, height), uprightHeight = Math.max(width, height);
        PhoneStreamLayout normal = new PhoneStreamLayout(uprightWidth, uprightHeight, null);
        PhoneStreamLayout larger = new PhoneStreamLayout(uprightWidth, uprightHeight, keyboard);
        return larger.picture.height > normal.picture.height && larger.menus.width >= 44;
    }

    static PhoneStreamLayout forSource(double width, double height, Double keyboard, boolean foldable,
                                       boolean enabled, Streaming streaming) {
        boolean use = width < height && keyboard != null && largerPicture(enabled, streaming)
                && offersLargerPicture(width, height, keyboard, foldable);
        return new PhoneStreamLayout(width, height, use ? keyboard : null);
    }

    PhoneStreamLayout(double width, double height, Double keyboard) {
        width = Math.max(0, width); height = Math.max(0, height);
        largerPicture = keyboard != null;
        double pictureWidth = Math.max(0, width - 16);
        double normalHeight = Math.min(Math.floor(pictureWidth / 1.6), Math.max(0, height - 240 - 120));
        double footprint = largerPicture ? Math.max(keyboard, 136) : 0;
        double pictureHeight = largerPicture ? Math.max(0, Math.floor(height - footprint - 152)) : normalHeight;
        picture = new Rect(8, 8, pictureWidth, pictureHeight);
        double rowWidth = Math.max(0, width - 28);
        row1 = new Rect(14, picture.bottom() + 24, rowWidth, 50);
        buttons = split(row1, 5);
        if (!largerPicture) {
            strip = new Rect(6, row1.bottom() + 16, Math.max(0, width - 12), 62);
            Rect last = buttons[4];
            menus = new Rect(last.x, strip.y + 6, last.width, 50);
            stripBesideMenus = new Rect(strip.x, strip.y, Math.max(0, menus.x - 8 - strip.x), strip.height);
            buttonsBesideMenus = buttons.clone();
        } else {
            strip = new Rect(6, row1.bottom() + 6, Math.max(0, width - 12), 0);
            stripBesideMenus = strip;
            Rect[] six = split(row1, 6);
            menus = six[1];
            buttonsBesideMenus = new Rect[] {six[0], six[2], six[3], six[4], six[5]};
        }
        keys = new Rect(14, strip.bottom() + 10, rowWidth, 44);
        caps = split(keys, 6);
        double padTop = largerPicture ? Math.max(keys.bottom() + 10, height - footprint) : keys.bottom() + 10;
        trackpad = new Rect(14, padTop, rowWidth, Math.max(0, height - 16 - padTop));
        trackpadSpan = trackpad.width / 1.6;
        ruler = ruler(buttons[1], row1);
        rulerBesideMenus = ruler(buttonsBesideMenus[1], row1);
        double panelTop = row1.bottom() + 8;
        settings = new Rect(14, panelTop, rowWidth, Math.max(0, height - 16 - panelTop));
        drawer = largerPicture ? new Rect(14, 8, rowWidth, Math.max(0, row1.y - 16)) : settings;
        dim = new Rect[] {new Rect(0, 0, width, row1.y),
                new Rect(0, row1.bottom(), width, Math.max(0, height - row1.bottom()))};
    }

    /** The host viewport is picture, even when the rendered pane fits inside it. */
    Rect pane(double videoWidth, double videoHeight) {
        if (!largerPicture || picture.width <= 0 || picture.height <= 0) return picture;
        double w = videoWidth > 0 && videoHeight > 0 ? videoWidth : 1.6;
        double h = videoWidth > 0 && videoHeight > 0 ? videoHeight : 1;
        double tall = picture.width * h / w;
        if (tall <= picture.height + .01)
            return new Rect(picture.x, picture.y, picture.width, Math.min(picture.height, Math.floor(tall + .01)));
        double wide = Math.min(picture.width, Math.floor(picture.height * w / h + .01));
        return new Rect(picture.x + Math.floor((picture.width - wide) / 2), picture.y, wide, picture.height);
    }

    double trackpadSpan(double videoWidth, double videoHeight) {
        if (!largerPicture) return trackpadSpan;
        Rect frame = pane(videoWidth, videoHeight);
        return frame.width > 0 && frame.height > 0 ? trackpad.width * frame.height / frame.width : trackpadSpan;
    }

    private static Rect[] split(Rect row, int count) {
        Rect[] result = new Rect[count];
        double each = Math.max(0, (row.width - (count - 1) * 8) / count);
        for (int i = 0; i < count; i++) result[i] = new Rect(row.x + i * (each + 8), row.y, each, row.height);
        return result;
    }

    private static Ruler ruler(Rect aa, Rect row) {
        double center = aa.x + aa.width / 2;
        double step = Math.max(0, Math.min(36, ((center - row.x) * 2 - 60) / 4));
        double width = step * 4 + 60;
        return new Ruler(new Rect(center - width / 2, row.y, width, 50), step);
    }
}
