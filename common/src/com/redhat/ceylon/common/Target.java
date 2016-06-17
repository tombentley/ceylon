package com.redhat.ceylon.common;

/**
 * Possible values of the {@code --target} CLI option
 * @author tom
 */
public enum Target {
    CEYLON1_2("1.2"),
    CEYLON1_3("1.3");
    
    public final String version;
    
    Target(String version) {
        this.version = version;
    }
    
    public static Target fromString(String s) {
        if ("1.2".equals(s)) {
            return CEYLON1_2;
        } else if ("1.3".equals(s)) {
            return CEYLON1_3;
        } else {
            return null;
        }
    }
    
    public boolean supportsDefaultInterfaces() {
        return this.ordinal() >= CEYLON1_3.ordinal();
    }
}
