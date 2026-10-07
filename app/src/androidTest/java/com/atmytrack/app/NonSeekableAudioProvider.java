package com.atmytrack.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// Java keeps this isolated test-APK process independent of the target APK's Kotlin runtime.
public class NonSeekableAudioProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int i=0; i<columns.length; i++) if (columns[i].equals(OpenableColumns.DISPLAY_NAME)) values[i] = "Cloud.wav";
        cursor.addRow(values); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    ByteBuffer b = ByteBuffer.allocate(96044).order(ByteOrder.LITTLE_ENDIAN);
                    b.put("RIFF".getBytes(StandardCharsets.US_ASCII)); b.putInt(96036); b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)); b.putInt(16);
                    b.putShort((short)1); b.putShort((short)1); b.putInt(48000); b.putInt(96000); b.putShort((short)2); b.putShort((short)16);
                    b.put("data".getBytes(StandardCharsets.US_ASCII)); b.putInt(96000);
                    for (int i=0;i<48000;i++) b.putShort((short)8192);
                    out.write(b.array());
                } catch (IOException e) { android.util.Log.e("StreamFixture", "Pipe closed", e); }
            }).start();
            return pipe[0];
        } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
