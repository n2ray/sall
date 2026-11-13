package io.github.n2ray.sill;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Synthetic strings only; no microphone, Mac connection or user preferences. */
public final class GroupProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String prefix = new String(new char[31]).replace('\0', 'a');
            for (String character : new String[]{"e\u0301", "👍🏽", "👨‍👩‍👧‍👦", "🇩🇪"}) {
                if (!(prefix + character).equals(ConnectionGroups.clean(prefix + character + "z")))
                    throw new AssertionError("grapheme boundary");
            }
            if (!"Home".equals(ConnectionGroups.clean("  home \n")) || ConnectionGroups.clean("Other") != null)
                throw new AssertionError("normalization");
            result.putString("result", "PASS: group graphemes check");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
