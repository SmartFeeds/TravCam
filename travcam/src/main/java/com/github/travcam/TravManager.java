package com.github.travcam;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

@SuppressWarnings("FieldCanBeLocal")
public class TravManager {
    /** Activity context **/
    private final Context context;

    /** Extensions to be used for storing files **/
    private final static String EXT_JPG = "jpeg";
    private final static String EXT_MP4 = "mp4";

    /** Callbacks interface **/
    private ScopedFileManagerCallbacks mFileManageCallbacks;

    public TravManager(@NonNull Context context){
        this.context = context;
    }


    // ===========================================================================================
    // ================================= Save captured image =====================================
    // ===========================================================================================
    /**
     * Converts image to bytes using imageToBytes() method
     * Generates files name using generateFileName() method
     * Writes new File from generated converted bytes using writeFileFromBytes() method
     * @param image     file to be saved
     * @param fileType  file type helps provide appropriate directory path
     */
    public void storeCapturedImage(@NonNull Image image, @NonNull ScopedFileType fileType){
        final String path = Environment.DIRECTORY_DCIM + File.separator + fileType;

        // Getting captured image bytes
        final byte[] bytes = imageToBytes(image);

        // Generate file name
        final String fileName = generateFileName(fileType);

        // Close image
        image.close();

        // save image with appropriate SDK supporting
        // then return to caller using listener
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            saveImageSDKNew(bytes, fileName, path);
        }else{
            saveImageSDKOld(bytes, fileName, path);
        }
    }

    /**
     * Used to save images for Android 10+ devices, using SDK 29+
     * This method uses {@link MediaStore} API to save media files to local storage
     * @param bytes      bytes array of current file
     * @param fileName   file name
     * @param filePath   directory path
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void saveImageSDKNew(@NonNull byte[] bytes, @NonNull String fileName, @NonNull String filePath){
        // Converting bytes to bitmap
        Bitmap bitmap = byteToBitmap(bytes);

        // Save bitmap to local storage
        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, filePath);

        final ContentResolver resolver = context.getContentResolver();
        Uri uri = null;

        try {
            final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            uri = resolver.insert(contentUri, values);

            // Catching exceptions
            if (uri == null) throw new IOException("Failed to create new MediaStore record.");
            try (final OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null) throw new IOException("Failed to open output stream.");
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) throw new IOException("Failed to save bitmap.");

                // Return created image file
                if(mFileManageCallbacks != null) {
                    File file = new File(getRealPathFromURI(uri, ScopedFileType.IMAGE));
                    if(file.exists()){
                        mFileManageCallbacks.onFileCreated(rotateImageFile(file));
                    }else{
                        mFileManageCallbacks.onError("Image file does not exist! "+file.getAbsolutePath());
                    }
                }
            }
        }
        catch (IOException e) {
            if (uri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(uri, null, null);
            }
            if(mFileManageCallbacks != null) mFileManageCallbacks.onError(e.getMessage());
        }
    }

    /**
     * Used to save images for Android versions less than 10, SDK < 29
     * @param bytes      bytes array of current file
     * @param fileName   file name
     * @param filePath   directory path
     */
    private void saveImageSDKOld(@NonNull byte[] bytes, @NonNull String fileName, @NonNull String filePath){
        final File file = new File(filePath, fileName);

        try{
            if(file.createNewFile()){
                // Write bytes to file
                final FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(bytes);
                fileOutputStream.flush();
                fileOutputStream.close();

                // Get media type from extension
                if(mFileManageCallbacks != null) mFileManageCallbacks.onFileCreated(rotateImageFile(file));
            }else{
                if (mFileManageCallbacks != null) mFileManageCallbacks.onError("Couldn't create new file!");
            }
        }catch (IOException e){
            if (mFileManageCallbacks != null) mFileManageCallbacks.onError(String.format("Exception: %s", e.getMessage()));
        }
        if (mFileManageCallbacks != null) mFileManageCallbacks.onError("Unexpected error occurred!");
    }

    /**
     * Captured image files are being rotated from {@link TravCam}
     * Using this method, images should be returned to default rotation state
     * and returned to host
     * Image file is being converted to Bitmap to apply rotation
     * Then reverted to file and returned
     * @param imageFile  Image File
     * @return           Rotated image file
     */
    private File rotateImageFile(@NonNull File imageFile){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        // Get the original bitmap from the filepath to which you want to change orientation
        // fileName ist the filepath of the image
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        // Screen orientation
        int orientation = TravCam.revertOrientationToPortrait(TravCam.getSensorOrientation());

        Matrix matrix = new Matrix();
        matrix.postRotate(orientation);
        bitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        //Convert bitmap to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
        byte[] bitmapData = bos.toByteArray();

        //write the bytes in file
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(imageFile);
            fos.write(bitmapData);
            fos.flush();
            fos.close();
            return imageFile;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return imageFile;
    }




    // ===========================================================================================
    // =================================== Generating Files ======================================
    // ===========================================================================================
    /**
     * Generates empty video file to write captured video data on
     * File will be return using callbacks listener to host {@link #mFileManageCallbacks}
     */
    public void generateVideoFile(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            generateEmptyFileNew(generateFileName(ScopedFileType.VIDEO), ScopedFileType.VIDEO);
        }else{
            generateEmptyFileOld(generateFileName(ScopedFileType.VIDEO), ScopedFileType.VIDEO);
        }
    }

    /**
     * Generates writable empty file for New SDK (SDK >= 29)
     * @param fileName  File name
     * @param type      File type to be used for creating storing path
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void generateEmptyFileNew(@NonNull String fileName, @NonNull ScopedFileType type){
        final String path = Environment.DIRECTORY_DCIM + File.separator + type;

        // Adding content values to video
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.RELATIVE_PATH, path);
        values.put(MediaStore.Video.Media.TITLE, fileName);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        // Insert video Uri
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uriVideo = resolver.insert(collection, values);

        // Writing video file using FileOutputStream
        ParcelFileDescriptor pfd;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uriVideo, "w");
            FileOutputStream out;
            if (pfd != null) {
                out = new FileOutputStream(pfd.getFileDescriptor());
                File videoFile = new File(context.getExternalFilesDir("Traveln"), fileName);
                FileInputStream in = new FileInputStream(videoFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
                out.getFD().sync();
                out.close();
                in.close();
                pfd.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        values.clear();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);

        context.getContentResolver().update(uriVideo, values, null, null);
        File file = new File(getRealPathFromURI(uriVideo, ScopedFileType.VIDEO));
        if(file.exists()){
            mFileManageCallbacks.onFileCreated(file);
        }else{
            mFileManageCallbacks.onError("Video file does not exist! "+file.getAbsolutePath());
        }
    }

    /**
     * Generates writable empty file for Old SDK (SDK < 29)
     * @param fileName  File name
     * @param type      File type to be used for creating storing path
     */
    private void generateEmptyFileOld(@NonNull String fileName, @NonNull ScopedFileType type){
        // File path
        final String path = Environment.DIRECTORY_DCIM + File.separator + type;

        // Create directory
        File dir = new File(path);
        if(!dir.exists()){
            if(dir.mkdirs()){
                File file = new File(path, fileName);
                if(file.exists()) mFileManageCallbacks.onFileCreated(file);
                try{
                    if(!file.createNewFile()){
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        fileOutputStream.flush();
                        fileOutputStream.close();
                    }
                    mFileManageCallbacks.onFileCreated(file);
                }catch (IOException e){
                    mFileManageCallbacks.onError("Video file exception: "+e.getMessage());
                }
            }
        }
        mFileManageCallbacks.onError("Could not create video file!");
    }




    // ===========================================================================================
    // ==================================== Helper Methods =======================================
    // ===========================================================================================
    /**
     * Returns absolute path for Uris
     * @param contentUri  Uri to retrieve path for
     * @return            String path
     */
    private String getRealPathFromURI(Uri contentUri, ScopedFileType type) {
        Cursor cursor = null;
        try {
            String[] projImage = { MediaStore.Images.Media.DATA };
            String[] projVideo = { MediaStore.Video.Media.DATA };

            cursor = context.getContentResolver().query(contentUri, type == ScopedFileType.IMAGE ? projImage : projVideo, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(type == ScopedFileType.IMAGE ?
                    MediaStore.Images.Media.DATA :
                    MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Generates file name using global structure for the whole project
     * Followed structure trav_{type}_{System.currentTimeMillis()}.{extension}
     * @param type      file type [img, vid]
     * @return          generated file name
     */
    public String generateFileName(@NonNull ScopedFileType type){
        String extension;
        if(type.toString().toLowerCase().contains("image")) {
            extension = EXT_JPG;
        }else{
            extension = EXT_MP4;
        }

        String typeStr = type == ScopedFileType.IMAGE ? "img" : "vid";
        return String.format("trav_%s_%s.%s", typeStr, System.currentTimeMillis(), extension);
    }

    /**
     * Request this method to delete no longer used files
     * Like dismissed video captures
     * @param file  File to be deleted
     * @return      is file deleted
     */
    public boolean deleteFile(@NonNull File file){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            // Using MediaStore to delete file for SDK >= 29
            String [] selectionArgs = new String[] {(file.getName())};
            ContentResolver contentResolver = context.getContentResolver();
            String where = MediaStore.Images.Media._ID + "=?";
            Uri filesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            contentResolver.delete(filesUri, where, selectionArgs);
            return file.exists();
        }else{
            // Old external storage file deletion process
            return (!file.isFile() || (file.isFile() && file.exists() && file.delete()));
        }
    }

    /**
     * Converts bytes array to Bitmap
     * @param b  bytes array
     * @return   Bitmap
     */
    private static Bitmap byteToBitmap(byte[] b) {
        return (b == null || b.length == 0) ? null : BitmapFactory.decodeByteArray(b, 0, b.length);
    }

    /**
     * Converts android.media.Image to bytes
     * @param image  file to be converted
     * @return       converted bytes array
     */
    private byte[] imageToBytes(Image image){
        final ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * File type enum
     */
    public enum ScopedFileType{
        IMAGE{
            @NonNull
            @Override
            public String toString() {
                return "Traveln Image";
            }
        },
        VIDEO{
            @NonNull
            @Override
            public String toString() {
                return "Traveln Video";
            }
        }
    }

    /**
     * Linking listener with host
     */
    public TravManager listenForCallbacks(ScopedFileManagerCallbacks callbacks){
        this.mFileManageCallbacks = callbacks;
        return this;
    }

    /**
     * Callbacks listener to inform host
     */
    public interface ScopedFileManagerCallbacks{
        void onFileCreated(File file);
        void onError(String message);
    }
}