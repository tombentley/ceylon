package com.redhat.ceylon.langtools.tools.javac.resources;

import java.util.ListResourceBundle;

import com.redhat.ceylon.common.Versions;

public final class version extends ListResourceBundle {
    private static final Object CEYLON_VERSION = "ceylon " + Versions.CEYLON_VERSION;

    protected final Object[][] getContents() {
        return new Object[][] {
            { "full", CEYLON_VERSION },
            { "jdk", CEYLON_VERSION },
            { "release", CEYLON_VERSION },
        };
    }
}
