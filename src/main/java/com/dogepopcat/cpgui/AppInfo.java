package com.dogepopcat.cpgui;

/**
 * Static application metadata shared by the UI and the build scripts.
 */
public final class AppInfo {

    /** Product name shown in window titles, the about box and the task bar. */
    public static final String NAME = "Cmd/PowerShell GUI";

    /** Short name used for file-system artefacts (jar, app-image, config dir). */
    public static final String SLUG = "cpgui";

    /** Semantic version of the application. */
    public static final String VERSION = "0.1.0";

    private AppInfo() {
    }

    /** @return a human readable title such as {@code Cmd/PowerShell GUI 0.1.0}. */
    public static String title() {
        return NAME + " " + VERSION;
    }
}
