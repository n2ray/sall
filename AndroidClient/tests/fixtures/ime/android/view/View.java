package android.view;
import android.content.Context;
public class View {
    private final Context context;
    private final java.util.List<Runnable> posted = new java.util.ArrayList<>();
    public View(Context context) { this.context = context; }
    public Context getContext() { return context; }
    public void setFocusableInTouchMode(boolean value) { }
    public void setContentDescription(CharSequence value) { }
    public boolean onCheckIsTextEditor() { return false; }
    public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) { return null; }
    public boolean hasFocus() { return true; }
    public boolean post(Runnable task) { posted.add(task); return true; }
    public void runPosted() { while (!posted.isEmpty()) posted.remove(0).run(); }
}
