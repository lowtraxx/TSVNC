/* 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

//
// androidVNC is the Activity for setting VNC server IP and port.
//

package de.toshsoft.tsvnc;

import android.app.Activity;
import android.app.ActivityManager.MemoryInfo;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;

import de.toshsoft.tsvnc.R;

public class TSVNC extends Activity {
	private EditText ipText;
	private EditText portText;
	private EditText passwordText;
	private VncSettings settings;
	private EditText textUsername;
	private CheckBox checkboxKeepPassword;
	private CheckBox checkboxUseOverlay;
	private CheckBox checkboxAutomaticLogin;
	private AutoCompleteTextView editTextFilledExposedDropdown;

	@Override
	public void onCreate(Bundle icicle) {

		super.onCreate(icicle);
		// setContentView(R.layout.main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		// get prompts.xml view
		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.main, null);

		ipText = (TextInputEditText)promptsView.findViewById(R.id.textIP);
		portText = (TextInputEditText)promptsView.findViewById(R.id.textPORT);
		passwordText = (TextInputEditText)promptsView.findViewById(R.id.textPASSWORD);
		// textNickname = (TextInputEditText) findViewById(R.id.textNickname);
		textUsername = (TextInputEditText)promptsView.findViewById(R.id.textUsername);
		checkboxKeepPassword = (CheckBox)promptsView.findViewById(R.id.checkboxKeepPassword);
		checkboxUseOverlay = (CheckBox)promptsView.findViewById(R.id.checkboxUseOverlay);
		checkboxAutomaticLogin = (CheckBox)promptsView.findViewById(R.id.checkboxAutomaticLogin);

		editTextFilledExposedDropdown =
				promptsView.findViewById(R.id.filled_exposed_dropdown);
		editTextFilledExposedDropdown.setInputType(InputType.TYPE_NULL);

		String[] COLORMODES = new String[] {COLORMODEL.C2.toString(),
				COLORMODEL.C4.toString(), COLORMODEL.C8.toString(),
				COLORMODEL.C64.toString(), COLORMODEL.C256.toString(),
				COLORMODEL.C24bit.toString()
		};

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.dropdown_menu_popup_item,
						COLORMODES);


        editTextFilledExposedDropdown.setAdapter(adapter);

		settings = VncSettings.getPreferences(getApplication());

		boolean automaticLogin = settings.getAutomaticLogin();
		checkboxAutomaticLogin.setChecked(automaticLogin);

		// If automatic login is enabled, try to straight jump to the canvas
		if(automaticLogin) {
			arriveOnPage();
			canvasStart();
		} else {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					this);

			// set prompts.xml to alertdialog builder
			alertDialogBuilder.setView(promptsView);

			// set dialog message
			alertDialogBuilder
					.setCancelable(false)
					.setPositiveButton(R.string.connect_button,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									canvasStart();
								}
							})
					.setNegativeButton(android.R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int id) {
									finishAndRemoveTask();
								}
							});

			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();

			// show it
			alertDialog.show();
		}
	}
	
	protected void onDestroy() {
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.androidvncmenu,menu);
		return true;
	}

	private void updateViewFromSelected() {
		if (settings ==null)
			return;
		ipText.setText(settings.getAddress());
		portText.setText(Integer.toString(settings.getPort()));
		if (settings.getKeepPassword() || settings.getPassword().length() > 0) {
			passwordText.setText(settings.getPassword());
		}
		checkboxKeepPassword.setChecked(settings.getKeepPassword());
		checkboxUseOverlay.setChecked(settings.getMenuOverlay());
		checkboxAutomaticLogin.setChecked(settings.getAutomaticLogin());
		textUsername.setText(settings.getUserName());

		editTextFilledExposedDropdown.setText(COLORMODEL.getModelForId(settings.getColorModel()).toString(), false);
	}
	
	private void updateSelectedFromView() {
		if (settings == null) {
			return;
		}
		settings.setAddress(ipText.getText().toString());
		try
		{
			settings.setPort(Integer.parseInt(portText.getText().toString()));
		}
		catch (NumberFormatException nfe)
		{
			
		}
		// selected.setNickname(textNickname.getText().toString());
		settings.setUserName(textUsername.getText().toString());
		// selected.setForceFull(groupForceFullScreen.getCheckedRadioButtonId()==R.id.radioForceFullScreenAuto ? BitmapImplHint.AUTO : (groupForceFullScreen.getCheckedRadioButtonId()==R.id.radioForceFullScreenOn ? BitmapImplHint.FULL : BitmapImplHint.TILE));
		settings.setPassword(passwordText.getText().toString());
		settings.setKeepPassword(checkboxKeepPassword.isChecked());
		settings.setMenuOverlay(checkboxUseOverlay.isChecked());
		settings.setAutomaticLogin(checkboxAutomaticLogin.isChecked());
		// selected.setUseLocalCursor(checkboxLocalCursor.isChecked());
		settings.setColorModel(COLORMODEL.getModelForDesc(editTextFilledExposedDropdown.getText().toString()).getId());
		//selected.setUseWakeLock(checkboxWakeLock.isChecked());
		/*if (repeaterTextSet)
		{
			selected.setRepeaterId(repeaterText.getText().toString());
			selected.setUseRepeater(true);
		}
		else
		{
			selected.setUseRepeater(false);
		}*/
	}
	
	protected void onStart() {
		super.onStart();
		arriveOnPage();
	}
	
	void arriveOnPage() {
		updateViewFromSelected();
	}
	
	protected void onStop() {
		super.onStop();
		if ( settings == null ) {
			return;
		}
		updateSelectedFromView();
		settings.save();
	}

	private void canvasStart() {
		if (settings == null) return;
		MemoryInfo info = Utils.getMemoryInfo(this);
		if (info.lowMemory) {
			// Low Memory situation.  Prompt.
			Utils.showYesNoPrompt(this, "Continue?", "Android reports low system memory.\nContinue with VNC connection?", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					vnc();
				}
			}, null);
		} else
			vnc();
	}

	private void vnc() {
		updateSelectedFromView();
		Intent intent = new Intent(this, VncCanvasActivity.class);
		startActivity(intent);
		finish();
	}
}
