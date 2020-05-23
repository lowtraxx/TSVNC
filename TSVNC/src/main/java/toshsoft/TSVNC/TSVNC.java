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

package toshsoft.TSVNC;

import android.app.Activity;
import android.app.ActivityManager.MemoryInfo;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;

public class TSVNC extends Activity {
	private EditText ipText;
	private EditText portText;
	private EditText passwordText;
	private Button goButton;
	private TextView repeaterText;
	private RadioGroup groupForceFullScreen;
	private Spinner colorSpinner;
	private Spinner spinnerConnection;
	private VncSettings settings;
	private EditText textNickname;
	private EditText textUsername;
	private CheckBox checkboxKeepPassword;
	private CheckBox checkboxLocalCursor;
	private CheckBox checkboxWakeLock;
	private boolean repeaterTextSet;

	@Override
	public void onCreate(Bundle icicle) {

		super.onCreate(icicle);
		// setContentView(R.layout.main);

		// get prompts.xml view
		LayoutInflater li = LayoutInflater.from(this);
		View promptsView = li.inflate(R.layout.main, null);

		ipText = (TextInputEditText)promptsView.findViewById(R.id.textIP);
		portText = (TextInputEditText)promptsView.findViewById(R.id.textPORT);
		passwordText = (TextInputEditText)promptsView.findViewById(R.id.textPASSWORD);
		// textNickname = (TextInputEditText) findViewById(R.id.textNickname);
		textUsername = (TextInputEditText)promptsView.findViewById(R.id.textUsername);
		checkboxKeepPassword = (CheckBox)promptsView.findViewById(R.id.checkboxKeepPassword);

		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
				this);

		// set prompts.xml to alertdialog builder
		alertDialogBuilder.setView(promptsView);

		// set dialog message
		alertDialogBuilder
				.setCancelable(false)
				.setPositiveButton(R.string.connect_button,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,int id) {
								canvasStart();
							}
						})
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                finishAndRemoveTask();
                            }
                        });

		// create alert dialog
		AlertDialog alertDialog = alertDialogBuilder.create();

		// show it
		alertDialog.show();

		// Get screen width and height in pixels and set the Dialog to be
		// 70 percent of its size
		/*DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		int displayWidth = displayMetrics.widthPixels;
		int displayHeight = displayMetrics.heightPixels;
		WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();

		layoutParams.copyFrom(alertDialog.getWindow().getAttributes());

		int dialogWindowWidth = (int) (displayWidth * 0.9f);
		int dialogWindowHeight = (int) (displayHeight * 0.9f);

		layoutParams.width = dialogWindowWidth;
		layoutParams.height = dialogWindowHeight;
		alertDialog.getWindow().setAttributes(layoutParams);*/
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
		if (settings.getKeepPassword() || settings.getPassword().length()>0) {
			passwordText.setText(settings.getPassword());
		}
		// groupForceFullScreen.check(selected.getForceFull()==BitmapImplHint.AUTO ? R.id.radioForceFullScreenAuto : (selected.getForceFull() == BitmapImplHint.FULL ? R.id.radioForceFullScreenOn : R.id.radioForceFullScreenOff));
		checkboxKeepPassword.setChecked(settings.getKeepPassword());
		// checkboxLocalCursor.setChecked(selected.getUseLocalCursor());
		// checkboxWakeLock.setChecked(selected.getUseWakeLock());
		// textNickname.setText(selected.getNickname());
		textUsername.setText(settings.getUserName());

		/*
		COLORMODEL cm = COLORMODEL.valueOf(selected.getColorModel());
		COLORMODEL[] colors=COLORMODEL.values();
		for (int i=0; i<colors.length; ++i)
		{
			if (colors[i] == cm) {
				colorSpinner.setSelection(i);
				break;
			}
		}
		updateRepeaterInfo(selected.getUseRepeater(), selected.getRepeaterId());
		 */
	}
	
	/**
	 * Called when changing view to match selected connection or from
	 * Repeater dialog to update the repeater information shown.
	 * @param repeaterId If null or empty, show text for not using repeater
	 */
	void updateRepeaterInfo(boolean useRepeater, String repeaterId)
	{
		if (useRepeater)
		{
			repeaterText.setText(repeaterId);
			repeaterTextSet = true;
		}
		else
		{
			repeaterText.setText(getText(R.string.repeater_empty_text));
			repeaterTextSet = false;
		}
	}
	
	private void updateSelectedFromView() {
		if (settings ==null) {
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
		// selected.setUseLocalCursor(checkboxLocalCursor.isChecked());
		// selected.setColorModel(((COLORMODEL)colorSpinner.getSelectedItem()).nameString());
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
		settings = VncSettings.getPreferences();
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
		intent.putExtra(VncConstants.CONNECTION, settings.Gen_getValues());
		startActivity(intent);
	}
}
