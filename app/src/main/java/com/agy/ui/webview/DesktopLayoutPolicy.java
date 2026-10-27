package com.agy.ui.webview;

/** One responsive policy for native profiles and mobile presentation styles. */
public final class DesktopLayoutPolicy {
    private DesktopLayoutPolicy() {}
    public static String mobileQuery(boolean desktop) {
        return desktop ? "(max-width: 720px)" : "(max-width: 720px), (pointer: coarse)";
    }
    public static String styles(String html, boolean desktop) {
        if (!desktop) return html;
        for (String file : new String[]{"mobile-composer.css", "mobile-interface.css"}) {
            String href = "href=\"./" + file + "\"";
            html = html.replace(href, href + " media=\"(max-width: 720px)\"");
        }
        return html;
    }
}
