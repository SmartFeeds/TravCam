<div id="top"></div>

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <!--
    <a href="https://github.com/othneildrew/Best-README-Template">
      <img src="images/logo.png" alt="Logo" width="80" height="80">
    </a>
  -->

  <h1 align="center">TravCam Android</h1>

  <p align="center">
    Android Camera2 API for capturing images & videos
    <br />
  </p>
</div>



<!-- ABOUT THE PROJECT -->
## About The Project

TravCam library was built to handle the implementation of 'android.hardware.camera2'. TravCam can capture JPEGs and MP4s with callbacks to inform host with new media captures.
Files management also handled by TravCam with catching different SDKs exceptions for storing files on ExternalMediaStorage. 


<!-- GETTING STARTED -->
## Getting Started
The first step will be the implementation of the library using Gradle or Maven.

### Installation
#### Using Gradle:
Step 1. Add JitPack in your root `build.gradle` (project level) at the end of repositories:
```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
Step 2. Add the dependency in your `build.gradle` (app level) file
```
dependencies {
        implementation 'com.github.SmartFeeds:TravCam:{version}'
}
```

#### Using Maven:
Step 1. Add the JitPack repository to your build file:
```
<repositories>
  <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
  </repository>
</repositories>
```
Step 2. Add the dependency:
```
<dependency>
    <groupId>com.github.SmartFeeds</groupId>
    <artifactId>TravCam</artifactId>
    <version>{version}</version>
</dependency>
```

## Prerequisites

* The first requirement is `android.hardware.camera2` API which it must be already implemented in your Android studio project.
* Second you must have a `TextureView` in your XML layout file to be used by `TravCam`


## Important calls
Use activity's lifecycle methods to call `TravCam.onResume();` and `TravCam.onPause();`.
These methods should be called to free camera listeners and background threads when activity is paused, and to refresh the preview when resumed.

<!-- USAGE EXAMPLES -->
## Usage

1. First step in here is to check for all needed permissions before using `TravCam`. 
   Required permissions:
   * Launching the Camera `Manifest.permission.Camera`
   * Creating files & dirs `Manifest.permission.READ_EXTERNAL_STORAGE`
   * Creating files & dirs `Manifest.permission.WRITE_EXTERNAL_STORAGE`
   * Recording videos `Manifest.permission.RECORD_AUDIO`
   
2. In your `Activity` or `Fragment` add a `TextureView` to your XML layout file.
3. Make class implements ```TravCam.CameraHandlerListener``` and Override methods.
4. Initializing camera using `TravCam.java`
   ```
   TravCam.init
      (mTextureView, 
      /** context **/ this, 
      /** TravCam.CameraHandlerListener **/ this);
   ```
   
4. Capturing an image
   ```
   TravCam.captureImage();
   ```
5. Starting and stopping video recording
   ```
   TravCam.setUpMediaRecorder();    // Starts video recording
   TravCam.stopVideoRecording();     // Stops video recording
   TravCam.isVideoRecordingRunning() // Returns Boolean 
   ```
   
## Callbacks & Useful methods:
```
    @Override
    public void onImageCapture(@NonNull File imageFile) {
        // A new image file captured
        // TODO: Load this image to an ImageView!
    }
```

```
    @Override
    public void onVideoRecordingStarts() {
        // Video capturing stated
        
        // Caution! This callback is sent from a background thread. 
        // Updating UIs will require to be run on UI thread.
        runOnUiThread(() -> {
            // Update UI
        });
    }
```

```
    @Override
    public void onVideoRecordingEnds(@NonNull File videoFile) {
        // Video capturing ended
        // Caution! This callback is sent from a background thread. 
        // Updating UIs will require to be run on UI thread.
        runOnUiThread(() -> {
            // Update UI
        });
    }
```

```
    @Override
    public void onVideoRecordingLengthTicks(long maxProgress, long currentProgress) {
        // Update progress bar
        // Caution! This callback is sent from a background thread. 
        // Updating UIs will require to be run on UI thread.
        runOnUiThread(() -> {
            // Update UI
        });
    }
```
