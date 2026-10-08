package com.atmytrack.app;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.*;

/** Opaque document IDs deliberately differ from the visible folder name. Test APK only. */
public class FolderFixtureProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    public String getType(Uri uri) { return "application/octet-stream"; }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        MatrixCursor cursor = new MatrixCursor(projection);
        String[] ids = uri.getLastPathSegment().equals("children") ? new String[]{"tone.wav","cover.png","cover.webp"} : new String[]{DocumentsContract.getDocumentId(uri)};
        for (String id: ids) {
            Object[] row = new Object[projection.length];
            for (int i=0;i<projection.length;i++) {
                switch(projection[i]) {
                    case "document_id": row[i]=id; break;
                    case "_display_name": row[i]=id.equals("opaque-root") ? "Clamo Jesus - Baruk" : id; break;
                    case "mime_type": row[i]=id.endsWith("wav") ? "audio/wav" : "image/"+id.substring(id.lastIndexOf('.')+1); break;
                    case "_size": row[i]=new File(getContext().getFilesDir(),id).length(); break;
                }
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        getContext().getFilesDir().mkdirs();
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),DocumentsContract.getDocumentId(uri)),mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }
    public Uri insert(Uri uri,ContentValues values) { return null; }
    public int delete(Uri uri,String selection,String[] args) { return 0; }
    public int update(Uri uri,ContentValues values,String selection,String[] args) { return 0; }
}
