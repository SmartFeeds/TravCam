package com.github.travcam;

import android.content.Context;

/**
 * Main Application Java File
 * Used to initialize data used across the project
 */
public class Application extends android.app.Application {
    private static android.app.Application application;

    public Application(){
        // Default Constructor
    }

    public static android.app.Application getApplication(){
        return application;
    }

    /**
     * To be used globally in the app
     * @return current application context
     */
    public static Context getContext(){
        return getApplication().getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
    }
}