package com.agy.ui.webview;

import java.net.URI;

/** Pure origin policy; never grant to arbitrary loopback ports or remote pages. */
public final class WebAudioOrigin {
    private WebAudioOrigin() {}
    public static boolean allowed(String origin, String serverUrl) {
        try {
            URI page = new URI(origin), core = new URI(serverUrl);
            return "https".equals(page.getScheme()) && "https".equals(core.getScheme())
                    && "127.0.0.1".equals(page.getHost()) && page.getHost().equals(core.getHost())
                    && page.getPort() > 0 && page.getPort() == core.getPort()
                    && page.getRawUserInfo() == null && core.getRawUserInfo() == null
                    && page.getRawQuery() == null && page.getRawFragment() == null
                    && (page.getRawPath().isEmpty() || "/".equals(page.getRawPath()));
        } catch (Exception invalid) { return false; }
    }
}
