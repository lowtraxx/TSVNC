package toshsoft.TSVNC;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

class VncSettings {
    private static final VncSettings settings = new VncSettings();
    private static SharedPreferences sharedPref = null;
    private static SharedPreferences.Editor prefEditor = null;

    private static final String VNC_PREF_USERNAME = "vnc_pref_username";
    private static final String VNC_PREF_PASSWORD = "vnc_pref_password";
    private static final String VNC_PREF_PASSWORD_CHECKED = "vnc_pref_password_checked";
    private static final String VNC_PREF_SERVER = "vnc_pref_server";
    private static final String VNC_PREF_PORT = "vnc_pref_port";

    private VncSettings() {

    }

    public static VncSettings getPreferences(Context c) {
        if(sharedPref == null && c != null) {
            try {
                String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
                sharedPref = EncryptedSharedPreferences.create(
                        "secret_shared_prefs",
                        masterKeyAlias,
                        c,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );

                prefEditor = sharedPref.edit();
            } catch (Exception e) {

            }
        }
        return settings;
    }

    public void save() {
        prefEditor.commit();
    }


    // Getter
    public String getAddress() {
        return sharedPref.getString(VNC_PREF_SERVER, "127.0.0.1");
    }

    public int getPort() {
        return sharedPref.getInt(VNC_PREF_PORT, 5900);
    }

    public String getUserName() {
        return sharedPref.getString(VNC_PREF_USERNAME, "");
    }

    public String getPassword() {
        return sharedPref.getString(VNC_PREF_PASSWORD, "");
    }

    public String getColorModel() {
        return COLORMODEL.C24bit.nameString();
    }

    public boolean getKeepPassword() {
        return sharedPref.getBoolean(VNC_PREF_PASSWORD_CHECKED, false);
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

    public String getInputMode() {
        return VncCanvasActivity.FIT_SCREEN_NAME;
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


    // Setter
    public void setAddress(String address) {
        prefEditor.putString(VNC_PREF_SERVER, address);
        prefEditor.apply();
    }

    public void setPort(int port) {
        prefEditor.putInt(VNC_PREF_PORT, port);
        prefEditor.apply();
    }

    public void setUserName(String username) {
        prefEditor.putString(VNC_PREF_USERNAME, username);
        prefEditor.apply();
    }

    public void setPassword(String password) {
        prefEditor.putString(VNC_PREF_PASSWORD, password);
        prefEditor.apply();
    }

    public void setKeepPassword(boolean checked) {
        prefEditor.putBoolean(VNC_PREF_PASSWORD_CHECKED, checked);
        prefEditor.apply();
    }

    public void setScaleMode(ImageView.ScaleType scaleType) {
    }

    public void setInputMode(String name) {
    }

    public void setFollowMouse(boolean newFollow) {
    }

    public void setFollowPan(boolean newFollowPan) {
    }

    public void setColorModel(String s) {
    }


    // Generators
    public void Gen_populate(ContentValues parcelable) {
    }

    public android.content.ContentValues Gen_getValues() {
        return new ContentValues();
    }

    public void Gen_update() {
    }
}
