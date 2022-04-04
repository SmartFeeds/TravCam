package com.github.travcam;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Common Activity includes common methods used across other Activities
 */
@SuppressWarnings("FieldCanBeLocal")
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getActivityLayoutResourceId());
    }

    /**
     * Used to load Activity resource layout
     * @return resource XML file
     */
    protected abstract int getActivityLayoutResourceId();

    /**
     * Checks and returns missing permissions from a String[] of permissions
     */
    protected List<String> getMissingPermissions(@NonNull String ...permissions){
        final List<String> missing = new ArrayList<>();
        for(String permission : permissions){
            if(ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED){
                // Only use WRITE_EXTERNAL_STORAGE permission for SDK 29 or lower
                if(!(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE))){
                    missing.add(permission);
                }
            }
        }
        return missing;
    }
}