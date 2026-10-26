import com.agy.ui.webview.WebAudioOrigin;
public final class WebAudioOriginTest {
    public static void main(String[] args) {
        String core = "https://127.0.0.1:45159/?csrf_token=fixture";
        String[] valid = {"https://127.0.0.1:45159", "https://127.0.0.1:45159/"};
        String[] invalid = {null, "", "http://127.0.0.1:45159/", "https://127.0.0.1:45160/",
            "https://localhost:45159/", "https://evil.invalid:45159/",
            "https://127.0.0.1.evil.invalid:45159/", "https://user@127.0.0.1:45159/",
            "https://127.0.0.1:45159/other", "https://127.0.0.1:45159/?x=1",
            "https://127.0.0.1:45159/#x", "file:///android_asset/"};
        for (String s : valid) if (!WebAudioOrigin.allowed(s,core)) throw new AssertionError("valid");
        for (String s : invalid) if (WebAudioOrigin.allowed(s,core)) throw new AssertionError("untrusted");
        if (WebAudioOrigin.allowed(valid[0],null)) throw new AssertionError("stopped");
        System.out.println("PASS WebAudioOrigin: 15 origin/lifecycle cases");
    }
}
