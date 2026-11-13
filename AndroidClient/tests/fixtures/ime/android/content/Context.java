package android.content;
public class Context {
    public static final String INPUT_METHOD_SERVICE = "input_method";
    private final android.view.inputmethod.InputMethodManager manager = new android.view.inputmethod.InputMethodManager();
    public Object getSystemService(String name) { return manager; }
}
