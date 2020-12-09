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
// CanvasView is the Activity for showing VNC Desktop.
//
package de.toshsoft.tsvnc;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;

import com.antlersoft.android.bc.BCFactory;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import de.toshsoft.tsvnc.R;

public class VncCanvasActivity extends Activity {

	private final static String TAG = "VncCanvasActivity";

	AbstractInputHandler inputHandler;

	VncCanvas vncCanvas;

	private MenuItem[] inputModeMenuItems;
	private AbstractInputHandler inputModeHandlers[];
	private VncSettings settings;
	private boolean trackballButtonDown;
	private static final int inputModeIds[] = { R.id.itemInputFitToScreen };

	Panner panner;

	@Override
	public void onCreate(Bundle icicle) {

		super.onCreate(icicle);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		settings = VncSettings.getPreferences(getApplication());
		Intent i = getIntent();
		Uri data = i.getData();
		if ((data != null) && (data.getScheme().equals("vnc"))) {
			String host = data.getHost();
			// This should not happen according to Uri contract, but bug introduced in Froyo (2.2)
			// has made this parsing of host necessary
			int index = host.indexOf(':');
			int port;
			if (index != -1)
			{
				try
				{
					port = Integer.parseInt(host.substring(index + 1));
				}
				catch (NumberFormatException nfe)
				{
					port = 0;
				}
				host = host.substring(0,index);
			}
			else
			{
				port = data.getPort();
			}
			if (host.equals(VncConstants.CONNECTION))
			{

			}
			else
			{
			    settings.setAddress(host);
			    settings.setPort(port);
			    List<String> path = data.getPathSegments();
			    if (path.size() >= 1) {
			        settings.setColorModel(path.get(0));
			    }
			    if (path.size() >= 2) {
			        settings.setPassword(path.get(1));
			    }
			    settings.save();
			}
		} else {
		    if (settings.getPort() == 0)
			    settings.setPort(5900);

            // Parse a HOST:PORT entry
		    String host = settings.getAddress();
		    if (host.indexOf(':') > -1) {
			    String p = host.substring(host.indexOf(':') + 1);
			    try {
				    settings.setPort(Integer.parseInt(p));
			    } catch (Exception e) {
			    }
			    settings.setAddress(host.substring(0, host.indexOf(':')));
	  	    }
		}
		setContentView(R.layout.canvas);

		vncCanvas = (VncCanvas) findViewById(R.id.vnc_canvas);
		vncCanvas.initializeVncCanvas(settings, new Runnable() {
			public void run() {
				setModes();
			}
		});

		panner = new Panner(this, vncCanvas.handler);

		inputHandler = getInputHandlerById(R.id.itemInputFitToScreen);

		if(!settings.getMenuOverlay()) {
            FrameLayout layout = (FrameLayout) findViewById(R.id.corner_behavior_container);
            layout.setVisibility(layout.GONE);
            layout.requestLayout();
        } else {
			final Activity vca = this;
            Button logoutButton = (Button) findViewById(R.id.logoutButton);
            logoutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                	settings.setAutomaticLogin(false);
					Intent intent = new Intent(vca, TSVNC.class);
					startActivity(intent);
					finish();
                }
            });

			Button exitButton = (Button) findViewById(R.id.exitButton);
			exitButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					finish();
				}
			});
        }

		// We need to do this here, otherwise the first KeyEvent
		// gets swallowed
		vncCanvas.setFocusableInTouchMode(true);
		vncCanvas.requestFocus();
	}

	/**
	 * Set modes on start to match what is specified in the ConnectionBean;
	 * color mode (already done) scaling, input mode
	 */
	void setModes() {
		AbstractInputHandler handler = getInputHandlerByName(settings
				.getInputMode());
		AbstractScaling.getByScaleType(settings.getScaleMode())
				.setScaleTypeForActivity(this);
		this.inputHandler = handler;
		showPanningState();
		BCFactory.getInstance().getSystemUiVisibility().HideSystemUI(vncCanvas);
	}

	VncSettings getSettings() {
		return settings;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// ignore orientation/keyboard change
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && settings.getScaleMode()==ScaleType.MATRIX && settings.getUseImmersive()) {
			BCFactory.getInstance().getSystemUiVisibility().HideSystemUI(vncCanvas);
		}
	}

	@Override
	protected void onStop() {
		vncCanvas.disableRepaints();
		// Remove the password if the user does not want it saved
		if(!settings.getKeepPassword())
			settings.setPassword("");
		super.onStop();
	}

	@Override
	protected void onRestart() {
		vncCanvas.enableRepaints();
		super.onRestart();
	}

	@Override
	protected void onResume() {
		if(settings.getUseImmersive())
			BCFactory.getInstance().getSystemUiVisibility().HideSystemUI(vncCanvas);
		super.onResume();
	}

	/** {@inheritDoc} */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.vnccanvasactivitymenu, menu);

		if (vncCanvas.scaling != null)
			menu.findItem(vncCanvas.scaling.getId()).setChecked(true);

		Menu inputMenu = menu.findItem(R.id.itemInputMode).getSubMenu();

		inputModeMenuItems = new MenuItem[inputModeIds.length];
		for (int i = 0; i < inputModeIds.length; i++) {
			inputModeMenuItems[i] = inputMenu.findItem(inputModeIds[i]);
		}
		updateInputMenu();
		menu.findItem(R.id.itemFollowMouse).setChecked(
				settings.getFollowMouse());
		menu.findItem(R.id.itemFollowPan).setChecked(settings.getFollowPan());
		return true;
	}

	/**
	 * Change the input mode sub-menu to reflect change in scaling
	 */
	void updateInputMenu() {
		if (inputModeMenuItems == null || vncCanvas.scaling == null) {
			return;
		}
		for (MenuItem item : inputModeMenuItems) {
			item.setEnabled(vncCanvas.scaling
					.isValidInputMode(item.getItemId()));
			if (getInputHandlerById(item.getItemId()) == inputHandler)
				item.setChecked(true);
		}
	}

	/**
	 * If id represents an input handler, return that; otherwise return null
	 * 
	 * @param id
	 * @return
	 */
	AbstractInputHandler getInputHandlerById(int id) {
		if (inputModeHandlers == null) {
			inputModeHandlers = new AbstractInputHandler[inputModeIds.length];
		}
		for (int i = 0; i < inputModeIds.length; ++i) {
			if (inputModeIds[i] == id) {
				if (inputModeHandlers[i] == null) {
					switch (id) {
						case R.id.itemInputFitToScreen:
							inputModeHandlers[i] = new FitToScreenMode();
							break;
					}
				}
				return inputModeHandlers[i];
			}
		}
		return null;
	}

	AbstractInputHandler getInputHandlerByName(String name) {
		AbstractInputHandler result = null;
		for (int id : inputModeIds) {
			AbstractInputHandler handler = getInputHandlerById(id);
			if (handler.getName().equals(name)) {
				result = handler;
				break;
			}
		}
		if (result == null) {
			result = getInputHandlerById(R.id.itemInputFitToScreen);
		}
		return result;
	}
	
	int getModeIdFromHandler(AbstractInputHandler handler) {
		for (int id : inputModeIds) {
			if (handler == getInputHandlerById(id))
				return id;
		}
		return R.id.itemInputFitToScreen;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		vncCanvas.afterMenu = true;
		switch (item.getItemId()) {
		case R.id.itemInfo:
			vncCanvas.showConnectionInfo();
			return true;
		case R.id.itemColorMode:
			selectColorModel();
			return true;
			// Following sets one of the scaling options
		case R.id.itemZoomable:
		case R.id.itemOneToOne:
		case R.id.itemFitToScreen:
			AbstractScaling.getById(item.getItemId()).setScaleTypeForActivity(
					this);
			item.setChecked(true);
			showPanningState();
			return true;
		case R.id.itemCenterMouse:
			vncCanvas.warpMouse(vncCanvas.absoluteXPosition
					+ vncCanvas.getVisibleWidth() / 2,
					vncCanvas.absoluteYPosition + vncCanvas.getVisibleHeight()
							/ 2);
			return true;
		case R.id.itemDisconnect:
			vncCanvas.closeConnection();
			finish();
			return true;
		case R.id.itemFollowMouse:
			boolean newFollow = !settings.getFollowMouse();
			item.setChecked(newFollow);
			settings.setFollowMouse(newFollow);
			if (newFollow) {
				vncCanvas.panToMouse();
			}
			settings.save();
			return true;
		case R.id.itemFollowPan:
			boolean newFollowPan = !settings.getFollowPan();
			item.setChecked(newFollowPan);
			settings.setFollowPan(newFollowPan);
			settings.save();
			return true;
		case R.id.itemOpenDoc:
			Utils.showDocumentation(this);
			return true;
		default:
			AbstractInputHandler input = getInputHandlerById(item.getItemId());
			if (input != null) {
				inputHandler = input;
				settings.setInputMode(input.getName());
				if (input.getName().equals(TOUCHPAD_MODE))
					settings.setFollowMouse(true);
				item.setChecked(true);
				showPanningState();
				settings.save();
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (isFinishing()) {
			vncCanvas.closeConnection();
			vncCanvas.onDestroy();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent evt) {
		if (keyCode == KeyEvent.KEYCODE_MENU)
			return super.onKeyDown(keyCode, evt);

		return inputHandler.onKeyDown(keyCode, evt);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent evt) {
		if (keyCode == KeyEvent.KEYCODE_MENU)
			return super.onKeyUp(keyCode, evt);

		return inputHandler.onKeyUp(keyCode, evt);
	}

	public void showPanningState() {
		Snackbar.make(vncCanvas, inputHandler.getHandlerDescription(), Snackbar.LENGTH_SHORT).show();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onTrackballEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			trackballButtonDown = true;
			break;
		case MotionEvent.ACTION_UP:
			trackballButtonDown = false;
			break;
		}
		return inputHandler.onTrackballEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return inputHandler.onTouchEvent(event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		return inputHandler.onGenericMotionEvent(event);
	}

	private void selectColorModel() {
		// Stop repainting the desktop
		// because the display is composited!
		vncCanvas.disableRepaints();

		String[] choices = new String[COLORMODEL.values().length];
		int currentSelection = -1;
		for (int i = 0; i < choices.length; i++) {
			COLORMODEL cm = COLORMODEL.values()[i];
			choices[i] = cm.toString();
			if (vncCanvas.isColorModel(cm))
				currentSelection = i;
		}

		final Dialog dialog = new Dialog(this);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		ListView list = new ListView(this);
		list.setAdapter(new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_checked, choices));
		list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		list.setItemChecked(currentSelection, true);
		list.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				dialog.dismiss();
				COLORMODEL cm = COLORMODEL.values()[arg2];
				vncCanvas.setColorModel(cm);
				settings.setColorModel(cm.getId());
				settings.save();
				Snackbar.make(vncCanvas, "Updating Color Model to " + cm.toString(), Snackbar.LENGTH_SHORT).show();
			}
		});
		dialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface arg0) {
				Log.i(TAG, "Color Model Selector dismissed");
				// Restore desktop repaints
				vncCanvas.enableRepaints();
			}
		});
		dialog.setContentView(list);
		dialog.show();
	}

	boolean defaultKeyDownHandler(int keyCode, KeyEvent evt) {
		if (vncCanvas.processLocalKeyEvent(keyCode, evt))
			return true;
		return super.onKeyDown(keyCode, evt);
	}

	boolean defaultKeyUpHandler(int keyCode, KeyEvent evt) {
		if (vncCanvas.processLocalKeyEvent(keyCode, evt))
			return true;
		return super.onKeyUp(keyCode, evt);
	}

	boolean mouseMoveEvent(MotionEvent evt) {
		// evt.offsetLocation(-(evt.getX() / vncCanvas.scaling.getScale()), -(evt.getY() / vncCanvas.scaling.getScale()));
		return vncCanvas.processPointerEvent(evt, evt.getButtonState() == MotionEvent.BUTTON_PRIMARY);
	}

	boolean mouseClickEvent(MotionEvent evt) {
		evt.offsetLocation(-(evt.getX() / vncCanvas.scaling.getScale()), -(evt.getY() / vncCanvas.scaling.getScale()));
		return vncCanvas.processPointerEvent((int)evt.getX(),(int)evt.getY(),
				MotionEvent.ACTION_DOWN, evt.getMetaState(),
				true, true);
	}


	static final String FIT_SCREEN_NAME = "FIT_SCREEN";
	/** Internal name for default input mode with Zoom scaling */
	static final String TOUCH_ZOOM_MODE = "TOUCH_ZOOM_MODE";
	
	static final String TOUCHPAD_MODE = "TOUCHPAD_MODE";

	/**
	 * In fit-to-screen mode, no panning. Trackball and touchscreen work as
	 * mouse.
	 * 
	 * @author Michael A. MacDonald
	 * 
	 */
	public class FitToScreenMode extends GestureDetector.SimpleOnGestureListener implements AbstractInputHandler {
		private DPadMouseKeyHandler keyHandler = new DPadMouseKeyHandler(VncCanvasActivity.this, vncCanvas.handler);
		private final GestureDetector mGestureDetector = new GestureDetector(VncCanvasActivity.this, this, null, true);

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyDown(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent evt) {
			return defaultKeyDownHandler(keyCode, evt); // keyHandler.onKeyDown(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onKeyUp(int,
		 *      android.view.KeyEvent)
		 */
		@Override
		public boolean onKeyUp(int keyCode, KeyEvent evt) {
			return defaultKeyUpHandler(keyCode, evt); // keyHandler.onKeyUp(keyCode, evt);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTouchEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTouchEvent(MotionEvent evt) {
			// If the gesture detector did not handle the gesture, we do
			if(!mGestureDetector.onTouchEvent(evt)) {
				vncCanvas.changeTouchCoordinatesToFullFrame(evt);
				if (vncCanvas.processPointerEvent(evt, evt.getButtonState() == MotionEvent.BUTTON_PRIMARY))
					return true;

				return VncCanvasActivity.super.onTouchEvent(evt);
			}

			// If we end up here, the gesture detector handled it
			return true;
		}

		@Override
		public boolean onGenericMotionEvent(MotionEvent event) {
			if(!mGestureDetector.onGenericMotionEvent(event)) {
				int pointerCount = event.getPointerCount();
				if ((event.getSource() & InputDevice.SOURCE_MOUSE) != 0) {
					if (event.getButtonState() == MotionEvent.BUTTON_SECONDARY)
						return inputHandler.onMouseButton(event);
					switch (event.getAction()) {
						case MotionEvent.ACTION_HOVER_MOVE:
							if (pointerCount == 1)
								return inputHandler.onMouseMove(event);
							else if (pointerCount == 2)
								break; // TODO: Scrolling needs to happen here in some cases

						case MotionEvent.ACTION_SCROLL:
							if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f)
								vncCanvas.processScroll(vncCanvas.MOUSE_BUTTON_SCROLL_DOWN, false);
							else
								vncCanvas.processScroll(vncCanvas.MOUSE_BUTTON_SCROLL_UP, false);
					}
				}

				return VncCanvasActivity.super.onGenericMotionEvent(event);
			}

			// If we end up here, the gesture detector handled it
			return true;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#onTrackballEvent(android.view.MotionEvent)
		 */
		@Override
		public boolean onTrackballEvent(MotionEvent evt) {
			// Not using it any longer as we directly translate the mouse via move events
			// return trackballMouse(evt);
			return false;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#handlerDescription()
		 */
		@Override
		public CharSequence getHandlerDescription() {
			return getResources().getText(R.string.input_mode_fit_to_screen);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see android.androidVNC.AbstractInputHandler#getName()
		 */
		@Override
		public String getName() {
			return FIT_SCREEN_NAME;
		}

		@Override
		public boolean onMouseMove(MotionEvent e) {
			vncCanvas.changeTouchCoordinatesToFullFrame(e);
			return mouseMoveEvent(e);
		}

		@Override
		public boolean onMouseButton(MotionEvent event) {
			return mouseClickEvent(event);
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			if (e1.isFromSource(InputDevice.SOURCE_MOUSE) && e1.getButtonState() != MotionEvent.BUTTON_PRIMARY && e2.getButtonState() != MotionEvent.BUTTON_PRIMARY) {
				if (distanceY < 0.0f)
					vncCanvas.processScroll(vncCanvas.MOUSE_BUTTON_SCROLL_DOWN, true);
				else
					vncCanvas.processScroll(vncCanvas.MOUSE_BUTTON_SCROLL_UP, true);

				return true;
			}

			return false;
		}
	}
}
