package yy.daniel.yyplayerforandroid;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Test APK's independent UID owns this single generated fixture; no user files are exposed. */
public final class ForeignVideoFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/video".equals(uri.getPath()) || !"r".equals(mode)) throw new FileNotFoundException();
        File file = new File(getContext().getCacheDir(), "foreign-u21.mp4");
        if (!file.exists()) {
            try (InputStream in = getContext().getAssets().open("v15-video.mp4"); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            } catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "video/mp4"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
