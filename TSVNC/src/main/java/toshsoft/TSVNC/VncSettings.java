package toshsoft.TSVNC;

import android.content.ContentValues;
import android.widget.ImageView;

class VncSettings {
    private static final VncSettings settings = new VncSettings();

    private VncSettings() {}

    public static VncSettings getPreferences() {
        return settings;
    }

    public void save() {
    }

    public String getColorModel() {
        return COLORMODEL.C24bit.nameString();
    }

    public String getAddress() {
        return "127.0.0.1";
    }

    public int getPort() {
        return 5901;
    }

    public String getPassword() {
        return "";
    }

    public boolean getKeepPassword() {
        return true;
    }

    public String getUserName() {
        return "";
    }

    public void setAddress(String toString) {
    }

    public void setPort(int parseInt) {
    }

    public void setUserName(String toString) {
    }

    public void setPassword(String toString) {
    }

    public void setKeepPassword(boolean checked) {
    }

    public android.content.ContentValues Gen_getValues() {
        return new ContentValues();
    }

    public void setScaleMode(ImageView.ScaleType scaleType) {
    }

    public void setInputMode(String name) {
    }

    public void Gen_update() {
    }

    public void setColorModel(String s) {
    }

    public void Gen_populate(ContentValues parcelable) {
    }

    public String getInputMode() {
        return VncCanvasActivity.FIT_SCREEN_NAME;
    }

    public ImageView.ScaleType getScaleMode() {
        return ImageView.ScaleType.FIT_CENTER;
    }

    public boolean getUseImmersive() {
        return true;
    }

    public boolean getFollowMouse() {
        return true;
    }

    public boolean getFollowPan() {
        return false;
    }

    public void setFollowMouse(boolean newFollow) {
    }

    public void setFollowPan(boolean newFollowPan) {
    }

    public boolean getUseWakeLock() {
        return true;
    }

    public long getForceFull() {
        return BitmapImplHint.AUTO;
    }

    public boolean getUseLocalCursor() {
        return true;
    }
}
