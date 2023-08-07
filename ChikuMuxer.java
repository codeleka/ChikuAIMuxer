import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class ChikuMuxer {
    
    public static boolean cancelMixer = false;

    public interface ChikuMux {
        void onStart(int totalSize);

        void onProgress(int progress);

        void onComplete(String path);

        void onFailed(String message);
    }

    private static int progressCounter = 0;

    @SuppressLint("WrongConstant")
    public static void videoAudioMuxer(String videoFilePath, String audioFilePath, Activity activity, ChikuMux chikuMux) {
        
        if (!videoFilePath.toLowerCase().endsWith(".mp4")) {
            chikuMux.onFailed("Please Select MP4 Video");
            return;
        }

        if (!audioFilePath.toLowerCase().endsWith(".aac")) {
            chikuMux.onFailed("Please Select AAC Audio");
            return;
        }
        
        try {
            File chikuCheckVideoFile = new File(videoFilePath);
            File chikuCheckAudioFile = new File(audioFilePath);

            if (!(chikuCheckVideoFile.exists() && chikuCheckAudioFile.exists())) {
                chikuMux.onFailed("File not exist");
                return;
            }
        } catch (Exception e) {
            chikuMux.onFailed("Access Denied");
            return;
        }

        ChikuMuxer.cancelMixer = false;
        progressCounter = 0;


        new Thread(() -> {

            @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
            File savedDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/ChikuAIMuxer");

            if (!savedDirectory.exists()) {
                savedDirectory.mkdir();
                savedDirectory.mkdirs();
            }

            String opPath = savedDirectory + "/" + timeStamp + "_editor_video.mp4";

            String outputFile = "";

            try {


                File file = new File(opPath);

                file.createNewFile();
                outputFile = file.getAbsolutePath();

                MediaExtractor videoExtractor = new MediaExtractor();
                videoExtractor.setDataSource(videoFilePath);
                MediaExtractor audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(audioFilePath);

//            Log.d("CHEDDDDDDDD", "Video Extractor Track Count " + videoExtractor.getTrackCount());
//            Log.d("CHEDDDDDDDD", "Audio Extractor Track Count " + audioExtractor.getTrackCount());

                MediaMuxer muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

//            --- video --------
                videoExtractor.selectTrack(0);
                MediaFormat videoFormat = videoExtractor.getTrackFormat(0);
                int videoTrack = muxer.addTrack(videoFormat);


//            ------ audio -------
                audioExtractor.selectTrack(0);
                MediaFormat audioFormat = audioExtractor.getTrackFormat(0);
                int audioTrack = muxer.addTrack(audioFormat);


//            Log.d("CHEDDDDDDDD", "Video Format " + videoFormat.toString());
//            Log.d("CHEDDDDDDDD", "Audio Format " + audioFormat.toString());

                boolean sawEOS = false;

                int offset = 100;
                int sampleSize = 256 * 1024;
                ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
                ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
                MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
                MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();


                videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

                muxer.start();


                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chikuMux.onStart(ChikuMuxer.getSizeInKb(new File(videoFilePath)) + ChikuMuxer.getSizeInKb(new File(audioFilePath)));
                    }
                });


                while (!sawEOS) {

                    if (ChikuMuxer.cancelMixer) {
                        deleteJunk(opPath);
                        break;
                    }

                    videoBufferInfo.offset = offset;
                    videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);

                    if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                        sawEOS = true;
                        videoBufferInfo.size = 0;
                    } else {

                        videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                        videoBufferInfo.flags = videoExtractor.getSampleFlags();
                        muxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo);
                        videoExtractor.advance();

                        progressCounter++;
                        activity.runOnUiThread(() -> chikuMux.onProgress(progressCounter));

                    }
                }

                boolean sawEOS2 = false;

                while (!sawEOS2) {

                    if (ChikuMuxer.cancelMixer) {
                        deleteJunk(opPath);
                        break;
                    }

                    audioBufferInfo.offset = offset;
                    audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);

                    if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                        sawEOS2 = true;
                        audioBufferInfo.size = 0;
                    } else {
                        audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                        audioBufferInfo.flags = audioExtractor.getSampleFlags();
                        muxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo);
                        audioExtractor.advance();
                    }

                    progressCounter++;
                    activity.runOnUiThread(() -> chikuMux.onProgress(progressCounter));
                }

                muxer.stop();
                muxer.release();
                if (!ChikuMuxer.cancelMixer) {
                    activity.runOnUiThread(() -> chikuMux.onComplete(opPath));
                }
            } catch (Exception e) {
                deleteJunk(opPath);
                activity.runOnUiThread(() -> chikuMux.onFailed(e.toString()));
            }
        }).start();
    }

    private static void deleteJunk(String opPath) {
        try {
            if (opPath.isEmpty()) {
                return;
            }
            File file = new File(opPath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }


//================================================================= File Helper =========================================

    public static String getPathFromUri(Context context, Uri uri) {

        if (DocumentsContract.isDocumentUri(context, uri)) {

            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                String dstPath = context.getCacheDir().getAbsolutePath() + File.separator + getFileName(context, uri);
                if (copyFile(context, uri, dstPath)) {
                    return dstPath;
                }
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) return uri.getLastPathSegment();
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static String getFileName(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        int nameindex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        cursor.moveToFirst();
        return cursor.getString(nameindex);
    }

    private static boolean copyFile(Context context, Uri uri, String dstPath) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            outputStream = new FileOutputStream(dstPath);

            byte[] buff = new byte[100 * 1024];
            int len;
            while ((len = inputStream.read(buff)) != -1) {
                outputStream.write(buff, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }


    //    ---------Information----------

    public static double getDurationInt(String filePath) {
        try {
            MediaMetadataRetriever metaRetriever_int = new MediaMetadataRetriever();
            metaRetriever_int.setDataSource(filePath);
            String songDuration = metaRetriever_int.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            assert songDuration != null;
            double duration = Double.parseDouble(songDuration);
            double time = duration / 1000;
            metaRetriever_int.release();
            return time;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public static int getSize(File file) {
        return (int) (file.length() / (1024 * 1024));
    }

    public static int getSizeInKb(File file) {
        return (int) (file.length() / (1024));
    }


//    --------- Chooser----------

    public static void chooser(Activity activity, String type, int RESPONSE_CODE) {
        Intent intent = new Intent();
        intent.setType(type);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        activity.startActivityForResult(Intent.createChooser(intent, "Select File"), RESPONSE_CODE);
    }

}
