package com.karaoke.config;

public enum StorageEnvironment {
    DEV("dev"),
    STAGING("staging"),
    PROD("prod");
    
    private final String pathValue;
    
    StorageEnvironment(String pathValue) {
        this.pathValue = pathValue;
    }
    
    public String getPathValue() {
        return pathValue;
    }
}
