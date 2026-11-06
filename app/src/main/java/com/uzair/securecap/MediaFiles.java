package com.uzair.securecap;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MediaFiles {
    public static final class Item {
        public Uri uri;
        public String name, mime;
        public long size, date, duration;
        public boolean video() { return mime != null && mime.startsWith("video/"); }
    }
    private MediaFiles() {}
    public static Uri create(Context context, boolean video) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "SecureCap_" + stamp + (video ? ".mp4" : ".png"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, video ? "Movies/SecureCap" : "Pictures/SecureCap");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = video ? MediaStore.Video.Media.getContentUri("external_primary") : MediaStore.Images.Media.getContentUri("external_primary");
        Uri uri = context.getContentResolver().insert(collection, values);
        if (uri == null) throw new IllegalStateException("Could not create media file");
        return uri;
    }
    public static void publish(Context context, Uri uri) {
        ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
    }
    public static List<Item> list(Context context) {
        List<Item> items = new ArrayList<>();
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] columns = {"_id", "_display_name", "mime_type", "_size", "date_added", "duration"};
        try (Cursor cursor = context.getContentResolver().query(collection, columns,
                "(relative_path = ? OR relative_path = ?) AND is_pending = 0",
                new String[]{"Movies/SecureCap/", "Pictures/SecureCap/"}, "date_added DESC, _id DESC")) {
            if (cursor != null) while (cursor.moveToNext()) {
                Item item = new Item(); item.uri = ContentUris.withAppendedId(collection, cursor.getLong(0));
                item.name = cursor.getString(1); item.mime = cursor.getString(2); item.size = cursor.getLong(3);
                item.date = cursor.getLong(4); item.duration = cursor.getLong(5); items.add(item);
            }
        }
        return items;
    }
    public static void share(Context context, Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(android.content.ClipData.newRawUri("SecureCap capture", uri));
        context.startActivity(Intent.createChooser(intent, "Share capture"));
    }
    public static String duration(long ms) {
        long sec = ms / 1000; return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }
    public static String size(long bytes) { return String.format(Locale.US, "%.1f MB", bytes / 1048576f); }
}
