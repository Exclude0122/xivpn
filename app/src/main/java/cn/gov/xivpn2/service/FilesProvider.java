package cn.gov.xivpn2.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import cn.gov.xivpn2.R;

public class FilesProvider extends DocumentsProvider {

    private static final String TAG = "FilesProvider";

    private static final String[] DEFAULT_ROOT_PROJECTION =
            new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_FLAGS, Root.COLUMN_SUMMARY};

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new
            String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE,};

    private String base;

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        if (projection == null) projection = DEFAULT_ROOT_PROJECTION;
        MatrixCursor matrixCursor = new MatrixCursor(projection);

        MatrixCursor.RowBuilder rowBuilder = matrixCursor.newRow();
        rowBuilder.add(Root.COLUMN_ROOT_ID, "files");
        rowBuilder.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD);
        rowBuilder.add(Root.COLUMN_DOCUMENT_ID, "/");
        rowBuilder.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        rowBuilder.add(Root.COLUMN_TITLE, "XiVPN");

        return matrixCursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        Log.d(TAG, "queryDocument " + documentId);

        if (projection == null) projection = DEFAULT_DOCUMENT_PROJECTION;

        if (documentId.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        File file = new File(base + documentId);

        if (!file.exists()) {
            throw new FileNotFoundException("file does not exist");
        }

        MatrixCursor cursor = new MatrixCursor(projection);
        MatrixCursor.RowBuilder row = cursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        if (file.isDirectory()) {
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        } else {
            row.add(Document.COLUMN_MIME_TYPE, "text/plain");
        }

        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE);
        row.add(Document.COLUMN_SIZE, file.length());
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        Log.d(TAG, "queryChildDocuments " + parentDocumentId);

        if (projection == null) projection = DEFAULT_DOCUMENT_PROJECTION;

        if (parentDocumentId.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        MatrixCursor cursor = new MatrixCursor(projection);

        File f = new File(base + parentDocumentId);

        if (!f.exists()) {
            throw new FileNotFoundException("file does not exist");
        }

        for (File file : Objects.requireNonNull(f.listFiles())) {

            MatrixCursor.RowBuilder row = cursor.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath().substring(base.length()));
            row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE);
            row.add(Document.COLUMN_SIZE, file.length());
            row.add(Document.COLUMN_DISPLAY_NAME, file.getName());

            if (file.isDirectory()) {
                row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            } else {
                row.add(Document.COLUMN_MIME_TYPE, "text/plain");
            }
        }

        return cursor;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        Log.d(TAG, "deleteDocument " + documentId);

        if (documentId.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        File file = new File(base + documentId);

        if (!file.exists()) {
            throw new FileNotFoundException("file does not exist");
        }

        FileUtils.deleteQuietly(file);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        Log.d(TAG, "isChildDocument " + parentDocumentId + ", " + documentId);

        return documentId.startsWith(parentDocumentId);
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, @Nullable CancellationSignal signal) throws FileNotFoundException {
        Log.d(TAG, "openDocument " + documentId + " " + mode);

        if (documentId.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        File file = new File(base + documentId);

        if (!file.exists()) {
            throw new FileNotFoundException("file does not exist");
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        Log.d(TAG, "renameDocument " + documentId + " " + displayName);

        if (documentId.contains("..") || displayName.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        File file = new File(base + documentId);

        if (!file.exists()) {
            throw new FileNotFoundException("file does not exist");
        }

        File dest = new File(file.getParentFile(), displayName);

        if (dest.exists()) {
            throw new FileNotFoundException("file with same name already exists");
        }

        if (!file.renameTo(dest)) {
            throw new FileNotFoundException("rename failed");
        }

        return dest.getAbsolutePath().substring(base.length());
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        Log.d(TAG, "createDocument " + parentDocumentId + " " + mimeType + " " + displayName);

        if (parentDocumentId.contains("..") || displayName.contains("..")) {
            throw new FileNotFoundException("illegal file name");
        }

        File file = new File(base + parentDocumentId + "/" + displayName);
        if (file.exists()) {
            throw new FileNotFoundException("file already exists");
        }

        try {
            if (mimeType.equals("vnd.android.document/directory")) {
                file.mkdirs();
            } else {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new FileNotFoundException("IOException: " + e);
        }

        return parentDocumentId + "/" + displayName;
    }

    @Override
    public boolean onCreate() {
        File f = Objects.requireNonNull(getContext()).getFilesDir();
        f.mkdirs();
        base = f.getAbsolutePath();
        Log.d(TAG, "base " + base);
        return true;
    }


}
