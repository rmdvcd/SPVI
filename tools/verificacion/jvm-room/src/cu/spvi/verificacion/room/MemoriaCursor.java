package cu.spvi.verificacion.room;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** SOLO tools/verificacion. Cursor con todas las filas ya leídas (conversión de tipos como SQLite en Android). */
final class MemoriaCursor implements Cursor {
    private final String[] cols;
    private final List<Object[]> filas;
    private int pos = -1;
    private boolean cerrado;

    MemoriaCursor(String[] cols, List<Object[]> filas) { this.cols = cols; this.filas = filas; }

    private Object val(int i) {
        if (pos < 0 || pos >= filas.size()) throw new android.database.CursorIndexOutOfBoundsException(pos, filas.size());
        return filas.get(pos)[i];
    }

    @Override public int getCount() { return filas.size(); }
    @Override public int getPosition() { return pos; }
    @Override public boolean move(int offset) { return moveToPosition(pos + offset); }
    @Override public boolean moveToPosition(int p) {
        if (p < 0) { pos = -1; return false; }
        if (p >= filas.size()) { pos = filas.size(); return false; }
        pos = p;
        return true;
    }
    @Override public boolean moveToFirst() { return moveToPosition(0); }
    @Override public boolean moveToLast() { return moveToPosition(filas.size() - 1); }
    @Override public boolean moveToNext() { return moveToPosition(pos + 1); }
    @Override public boolean moveToPrevious() { return moveToPosition(pos - 1); }
    @Override public boolean isFirst() { return pos == 0 && !filas.isEmpty(); }
    @Override public boolean isLast() { return !filas.isEmpty() && pos == filas.size() - 1; }
    @Override public boolean isBeforeFirst() { return filas.isEmpty() || pos == -1; }
    @Override public boolean isAfterLast() { return filas.isEmpty() || pos == filas.size(); }

    @Override public int getColumnIndex(String name) {
        for (int i = 0; i < cols.length; i++) if (cols[i].equalsIgnoreCase(name)) return i;
        int punto = name.lastIndexOf('.');
        if (punto >= 0) return getColumnIndex(name.substring(punto + 1));
        String sinComillas = name.replace("`", "");
        if (!sinComillas.equals(name)) return getColumnIndex(sinComillas);
        return -1;
    }
    @Override public int getColumnIndexOrThrow(String name) {
        int i = getColumnIndex(name);
        if (i < 0) throw new IllegalArgumentException("columna '" + name + "' inexistente");
        return i;
    }
    @Override public String getColumnName(int i) { return cols[i]; }
    @Override public String[] getColumnNames() { return cols.clone(); }
    @Override public int getColumnCount() { return cols.length; }

    @Override public byte[] getBlob(int i) {
        Object o = val(i);
        if (o == null) return null;
        if (o instanceof byte[]) return (byte[]) o;
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }
    @Override public String getString(int i) {
        Object o = val(i);
        if (o == null) return null;
        if (o instanceof byte[]) return new String((byte[]) o, StandardCharsets.UTF_8);
        if (o instanceof Double) {
            double d = (Double) o;
            return d == Math.rint(d) && !Double.isInfinite(d) ? String.format(Locale.ROOT, "%.1f", d) : Double.toString(d);
        }
        return o.toString();
    }
    @Override public void copyStringToBuffer(int i, CharArrayBuffer buffer) {
        String s = getString(i);
        char[] c = s == null ? new char[0] : s.toCharArray();
        buffer.data = c;
        buffer.sizeCopied = c.length;
    }
    @Override public short getShort(int i) { return (short) getLong(i); }
    @Override public int getInt(int i) { return (int) getLong(i); }
    @Override public long getLong(int i) {
        Object o = val(i);
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString().trim()); } catch (NumberFormatException e) {
            try { return (long) Double.parseDouble(o.toString().trim()); } catch (NumberFormatException e2) { return 0L; }
        }
    }
    @Override public float getFloat(int i) { return (float) getDouble(i); }
    @Override public double getDouble(int i) {
        Object o = val(i);
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString().trim()); } catch (NumberFormatException e) { return 0.0; }
    }
    @Override public int getType(int i) {
        Object o = val(i);
        if (o == null) return FIELD_TYPE_NULL;
        if (o instanceof Long) return FIELD_TYPE_INTEGER;
        if (o instanceof Double) return FIELD_TYPE_FLOAT;
        if (o instanceof byte[]) return FIELD_TYPE_BLOB;
        return FIELD_TYPE_STRING;
    }
    @Override public boolean isNull(int i) { return val(i) == null; }

    @Override @Deprecated public void deactivate() { }
    @Override @Deprecated public boolean requery() { return !cerrado; }
    @Override public void close() { cerrado = true; }
    @Override public boolean isClosed() { return cerrado; }
    @Override public void registerContentObserver(ContentObserver observer) { }
    @Override public void unregisterContentObserver(ContentObserver observer) { }
    @Override public void registerDataSetObserver(DataSetObserver observer) { }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) { }
    @Override public void setNotificationUri(ContentResolver cr, Uri uri) { }
    @Override public Uri getNotificationUri() { return null; }
    @Override public boolean getWantsAllOnMoveCalls() { return false; }
    @Override public void setExtras(Bundle extras) { }
    @Override public Bundle getExtras() { return Bundle.EMPTY; }
    @Override public Bundle respond(Bundle extras) { return Bundle.EMPTY; }
}
