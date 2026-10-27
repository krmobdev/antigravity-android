import com.agy.ui.webview.DesktopLayoutPolicy;
public final class DesktopLayoutPolicyTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        String html="<link href=\"./mobile-composer.css\" /><link href=\"./mobile-interface.css\" /><link href=\"./compiled_tailwind.css\" />";
        check(DesktopLayoutPolicy.styles(html,false).equals(html));
        String desktop=DesktopLayoutPolicy.styles(html,true);
        check(desktop.contains("mobile-composer.css\" media=\"(max-width: 720px)\""));
        check(desktop.contains("mobile-interface.css\" media=\"(max-width: 720px)\""));
        check(desktop.contains("compiled_tailwind.css\" />"));
        check(!DesktopLayoutPolicy.mobileQuery(true).contains("coarse"));
        check(DesktopLayoutPolicy.mobileQuery(false).contains("coarse"));
        System.out.println("PASS native Desktop policy: narrow responsive styles, desktop-wide baseline, touch mobile profile");
    }
}
