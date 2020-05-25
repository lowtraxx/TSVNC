//
//  Copyright (C) 2010 Michael A. MacDonald
//  Copyright (C) 2004 Horizon Wimba.  All Rights Reserved.
//  Copyright (C) 2001-2003 HorizonLive.com, Inc.  All Rights Reserved.
//  Copyright (C) 2001,2002 Constantin Kaplinsky.  All Rights Reserved.
//  Copyright (C) 2000 Tridia Corporation.  All Rights Reserved.
//  Copyright (C) 1999 AT&T Laboratories Cambridge.  All Rights Reserved.
//
//  This is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 2 of the License, or
//  (at your option) any later version.
//
//  This software is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this software; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
//  USA.
//

//
// VncCanvas is a subclass of android.view.SurfaceView which draws a VNC
// desktop on it.
//

package de.toshsoft;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Handler;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.antlersoft.android.bc.BCFactory;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.zip.Inflater;


public class VncCanvas extends androidx.appcompat.widget.AppCompatImageView {
	private final static String TAG = "VncCanvas";
	private final static boolean LOCAL_LOGV = true;
	private PowerManager.WakeLock wakeLock;
	
	AbstractScaling scaling;
	
	// Available to activity
	int mouseX, mouseY;
	
	// Connection parameters
	VncSettings settings;

	// Runtime control flags
	private boolean maintainConnection = true;
	private boolean showDesktopInfo = true;
	private boolean repaintsEnabled = true;
	
	/**
	 * Use camera button as meta key for right mouse button
	 */
	boolean cameraButtonDown = false;
	
	// Keep track when a seeming key press was the result of a menu shortcut
	int lastKeyDown;
	boolean afterMenu;

	// Color Model settings
	private COLORMODEL pendingColorModel = COLORMODEL.C24bit;
	private COLORMODEL colorModel = null;
	private int bytesPerPixel = 0;
	private int[] colorPalette = null;

	// VNC protocol connection
	public RfbProto rfb;

	// Display Width and Height
	public float displayWidth;
	public float displayHeight;

	// Internal bitmap data
	AbstractBitmapData bitmapData;
	public Handler handler = new Handler();

	// VNC Encoding parameters
	private boolean useCopyRect = false; // TODO CopyRect is not working
	private int preferredEncoding = -1;

	// Unimplemented VNC encoding parameters
	private boolean requestCursorUpdates = false;
	private boolean ignoreCursorUpdates = true;

	// Unimplemented TIGHT encoding parameters
	private int compressLevel = -1;
	private int jpegQuality = -1;

	// Used to determine if encoding update is necessary
	private int[] encodingsSaved = new int[20];
	private int nEncodingsSaved = 0;

	// ZRLE encoder's data.
	private byte[] zrleBuf;
	private int[] zrleTilePixels;
	private ZlibInStream zrleInStream;

	// Zlib encoder's data.
	private byte[] zlibBuf;
	private Inflater zlibInflater;
	private MouseScrollRunnable scrollRunnable;
	
	private Paint handleRREPaint;
	
	/**
	 * Position of the top left portion of the <i>visible</i> part of the screen, in
	 * full-frame coordinates
	 */
	int absoluteXPosition = 0, absoluteYPosition = 0;

	/**
	 * Constructor used by the inflation apparatus
	 * @param context
	 */
	public VncCanvas(final Context context, AttributeSet attrs)
	{
		super(context, attrs);
		scrollRunnable = new MouseScrollRunnable();
		handleRREPaint = new Paint();
		handleRREPaint.setStyle(Style.FILL);
	}

	/**
	 * Create a view showing a VNC connection
	 * @param settings Connection settings
	 * @param setModes Callback to run on UI thread after connection is set up
	 */
	void initializeVncCanvas(VncSettings settings, final Runnable setModes) {
		this.settings = settings;
		if (this.settings.getUseWakeLock())
		{
			if (wakeLock != null && wakeLock.isHeld()) {
				wakeLock.release();
			}
			wakeLock = ((PowerManager)getContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "android.androidVNC");
		}
		this.pendingColorModel = COLORMODEL.getModelForId(settings.getColorModel());

		// Startup the RFB thread with a nifty progess dialog
		final ProgressDialog pd = ProgressDialog.show(getContext(), "Connecting...", "Establishing handshake.\nPlease wait...", true, true, new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				closeConnection();
				handler.post(new Runnable() {
					public void run() {
						Utils.showErrorMessage(getContext(), "VNC connection aborted!");
					}
				});
			}
		});
		final Display display = pd.getWindow().getWindowManager().getDefaultDisplay();
		DisplayMetrics displayMetrics = new DisplayMetrics();
		display.getRealMetrics(displayMetrics);
		displayWidth = displayMetrics.widthPixels;
		displayHeight = displayMetrics.heightPixels;
		Thread t = new Thread() {
			public void run() {
				try {
					connectAndAuthenticate(VncCanvas.this.settings.getUserName(), VncCanvas.this.settings.getPassword());
					doProtocolInitialisation((int)displayWidth, (int)displayHeight);
					handler.post(new Runnable() {
						public void run() {
							pd.setMessage("Downloading first frame.\nPlease wait...");
						}
					});
					processNormalProtocol(getContext(), pd, setModes);
				} catch (Throwable e) {
					if (maintainConnection) {
						Log.e(TAG, e.toString());
						e.printStackTrace();
						// Ensure we dismiss the progress dialog
						// before we fatal error finish
						if (pd.isShowing())
							pd.dismiss();
						if (e instanceof OutOfMemoryError) {
							// TODO  Not sure if this will happen but...
							// figure out how to gracefully notify the user
							// Instantiating an alert dialog here doesn't work
							// because we are out of memory. :(
						} else {
							String error = "VNC connection failed!";
							if (e.getMessage() != null && (e.getMessage().indexOf("authentication") > -1)) {
								error = "VNC authentication failed!";
 							}
							final String error_ = error + "<br>" + e.getLocalizedMessage();
							handler.post(new Runnable() {
								public void run() {
									Utils.showFatalErrorMessage(getContext(), error_);
								}
							});
						}
					}
				}
			}
		};
		t.start();
	}

	void connectAndAuthenticate(String us,String pw) throws Exception {
		Log.i(TAG, "Connecting to " + settings.getAddress() + ", port " + settings.getPort() + "...");

		rfb = new RfbProto(settings.getAddress(), settings.getPort());
		if (LOCAL_LOGV) Log.v(TAG, "Connected to server");

		rfb.readVersionMsg();
		Log.i(TAG, "RFB server supports protocol version " + rfb.serverMajor + "." + rfb.serverMinor);

		rfb.writeVersionMsg();
		Log.i(TAG, "Using RFB protocol version " + rfb.clientMajor + "." + rfb.clientMinor);

		int bitPref=0;
		if(settings.getUserName().length()>0)
		  bitPref|=1;
		Log.d("debug","bitPref="+bitPref);
		int secType = rfb.negotiateSecurity(bitPref);
		int authType;
		if (secType == RfbProto.SecTypeTight) {
			rfb.initCapabilities();
			rfb.setupTunneling();
			authType = rfb.negotiateAuthenticationTight();
		} else if (secType == RfbProto.SecTypeUltra34) {
			rfb.prepareDH();
			authType = RfbProto.AuthUltra;
		} else {
			authType = secType;
		}

		switch (authType) {
		case RfbProto.AuthNone:
			Log.i(TAG, "No authentication needed");
			rfb.authenticateNone();
			break;
		case RfbProto.AuthVNC:
			Log.i(TAG, "VNC authentication needed");
			rfb.authenticateVNC(pw);
			break;
		case RfbProto.AuthUltra:
			rfb.authenticateDH(us,pw);
			break;
		default:
			throw new Exception("Unknown authentication scheme " + authType);
		}
	}

	void doProtocolInitialisation(int dx, int dy) throws IOException {
		rfb.writeClientInit();
		rfb.readServerInit();

		Log.i(TAG, "Desktop name is " + rfb.desktopName);
		Log.i(TAG, "Desktop size is " + rfb.framebufferWidth + " x " + rfb.framebufferHeight);

		boolean useFull = false;
		int capacity = BCFactory.getInstance().getBCActivityManager().getMemoryClass(Utils.getActivityManager(getContext()));
		if (settings.getForceFull() == BitmapImplHint.AUTO)
		{
			if (rfb.framebufferWidth * rfb.framebufferHeight * FullBufferBitmapData.CAPACITY_MULTIPLIER <= capacity * 1024 * 1024)
				useFull = true;
		}
		else
			useFull = (settings.getForceFull() == BitmapImplHint.FULL);
		if (! useFull)
			bitmapData=new LargeBitmapData(rfb,this,dx,dy,capacity);
		else
			bitmapData=new FullBufferBitmapData(rfb,this, capacity);
		mouseX=rfb.framebufferWidth/2;
		mouseY=rfb.framebufferHeight/2;

		setPixelFormat();
	}

	private void setPixelFormat() throws IOException {
		pendingColorModel.setPixelFormat(rfb);
		bytesPerPixel = pendingColorModel.bpp();
		colorPalette = pendingColorModel.palette();
		colorModel = pendingColorModel;
		pendingColorModel = null;
	}

	public void setColorModel(COLORMODEL cm) {
		// Only update if color model changes
		if (colorModel == null || !colorModel.equals(cm))
			pendingColorModel = cm;
	}

	public boolean isColorModel(COLORMODEL cm) {
		return (colorModel != null) && colorModel.equals(cm);
	}
	
	private void mouseFollowPan()
	{
		if (settings.getFollowPan() && scaling.isAbleToPan())
		{
			int scrollx = absoluteXPosition;
			int scrolly = absoluteYPosition;
			int width = getVisibleWidth();
			int height = getVisibleHeight();
			//Log.i(TAG,"scrollx " + scrollx + " scrolly " + scrolly + " mouseX " + mouseX +" Y " + mouseY + " w " + width + " h " + height);
			if (mouseX < scrollx || mouseX >= scrollx + width || mouseY < scrolly || mouseY >= scrolly + height)
			{
				//Log.i(TAG,"warp to " + scrollx+width/2 + "," + scrolly + height/2);
				warpMouse(scrollx + width/2, scrolly + height / 2);
			}
		}
	}

	public void processNormalProtocol(final Context context, ProgressDialog pd, final Runnable setModes) throws Exception {
		if (wakeLock != null && settings.getUseWakeLock()) {
			wakeLock.acquire();
		}
		try {
			bitmapData.writeFullUpdateRequest(false);

			handler.post(setModes);
			//
			// main_old dispatch loop
			//
			while (maintainConnection) {
				bitmapData.syncScroll();
				// Read message type from the server.
				int msgType = rfb.readServerMessageType();
				bitmapData.doneWaiting();
				// Process the message depending on its type.
				switch (msgType) {
				case RfbProto.FramebufferUpdate:
					rfb.readFramebufferUpdate();

					for (int i = 0; i < rfb.updateNRects; i++) {
						rfb.readFramebufferUpdateRectHdr();
						int rx = rfb.updateRectX, ry = rfb.updateRectY;
						int rw = rfb.updateRectW, rh = rfb.updateRectH;

						if (rfb.updateRectEncoding == RfbProto.EncodingLastRect) {
							Log.v(TAG, "rfb.EncodingLastRect");
							break;
						}

						if (rfb.updateRectEncoding == RfbProto.EncodingNewFBSize) {
							rfb.setFramebufferSize(rw, rh);
							// - updateFramebufferSize();
							Log.v(TAG, "rfb.EncodingNewFBSize");
							break;
						}

						if (rfb.updateRectEncoding == RfbProto.EncodingXCursor || rfb.updateRectEncoding == RfbProto.EncodingRichCursor) {
							// - handleCursorShapeUpdate(rfb.updateRectEncoding,
							// rx,
							// ry, rw, rh);
							Log.v(TAG, "rfb.EncodingCursor");
							continue;

						}

						if (rfb.updateRectEncoding == RfbProto.EncodingPointerPos) {
							// This never actually happens
							mouseX=rx;
							mouseY=ry;
							Log.v(TAG, "rfb.EncodingPointerPos");
							continue;
						}

						rfb.startTiming();

						switch (rfb.updateRectEncoding) {
						case RfbProto.EncodingRaw:
							handleRawRect(rx, ry, rw, rh);
							break;
						case RfbProto.EncodingCopyRect:
							handleCopyRect(rx, ry, rw, rh);
							Log.v(TAG, "CopyRect is Buggy!");
							break;
						case RfbProto.EncodingRRE:
							handleRRERect(rx, ry, rw, rh);
							break;
						case RfbProto.EncodingCoRRE:
							handleCoRRERect(rx, ry, rw, rh);
							break;
						case RfbProto.EncodingHextile:
							handleHextileRect(rx, ry, rw, rh);
							break;
						case RfbProto.EncodingZRLE:
							handleZRLERect(rx, ry, rw, rh);
							break;
						case RfbProto.EncodingZlib:
							handleZlibRect(rx, ry, rw, rh);
							break;
						default:
							Log.e(TAG, "Unknown RFB rectangle encoding " + rfb.updateRectEncoding + " (0x" + Integer.toHexString(rfb.updateRectEncoding) + ")");
						}

						rfb.stopTiming();

						// Hide progress dialog
						if (pd.isShowing())
							pd.dismiss();
					}

					boolean fullUpdateNeeded = false;

					if (pendingColorModel != null) {
						setPixelFormat();
						fullUpdateNeeded = true;
					}

					setEncodings(true);
					bitmapData.writeFullUpdateRequest(!fullUpdateNeeded);

					break;

				case RfbProto.SetColourMapEntries:
					throw new Exception("Can't handle SetColourMapEntries message");

				case RfbProto.Bell:
					handler.post( new Runnable() {
						public void run() { /* TODO: Bell */ }
					});
					break;

				case RfbProto.ServerCutText:
					String s = rfb.readServerCutText();
					if (s != null && s.length() > 0) {
						// TODO implement cut & paste
					}
					break;

				case RfbProto.TextChat:
					// UltraVNC extension
					String msg = rfb.readTextChatMsg();
					if (msg != null && msg.length() > 0) {
						// TODO implement chat interface
					}
					break;

				default:
					throw new Exception("Unknown RFB message type " + msgType);
				}
			}
		} catch (Exception e) {
			throw e;
		} finally {
			Log.v(TAG, "Closing VNC Connection");
			rfb.close();
			if (wakeLock != null && wakeLock.isHeld()) {
				wakeLock.release();
			}
			wakeLock = null;
		}
	}
	
	/**
	 * Apply scroll offset and scaling to convert touch-space coordinates to the corresponding
	 * point on the full frame.
	 * @param e MotionEvent with the original, touch space coordinates.  This event is altered in place.
	 * @return e -- The same event passed in, with the coordinates mapped
	 */
	MotionEvent changeTouchCoordinatesToFullFrame(MotionEvent e)
	{
		//Log.v(TAG, String.format("tap at %f,%f", e.getX(), e.getY()));
		float scale = getScale();
		absoluteYPosition = -(int) ((getHeight() - (getImageHeight() * scale)) / 2.0f);
		absoluteXPosition = -(int) ((getWidth() - (getImageWidth() * scale)) / 2.0f);

		// Adjust coordinates for Android notification bar.
		e.offsetLocation(0, -1f * getTop());
		e.setLocation(absoluteXPosition + e.getX() / scale, absoluteYPosition + e.getY() / scale);
		
		return e;
	}

	public void onDestroy() {
		Log.v(TAG, "Cleaning up resources");
		if ( bitmapData!=null) bitmapData.dispose();
		bitmapData = null;
	}
	
	/**
	 * Warp the mouse to x, y in the RFB coordinates
	 * @param x
	 * @param y
	 */
	void warpMouse(int x, int y)
	{
		bitmapData.invalidateMousePosition();
		mouseX=x;
		mouseY=y;
		bitmapData.invalidateMousePosition();
		try
		{
			rfb.writePointerEvent(x, y, 0, MOUSE_BUTTON_NONE);
		}
		catch ( IOException ioe)
		{
			Log.w(TAG,ioe);
		}
	}

	/*
	 * f(x,s) is a function that returns the coordinate in screen/scroll space corresponding
	 * to the coordinate x in full-frame space with scaling s.
	 * 
	 * This function returns the difference between f(x,s1) and f(x,s2)
	 * 
	 * f(x,s) = (x - i/2) * s + ((i - w)/2)) * s
	 *        = s (x - i/2 + i/2 + w/2)
	 *        = s (x + w/2)
	 * 
	 * 
	 * f(x,s) = (x - ((i - w)/2)) * s
	 * @param oldscaling
	 * @param scaling
	 * @param imageDim
	 * @param windowDim
	 * @param offset
	 * @return
	 */
	
	/**
	 * Change to Canvas's scroll position to match the absoluteXPosition
	 */
	void scrollToAbsolute()
	{
		float scale = getScale();
		scrollTo((int)((absoluteXPosition + ((float)getWidth() - getImageWidth()) / 2 ) * scale),
				(int)((absoluteYPosition + ((float)getHeight() - getImageHeight()) / 2 ) * scale));
	}

	/**
	 * Make sure mouse is visible on displayable part of screen
	 */
	void panToMouse()
	{
		if (! settings.getFollowMouse())
			return;
		
		if (scaling != null && ! scaling.isAbleToPan())
			return;
			
		int x = mouseX;
		int y = mouseY;
		boolean panned = false;
		int w = getVisibleWidth();
		int h = getVisibleHeight();
		int iw = getImageWidth();
		int ih = getImageHeight();
		
		int newX = absoluteXPosition;
		int newY = absoluteYPosition;
		
		if (x - newX >= w - 5)
		{
			newX = x - w + 5;
			if (newX + w > iw)
				newX = iw - w;
		}
		else if (x < newX + 5)
		{
			newX = x - 5;
			if (newX < 0)
				newX = 0;
		}
		if ( newX != absoluteXPosition ) {
			absoluteXPosition = newX;
			panned = true;
		}
		if (y - newY >= h - 5)
		{
			newY = y - h + 5;
			if (newY + h > ih)
				newY = ih - h;
		}
		else if (y < newY + 5)
		{
			newY = y - 5;
			if (newY < 0)
				newY = 0;
		}
		if ( newY != absoluteYPosition ) {
			absoluteYPosition = newY;
			panned = true;
		}
		if (panned)
		{
			scrollToAbsolute();
		}		
	}
	
	/**
	 * Pan by a number of pixels (relative pan)
	 * @param dX
	 * @param dY
	 * @return True if the pan changed the view (did not move view out of bounds); false otherwise
	 */
	boolean pan(int dX, int dY) {
		
		double scale = getScale();
		
		double sX = (double)dX / scale;
		double sY = (double)dY / scale;
		
		if (absoluteXPosition + sX < 0)
			// dX = diff to 0
			sX = -absoluteXPosition;
		if (absoluteYPosition + sY < 0)
			sY = -absoluteYPosition;

		// Prevent panning right or below desktop image
		if (absoluteXPosition + getVisibleWidth() + sX > getImageWidth())
			sX = getImageWidth() - getVisibleWidth() - absoluteXPosition;
		if (absoluteYPosition + getVisibleHeight() + sY > getImageHeight())
			sY = getImageHeight() - getVisibleHeight() - absoluteYPosition;

		absoluteXPosition += sX;
		absoluteYPosition += sY;
		if (sX != 0.0 || sY != 0.0)
		{
			scrollToAbsolute();
			return true;
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see android.view.View#onScrollChanged(int, int, int, int)
	 */
	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		bitmapData.scrollChanged(absoluteXPosition, absoluteYPosition);
		mouseFollowPan();
	}

	void handleRawRect(int x, int y, int w, int h) throws IOException {
		handleRawRect(x, y, w, h, true);
	}

	byte[] handleRawRectBuffer = new byte[128];
	void handleRawRect(int x, int y, int w, int h, boolean paint) throws IOException {
		boolean valid=bitmapData.validDraw(x, y, w, h);
		int[] pixels=bitmapData.bitmapPixels;
		if (bytesPerPixel == 1) {
			// 1 byte per pixel. Use palette lookup table.
		  if (w > handleRawRectBuffer.length) {
			  handleRawRectBuffer = new byte[w];
		  }
			int i, offset;
			for (int dy = y; dy < y + h; dy++) {
				rfb.readFully(handleRawRectBuffer, 0, w);
				if ( ! valid)
					continue;
				offset = bitmapData.offset(x, dy);
				for (i = 0; i < w; i++) {
					pixels[offset + i] = colorPalette[0xFF & handleRawRectBuffer[i]];
				}
			}
		} else {
			// 4 bytes per pixel (argb) 24-bit color
		  
			final int l = w * 4;
			if (l>handleRawRectBuffer.length) {
      handleRawRectBuffer = new byte[l];
			}
			int i, offset;
			for (int dy = y; dy < y + h; dy++) {
				rfb.readFully(handleRawRectBuffer, 0, l);
				if ( ! valid)
					continue;
				offset = bitmapData.offset(x, dy);
				for (i = 0; i < w; i++) {
				  final int idx = i*4;
					pixels[offset + i] = // 0xFF << 24 |
					(handleRawRectBuffer[idx + 2] & 0xff) << 16 | (handleRawRectBuffer[idx + 1] & 0xff) << 8 | (handleRawRectBuffer[idx] & 0xff);
				}
			}
		}
		
		if ( ! valid)
			return;

		bitmapData.updateBitmap( x, y, w, h);

		if (paint)
			reDraw();
	}

	private Runnable reDraw = new Runnable() {
		public void run() {
			if (showDesktopInfo) {
				// Show a Toast with the desktop info on first frame draw.
				showDesktopInfo = false;
				showConnectionInfo();
			}
			if (bitmapData != null)
				bitmapData.updateView(VncCanvas.this);
		}
	};
	
	private void reDraw() {
		if (repaintsEnabled)
			handler.post(reDraw);
	}
	
	public void disableRepaints() {
		repaintsEnabled = false;
	}

	public void enableRepaints() {
		repaintsEnabled = true;
	}

	public void showConnectionInfo() {
		String msg = rfb.desktopName;
		int idx = rfb.desktopName.indexOf("(");
		if (idx > -1) {
			// Breakup actual desktop name from IP addresses for improved
			// readability
			String dn = rfb.desktopName.substring(0, idx).trim();
			String ip = rfb.desktopName.substring(idx).trim();
			msg = dn + "\n" + ip;
		}
		msg += "\n" + rfb.framebufferWidth + "x" + rfb.framebufferHeight;
		String enc = getEncoding();
		// Encoding might not be set when we display this message
		if (enc != null && !enc.equals(""))
			msg += ", " + getEncoding() + " encoding, " + colorModel.toString();
		else
			msg += ", " + colorModel.toString();
		Snackbar.make(this, msg, Snackbar.LENGTH_SHORT).show();
	}

	private String getEncoding() {
		switch (preferredEncoding) {
		case RfbProto.EncodingRaw:
			return "RAW";
		case RfbProto.EncodingTight:
			return "TIGHT";
		case RfbProto.EncodingCoRRE:
			return "CoRRE";
		case RfbProto.EncodingHextile:
			return "HEXTILE";
		case RfbProto.EncodingRRE:
			return "RRE";
		case RfbProto.EncodingZlib:
			return "ZLIB";
		case RfbProto.EncodingZRLE:
			return "ZRLE";
		}
		return "";
	}

    // Useful shortcuts for modifier masks.

    final static int CTRL_MASK  = KeyEvent.META_SYM_ON;
    final static int SHIFT_MASK = KeyEvent.META_SHIFT_ON;
    final static int META_MASK  = 0;
    final static int ALT_MASK   = KeyEvent.META_ALT_ON;
    
	private static final int MOUSE_BUTTON_NONE = 0;
	static final int MOUSE_BUTTON_LEFT = 1;
	static final int MOUSE_BUTTON_MIDDLE = 2;
	static final int MOUSE_BUTTON_RIGHT = 4;
	static final int MOUSE_BUTTON_SCROLL_UP = 8;
	static final int MOUSE_BUTTON_SCROLL_DOWN = 16;
	
	/**
	 * Current state of "mouse" buttons
	 * Alt meta means use second mouse button
	 * 0 = none
	 * 1 = default button
	 * 2 = second button
	 */
	private int pointerMask = MOUSE_BUTTON_NONE;
	
	/**
	 * Convert a motion event to a format suitable for sending over the wire
	 * @param evt motion event; x and y must already have been converted from screen coordinates
	 * to remote frame buffer coordinates.  cameraButton flag is interpreted as second mouse
	 * button
	 * @param downEvent True if "mouse button" (touch or trackball button) is down when this happens
	 * @return true if event was actually sent
	 */
	public boolean processPointerEvent(MotionEvent evt,boolean downEvent)
	{
		return processPointerEvent(evt,downEvent,cameraButtonDown);
	}
	
	/**
	 * Convert a motion event to a format suitable for sending over the wire
	 * @param evt motion event; x and y must already have been converted from screen coordinates
	 * to remote frame buffer coordinates.
	 * @param downEvent True if "mouse button" (touch or trackball button) is down when this happens
	 * @param useRightButton If true, event is interpreted as happening with right mouse button
	 * @return true if event was actually sent
	 */
	public boolean processPointerEvent(MotionEvent evt,boolean downEvent,boolean useRightButton) {
		return processPointerEvent((int)evt.getX(),(int)evt.getY(), evt.getAction(), evt.getMetaState(), downEvent, useRightButton);
	}
	
	boolean processPointerEvent(int x, int y, int action, int modifiers, boolean mouseIsDown, boolean useRightButton) {
		if (rfb != null && rfb.inNormalProtocol) {
		    if (action == MotionEvent.ACTION_DOWN || (mouseIsDown && action == MotionEvent.ACTION_MOVE)) {
		      if (useRightButton) {
		        pointerMask = MOUSE_BUTTON_RIGHT;
		      } else {
		        pointerMask = MOUSE_BUTTON_LEFT;
		      }
		    } else if (action == MotionEvent.ACTION_UP) {
		      pointerMask = 0;
		    }
		    bitmapData.invalidateMousePosition();
		    mouseX= x;
		    mouseY= y;
		    if ( mouseX<0) mouseX=0;
		    else if ( mouseX>=rfb.framebufferWidth) mouseX=rfb.framebufferWidth-1;
		    if ( mouseY<0) mouseY=0;
		    else if ( mouseY>=rfb.framebufferHeight) mouseY=rfb.framebufferHeight-1;
		    bitmapData.invalidateMousePosition();
			try {
				rfb.writePointerEvent(mouseX,mouseY,modifiers,pointerMask);
			} catch (Exception e) {
				e.printStackTrace();
			}
		    panToMouse();
			return true;
		}
		return false;		
	}
	
	/**
	 * Moves the scroll while the volume key is held down
	 * @author Michael A. MacDonald
	 */
	class MouseScrollRunnable implements Runnable
	{
		int delay = 100;
		
		int scrollButton = 0;
		
		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() {
			try
			{
				rfb.writePointerEvent(mouseX, mouseY, 0, scrollButton);
				rfb.writePointerEvent(mouseX, mouseY, 0, 0);
				
				handler.postDelayed(this, delay);
			}
			catch (IOException ioe)
			{
				
			}
		}		
	}

	public boolean processLocalKeyEvent(int keyCode, KeyEvent evt) {
		if (keyCode == KeyEvent.KEYCODE_MENU)
			// Ignore menu key
			return true;

		// Ignore the Back Button, as it could
		// also be fired by a Mouse Button Press
		if(keyCode == KeyEvent.KEYCODE_BACK)
			return true;

		if (keyCode == KeyEvent.KEYCODE_CAMERA)
		{
			cameraButtonDown = (evt.getAction() != KeyEvent.ACTION_UP);
		}
		else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
		{
			int mouseChange = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? MOUSE_BUTTON_SCROLL_DOWN : MOUSE_BUTTON_SCROLL_UP;
			if (evt.getAction() == KeyEvent.ACTION_DOWN)
			{
				// If not auto-repeat
				if (scrollRunnable.scrollButton != mouseChange)
				{
					pointerMask |= mouseChange;
					scrollRunnable.scrollButton = mouseChange;
					handler.postDelayed(scrollRunnable,200);
				}
			}
			else
			{
				handler.removeCallbacks(scrollRunnable);
				scrollRunnable.scrollButton = 0;
				pointerMask &= ~mouseChange;
			}
			try
			{
				rfb.writePointerEvent(mouseX, mouseY, evt.getMetaState(), pointerMask);
			}
			catch (IOException ioe)
			{
				// TODO: do something with exception
			}
			return true;
		}
		if (rfb != null && rfb.inNormalProtocol) {
		   boolean down = (evt.getAction() == KeyEvent.ACTION_DOWN);
		   int key;
		   int metaState = evt.getMetaState();
		   
		   switch(keyCode) {
		   	  case KeyEvent.KEYCODE_CTRL_LEFT :   key = 0xffe3; break;
		   	  case KeyEvent.KEYCODE_ALT_LEFT : 	  key = 0xffe9; break;
		   	  case KeyEvent.KEYCODE_ESCAPE :
		      case KeyEvent.KEYCODE_DPAD_LEFT:    key = 0xff51; break;
		   	  case KeyEvent.KEYCODE_DPAD_UP:      key = 0xff52; break;
		   	  case KeyEvent.KEYCODE_DPAD_RIGHT:   key = 0xff53; break;
		   	  case KeyEvent.KEYCODE_DPAD_DOWN:    key = 0xff54; break;
		   	  case KeyEvent.KEYCODE_MOVE_END:     key = 0xff57; break;
		   	  case KeyEvent.KEYCODE_MOVE_HOME:    key = 0xff50; break;
		   	  case KeyEvent.KEYCODE_PAGE_DOWN:    key = 0xff56; break;
		   	  case KeyEvent.KEYCODE_PAGE_UP:      key = 0xff55; break;
		      case KeyEvent.KEYCODE_DEL: 		  key = 0xff08; break;
		      case KeyEvent.KEYCODE_ENTER:        key = 0xff0d; break;
		      case KeyEvent.KEYCODE_DPAD_CENTER:  key = 0xff0d; break;
		      case KeyEvent.KEYCODE_FORWARD_DEL:  key = 0xffff; break;
		      case KeyEvent.KEYCODE_TAB:          key = 0xff09; break;
		      case KeyEvent.KEYCODE_F1:
		      case KeyEvent.KEYCODE_F2:
		      case KeyEvent.KEYCODE_F3:
		      case KeyEvent.KEYCODE_F4:
		      case KeyEvent.KEYCODE_F5:
		      case KeyEvent.KEYCODE_F6:
		      case KeyEvent.KEYCODE_F7:
		      case KeyEvent.KEYCODE_F8:
		      case KeyEvent.KEYCODE_F9:
		      case KeyEvent.KEYCODE_F10:
		      case KeyEvent.KEYCODE_F11:
		      case KeyEvent.KEYCODE_F12:
		    	  key = keyCode - KeyEvent.KEYCODE_F1 + 0xffbe; break;
		      case KeyEvent.KEYCODE_ALT_RIGHT :
		      	  metaState = 0;
		      default: 							  
		    	  key = evt.getUnicodeChar();

		    	  // We have a Key Combo
		    	  if(key == 0 && metaState != 0) {
		    	  	switch(keyCode) {
						case KeyEvent.KEYCODE_A: key = 'a'; break;
						case KeyEvent.KEYCODE_B: key = 'b'; break;
						case KeyEvent.KEYCODE_C: key = 'c'; break;
						case KeyEvent.KEYCODE_D: key = 'd'; break;
						case KeyEvent.KEYCODE_E: key = 'e'; break;
						case KeyEvent.KEYCODE_F: key = 'f'; break;
						case KeyEvent.KEYCODE_G: key = 'g'; break;
						case KeyEvent.KEYCODE_H: key = 'h'; break;
						case KeyEvent.KEYCODE_I: key = 'i'; break;
						case KeyEvent.KEYCODE_J: key = 'j'; break;
						case KeyEvent.KEYCODE_K: key = 'k'; break;
						case KeyEvent.KEYCODE_L: key = 'l'; break;
						case KeyEvent.KEYCODE_M: key = 'm'; break;
						case KeyEvent.KEYCODE_N: key = 'n'; break;
						case KeyEvent.KEYCODE_O: key = 'o'; break;
						case KeyEvent.KEYCODE_P: key = 'p'; break;
						case KeyEvent.KEYCODE_Q: key = 'q'; break;
						case KeyEvent.KEYCODE_R: key = 'r'; break;
						case KeyEvent.KEYCODE_S: key = 's'; break;
						case KeyEvent.KEYCODE_T: key = 't'; break;
						case KeyEvent.KEYCODE_U: key = 'u'; break;
						case KeyEvent.KEYCODE_V: key = 'v'; break;
						case KeyEvent.KEYCODE_W: key = 'w'; break;
						case KeyEvent.KEYCODE_X: key = 'x'; break;
						case KeyEvent.KEYCODE_Y: key = 'y'; break;
						case KeyEvent.KEYCODE_Z: key = 'z'; break;
						default:
							break;
					}
				  } else {
		    	  	metaState = 0;
				  }
		    	  break;
		    }

	    	try {
	    		if (afterMenu)
	    		{
	    			afterMenu = false;
	    			if (!down && key != lastKeyDown)
	    				return true;
	    		}
	    		if (down)
	    			lastKeyDown = key;
	    		Log.i(TAG,"key = " + key + " metastate = " + metaState + " keycode = " + keyCode);
	    		rfb.writeKeyEvent(key, metaState, down);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	public void closeConnection() {
		maintainConnection = false;
	}
	
	float getScale()
	{
		if (scaling == null)
			return 1;
		return scaling.getScale();
	}
	
	public int getVisibleWidth() {
		return (int)((double)getWidth() / getScale() + 0.5);
	}

	public int getVisibleHeight() {
		return (int)((double)getHeight() / getScale() + 0.5);
	}

	public int getImageWidth() {
		return bitmapData.framebufferwidth;
	}

	public int getImageHeight() {
		return bitmapData.framebufferheight;
	}
	
	public int getCenteredXOffset() {
		int xoffset = (bitmapData.framebufferwidth - getWidth()) / 2;
		return xoffset;
	}

	public int getCenteredYOffset() {
		int yoffset = (bitmapData.framebufferheight - getHeight()) / 2;
		return yoffset;
	}

	/**
	 * Additional Encodings
	 * 
	 */

	private void setEncodings(boolean autoSelectOnly) {
		if (rfb == null || !rfb.inNormalProtocol)
			return;

		if (preferredEncoding == -1) {
			// Preferred format is ZRLE
			preferredEncoding = RfbProto.EncodingZRLE;
		} else {
			// Auto encoder selection is not enabled.
			if (autoSelectOnly)
				return;
		}

		int[] encodings = new int[20];
		int nEncodings = 0;

		encodings[nEncodings++] = preferredEncoding;
		if (useCopyRect)
			encodings[nEncodings++] = RfbProto.EncodingCopyRect;
		// if (preferredEncoding != RfbProto.EncodingTight)
		// encodings[nEncodings++] = RfbProto.EncodingTight;
		if (preferredEncoding != RfbProto.EncodingZRLE)
			encodings[nEncodings++] = RfbProto.EncodingZRLE;
		if (preferredEncoding != RfbProto.EncodingHextile)
			encodings[nEncodings++] = RfbProto.EncodingHextile;
		if (preferredEncoding != RfbProto.EncodingZlib)
			encodings[nEncodings++] = RfbProto.EncodingZlib;
		if (preferredEncoding != RfbProto.EncodingCoRRE)
			encodings[nEncodings++] = RfbProto.EncodingCoRRE;
		if (preferredEncoding != RfbProto.EncodingRRE)
			encodings[nEncodings++] = RfbProto.EncodingRRE;

		if (compressLevel >= 0 && compressLevel <= 9)
			encodings[nEncodings++] = RfbProto.EncodingCompressLevel0 + compressLevel;
		if (jpegQuality >= 0 && jpegQuality <= 9)
			encodings[nEncodings++] = RfbProto.EncodingQualityLevel0 + jpegQuality;

		if (requestCursorUpdates) {
			encodings[nEncodings++] = RfbProto.EncodingXCursor;
			encodings[nEncodings++] = RfbProto.EncodingRichCursor;
			if (!ignoreCursorUpdates)
				encodings[nEncodings++] = RfbProto.EncodingPointerPos;
		}

		encodings[nEncodings++] = RfbProto.EncodingLastRect;
		encodings[nEncodings++] = RfbProto.EncodingNewFBSize;

		boolean encodingsWereChanged = false;
		if (nEncodings != nEncodingsSaved) {
			encodingsWereChanged = true;
		} else {
			for (int i = 0; i < nEncodings; i++) {
				if (encodings[i] != encodingsSaved[i]) {
					encodingsWereChanged = true;
					break;
				}
			}
		}

		if (encodingsWereChanged) {
			try {
				rfb.writeSetEncodings(encodings, nEncodings);
			} catch (Exception e) {
				e.printStackTrace();
			}
			encodingsSaved = encodings;
			nEncodingsSaved = nEncodings;
		}
	}

	//
	// Handle a CopyRect rectangle.
	//

  final Paint handleCopyRectPaint = new Paint();
	private void handleCopyRect(int x, int y, int w, int h) throws IOException {

		/**
		 * This does not work properly yet.
		 */

		rfb.readCopyRect();
		if ( ! bitmapData.validDraw(x, y, w, h))
			return;
		// Source Coordinates
		int leftSrc = rfb.copyRectSrcX;
		int topSrc = rfb.copyRectSrcY;
		int rightSrc = topSrc + w;
		int bottomSrc = topSrc + h;

		// Change
		int dx = x - rfb.copyRectSrcX;
		int dy = y - rfb.copyRectSrcY;

		// Destination Coordinates
		int leftDest = leftSrc + dx;
		int topDest = topSrc + dy;
		int rightDest = rightSrc + dx;
		int bottomDest = bottomSrc + dy;

		bitmapData.copyRect(new Rect(leftSrc, topSrc, rightSrc, bottomSrc), new Rect(leftDest, topDest, rightDest, bottomDest), handleCopyRectPaint);

		reDraw();
	}
	byte[] bg_buf = new byte[4];
	byte[] rre_buf = new byte[128];
	//
	// Handle an RRE-encoded rectangle.
	//
	private void handleRRERect(int x, int y, int w, int h) throws IOException {
		boolean valid=bitmapData.validDraw(x, y, w, h);
		int nSubrects = rfb.is.readInt();

		rfb.readFully(bg_buf, 0, bytesPerPixel);
		int pixel;
		if (bytesPerPixel == 1) {
			pixel = colorPalette[0xFF & bg_buf[0]];
		} else {
			pixel = Color.rgb(bg_buf[2] & 0xFF, bg_buf[1] & 0xFF, bg_buf[0] & 0xFF);
		}
		handleRREPaint.setColor(pixel);
		if ( valid)
			bitmapData.drawRect(x, y, w, h, handleRREPaint);

		int len = nSubrects * (bytesPerPixel + 8);
		if (len > rre_buf.length)
			rre_buf = new byte[len];
		
		rfb.readFully(rre_buf, 0, len);
		if ( ! valid)
			return;

		int sx, sy, sw, sh;

		int i = 0;
		for (int j = 0; j < nSubrects; j++) {
			if (bytesPerPixel == 1) {
				pixel = colorPalette[0xFF & rre_buf[i++]];
			} else {
				pixel = Color.rgb(rre_buf[i + 2] & 0xFF, rre_buf[i + 1] & 0xFF, rre_buf[i] & 0xFF);
				i += 4;
			}
			sx = x + ((rre_buf[i] & 0xff) << 8) + (rre_buf[i+1] & 0xff); i+=2;
			sy = y + ((rre_buf[i] & 0xff) << 8) + (rre_buf[i+1] & 0xff); i+=2;
			sw = ((rre_buf[i] & 0xff) << 8) + (rre_buf[i+1] & 0xff); i+=2;
			sh = ((rre_buf[i] & 0xff) << 8) + (rre_buf[i+1] & 0xff); i+=2;

			handleRREPaint.setColor(pixel);
			bitmapData.drawRect(sx, sy, sw, sh, handleRREPaint);
		}

		reDraw();
	}

	//
	// Handle a CoRRE-encoded rectangle.
	//

	private void handleCoRRERect(int x, int y, int w, int h) throws IOException {
		boolean valid=bitmapData.validDraw(x, y, w, h);
		int nSubrects = rfb.is.readInt();

		rfb.readFully(bg_buf, 0, bytesPerPixel);
		int pixel;
		if (bytesPerPixel == 1) {
			pixel = colorPalette[0xFF & bg_buf[0]];
		} else {
			pixel = Color.rgb(bg_buf[2] & 0xFF, bg_buf[1] & 0xFF, bg_buf[0] & 0xFF);
		}
		handleRREPaint.setColor(pixel);
		if ( valid)
			bitmapData.drawRect(x, y, w, h, handleRREPaint);

		int len = nSubrects * (bytesPerPixel + 8);
		if (len > rre_buf.length)
			rre_buf = new byte[len];
		
		rfb.readFully(rre_buf, 0, len);
		if ( ! valid)
			return;

		int sx, sy, sw, sh;
		int i = 0;

		for (int j = 0; j < nSubrects; j++) {
			if (bytesPerPixel == 1) {
				pixel = colorPalette[0xFF & rre_buf[i++]];
			} else {
				pixel = Color.rgb(rre_buf[i + 2] & 0xFF, rre_buf[i + 1] & 0xFF, rre_buf[i] & 0xFF);
				i += 4;
			}
			sx = x + (rre_buf[i++] & 0xFF);
			sy = y + (rre_buf[i++] & 0xFF);
			sw = rre_buf[i++] & 0xFF;
			sh = rre_buf[i++] & 0xFF;

			handleRREPaint.setColor(pixel);
			bitmapData.drawRect(sx, sy, sw, sh, handleRREPaint);
		}

		reDraw();
	}

	//
	// Handle a Hextile-encoded rectangle.
	//

	// These colors should be kept between handleHextileSubrect() calls.
	private int hextile_bg, hextile_fg;

	private void handleHextileRect(int x, int y, int w, int h) throws IOException {

		hextile_bg = Color.BLACK;
		hextile_fg = Color.BLACK;

		for (int ty = y; ty < y + h; ty += 16) {
			int th = 16;
			if (y + h - ty < 16)
				th = y + h - ty;

			for (int tx = x; tx < x + w; tx += 16) {
				int tw = 16;
				if (x + w - tx < 16)
					tw = x + w - tx;

				handleHextileSubrect(tx, ty, tw, th);
			}

			// Finished with a row of tiles, now let's show it.
			reDraw();
		}
	}

	//
	// Handle one tile in the Hextile-encoded data.
	//

	Paint handleHextileSubrectPaint = new Paint();
	byte[] backgroundColorBuffer = new byte[4];
	private void handleHextileSubrect(int tx, int ty, int tw, int th) throws IOException {

		int subencoding = rfb.is.readUnsignedByte();

		// Is it a raw-encoded sub-rectangle?
		if ((subencoding & RfbProto.HextileRaw) != 0) {
			handleRawRect(tx, ty, tw, th, false);
			return;
		}

		boolean valid=bitmapData.validDraw(tx, ty, tw, th);
		// Read and draw the background if specified.
		if (bytesPerPixel > backgroundColorBuffer.length) {
		  throw new RuntimeException("impossible colordepth");
		}
		if ((subencoding & RfbProto.HextileBackgroundSpecified) != 0) {
			rfb.readFully(backgroundColorBuffer, 0, bytesPerPixel);
			if (bytesPerPixel == 1) {
				hextile_bg = colorPalette[0xFF & backgroundColorBuffer[0]];
			} else {
				hextile_bg = Color.rgb(backgroundColorBuffer[2] & 0xFF, backgroundColorBuffer[1] & 0xFF, backgroundColorBuffer[0] & 0xFF);
			}
		}
		handleHextileSubrectPaint.setColor(hextile_bg);
		handleHextileSubrectPaint.setStyle(Paint.Style.FILL);
		if ( valid )
			bitmapData.drawRect(tx, ty, tw, th, handleHextileSubrectPaint);

		// Read the foreground color if specified.
		if ((subencoding & RfbProto.HextileForegroundSpecified) != 0) {
			rfb.readFully(backgroundColorBuffer, 0, bytesPerPixel);
			if (bytesPerPixel == 1) {
				hextile_fg = colorPalette[0xFF & backgroundColorBuffer[0]];
			} else {
				hextile_fg = Color.rgb(backgroundColorBuffer[2] & 0xFF, backgroundColorBuffer[1] & 0xFF, backgroundColorBuffer[0] & 0xFF);
			}
		}

		// Done with this tile if there is no sub-rectangles.
		if ((subencoding & RfbProto.HextileAnySubrects) == 0)
			return;

		int nSubrects = rfb.is.readUnsignedByte();
		int bufsize = nSubrects * 2;
		if ((subencoding & RfbProto.HextileSubrectsColoured) != 0) {
			bufsize += nSubrects * bytesPerPixel;
		}
		if (rre_buf.length < bufsize)
			rre_buf = new byte[bufsize];
		rfb.readFully(rre_buf, 0, bufsize);

		int b1, b2, sx, sy, sw, sh;
		int i = 0;
		if ((subencoding & RfbProto.HextileSubrectsColoured) == 0) {

			// Sub-rectangles are all of the same color.
			handleHextileSubrectPaint.setColor(hextile_fg);
			for (int j = 0; j < nSubrects; j++) {
				b1 = rre_buf[i++] & 0xFF;
				b2 = rre_buf[i++] & 0xFF;
				sx = tx + (b1 >> 4);
				sy = ty + (b1 & 0xf);
				sw = (b2 >> 4) + 1;
				sh = (b2 & 0xf) + 1;
				if ( valid)
					bitmapData.drawRect(sx, sy, sw, sh, handleHextileSubrectPaint);
			}
		} else if (bytesPerPixel == 1) {

			// BGR233 (8-bit color) version for colored sub-rectangles.
			for (int j = 0; j < nSubrects; j++) {
				hextile_fg = colorPalette[0xFF & rre_buf[i++]];
				b1 = rre_buf[i++] & 0xFF;
				b2 = rre_buf[i++] & 0xFF;
				sx = tx + (b1 >> 4);
				sy = ty + (b1 & 0xf);
				sw = (b2 >> 4) + 1;
				sh = (b2 & 0xf) + 1;
				handleHextileSubrectPaint.setColor(hextile_fg);
				if ( valid)
					bitmapData.drawRect(sx, sy, sw, sh, handleHextileSubrectPaint);
			}

		} else {

			// Full-color (24-bit) version for colored sub-rectangles.
			for (int j = 0; j < nSubrects; j++) {
				hextile_fg = Color.rgb(rre_buf[i + 2] & 0xFF, rre_buf[i + 1] & 0xFF, rre_buf[i] & 0xFF);
				i += 4;
				b1 = rre_buf[i++] & 0xFF;
				b2 = rre_buf[i++] & 0xFF;
				sx = tx + (b1 >> 4);
				sy = ty + (b1 & 0xf);
				sw = (b2 >> 4) + 1;
				sh = (b2 & 0xf) + 1;
				handleHextileSubrectPaint.setColor(hextile_fg);
				if ( valid )
					bitmapData.drawRect(sx, sy, sw, sh, handleHextileSubrectPaint);
			}

		}
	}

	//
	// Handle a ZRLE-encoded rectangle.
	//

  Paint handleZRLERectPaint = new Paint();
  int[] handleZRLERectPalette = new int[128];
	private void handleZRLERect(int x, int y, int w, int h) throws Exception {

		if (zrleInStream == null)
			zrleInStream = new ZlibInStream();

		int nBytes = rfb.is.readInt();
		if (nBytes > 64 * 1024 * 1024)
			throw new Exception("ZRLE decoder: illegal compressed data size");

		if (zrleBuf == null || zrleBuf.length < nBytes) {
			zrleBuf = new byte[nBytes+4096];
		}

		rfb.readFully(zrleBuf, 0, nBytes);

		zrleInStream.setUnderlying(new MemInStream(zrleBuf, 0, nBytes), nBytes);
		
		boolean valid=bitmapData.validDraw(x, y, w, h);

		for (int ty = y; ty < y + h; ty += 64) {

			int th = Math.min(y + h - ty, 64);

			for (int tx = x; tx < x + w; tx += 64) {

				int tw = Math.min(x + w - tx, 64);

				int mode = zrleInStream.readU8();
				boolean rle = (mode & 128) != 0;
				int palSize = mode & 127;

				readZrlePalette(handleZRLERectPalette, palSize);

				if (palSize == 1) {
					int pix = handleZRLERectPalette[0];
					int c = (bytesPerPixel == 1) ? colorPalette[0xFF & pix] : (0xFF000000 | pix);
					handleZRLERectPaint.setColor(c);
					handleZRLERectPaint.setStyle(Paint.Style.FILL);
					if ( valid)
						bitmapData.drawRect(tx, ty, tw, th, handleZRLERectPaint);
					continue;
				}

				if (!rle) {
					if (palSize == 0) {
						readZrleRawPixels(tw, th);
					} else {
						readZrlePackedPixels(tw, th, handleZRLERectPalette, palSize);
					}
				} else {
					if (palSize == 0) {
						readZrlePlainRLEPixels(tw, th);
					} else {
						readZrlePackedRLEPixels(tw, th, handleZRLERectPalette);
					}
				}
				if ( valid )
					handleUpdatedZrleTile(tx, ty, tw, th);
			}
		}

		zrleInStream.reset();

		reDraw();
	}

	//
	// Handle a Zlib-encoded rectangle.
	//

	byte[] handleZlibRectBuffer = new byte[128];
	private void handleZlibRect(int x, int y, int w, int h) throws Exception {
		boolean valid = bitmapData.validDraw(x, y, w, h);
		int nBytes = rfb.is.readInt();

		if (zlibBuf == null || zlibBuf.length < nBytes) {
			zlibBuf = new byte[nBytes*2];
		}

		rfb.readFully(zlibBuf, 0, nBytes);

		if (zlibInflater == null) {
			zlibInflater = new Inflater();
		}
		zlibInflater.setInput(zlibBuf, 0, nBytes);
		
		int[] pixels=bitmapData.bitmapPixels;

		if (bytesPerPixel == 1) {
			// 1 byte per pixel. Use palette lookup table.
		  if (w > handleZlibRectBuffer.length) {
		    handleZlibRectBuffer = new byte[w];
		  }
			int i, offset;
			for (int dy = y; dy < y + h; dy++) {
				zlibInflater.inflate(handleZlibRectBuffer,  0, w);
				if ( ! valid)
					continue;
				offset = bitmapData.offset(x, dy);
				for (i = 0; i < w; i++) {
					pixels[offset + i] = colorPalette[0xFF & handleZlibRectBuffer[i]];
				}
			}
		} else {
			// 24-bit color (ARGB) 4 bytes per pixel.
		  final int l = w*4;
		  if (l > handleZlibRectBuffer.length) {
			  handleZlibRectBuffer = new byte[l];
		  }
			int i, offset;
			for (int dy = y; dy < y + h; dy++) {
				zlibInflater.inflate(handleZlibRectBuffer, 0, l);
				if ( ! valid)
					continue;
				offset = bitmapData.offset(x, dy);
				for (i = 0; i < w; i++) {
				  final int idx = i*4;
					pixels[offset + i] = (handleZlibRectBuffer[idx + 2] & 0xFF) << 16 | (handleZlibRectBuffer[idx + 1] & 0xFF) << 8 | (handleZlibRectBuffer[idx] & 0xFF);
				}
			}
		}
		if ( ! valid)
			return;
		bitmapData.updateBitmap(x, y, w, h);

		reDraw();
	}

	private int readPixel(InStream is) throws Exception {
		int pix;
		if (bytesPerPixel == 1) {
			pix = is.readU8();
		} else {
			int p1 = is.readU8();
			int p2 = is.readU8();
			int p3 = is.readU8();
			pix = (p3 & 0xFF) << 16 | (p2 & 0xFF) << 8 | (p1 & 0xFF);
		}
		return pix;
	}

	byte[] readPixelsBuffer = new byte[128];
	private void readPixels(InStream is, int[] dst, int count) throws Exception {
		if (bytesPerPixel == 1) {
		  if (count > readPixelsBuffer.length) {
		    readPixelsBuffer = new byte[count];
		  }
			is.readBytes(readPixelsBuffer, 0, count);
			for (int i = 0; i < count; i++) {
				dst[i] = (int) readPixelsBuffer[i] & 0xFF;
			}
		} else {
		  final int l = count * 3;
      if (l > readPixelsBuffer.length) {
			readPixelsBuffer = new byte[l];
      }
			is.readBytes(readPixelsBuffer, 0, l);
			for (int i = 0; i < count; i++) {
			  final int idx = i*3;
				dst[i] = ((readPixelsBuffer[idx + 2] & 0xFF) << 16 | (readPixelsBuffer[idx + 1] & 0xFF) << 8 | (readPixelsBuffer[idx] & 0xFF));
			}
		}
	}

	private void readZrlePalette(int[] palette, int palSize) throws Exception {
		readPixels(zrleInStream, palette, palSize);
	}

	private void readZrleRawPixels(int tw, int th) throws Exception {
		int len = tw * th;
		if (zrleTilePixels == null || len > zrleTilePixels.length)
			zrleTilePixels = new int[len];
		readPixels(zrleInStream, zrleTilePixels, tw * th); // /
	}

	private void readZrlePackedPixels(int tw, int th, int[] palette, int palSize) throws Exception {

		int bppp = ((palSize > 16) ? 8 : ((palSize > 4) ? 4 : ((palSize > 2) ? 2 : 1)));
		int ptr = 0;
		int len = tw * th;
		if (zrleTilePixels == null || len > zrleTilePixels.length)
			zrleTilePixels = new int[len];

		for (int i = 0; i < th; i++) {
			int eol = ptr + tw;
			int b = 0;
			int nbits = 0;

			while (ptr < eol) {
				if (nbits == 0) {
					b = zrleInStream.readU8();
					nbits = 8;
				}
				nbits -= bppp;
				int index = (b >> nbits) & ((1 << bppp) - 1) & 127;
				if (bytesPerPixel == 1) {
					if (index >= colorPalette.length)
						Log.e(TAG, "zrlePlainRLEPixels palette lookup out of bounds " + index + " (0x" + Integer.toHexString(index) + ")");
					zrleTilePixels[ptr++] = colorPalette[0xFF & palette[index]];
				} else {
					zrleTilePixels[ptr++] = palette[index];
				}
			}
		}
	}

	private void readZrlePlainRLEPixels(int tw, int th) throws Exception {
		int ptr = 0;
		int end = ptr + tw * th;
		if (zrleTilePixels == null || end > zrleTilePixels.length)
			zrleTilePixels = new int[end];
		while (ptr < end) {
			int pix = readPixel(zrleInStream);
			int len = 1;
			int b;
			do {
				b = zrleInStream.readU8();
				len += b;
			} while (b == 255);

			if (!(len <= end - ptr))
				throw new Exception("ZRLE decoder: assertion failed" + " (len <= end-ptr)");

			if (bytesPerPixel == 1) {
				while (len-- > 0)
					zrleTilePixels[ptr++] = colorPalette[0xFF & pix];
			} else {
				while (len-- > 0)
					zrleTilePixels[ptr++] = pix;
			}
		}
	}

	private void readZrlePackedRLEPixels(int tw, int th, int[] palette) throws Exception {

		int ptr = 0;
		int end = ptr + tw * th;
		if (zrleTilePixels == null || end > zrleTilePixels.length)
			zrleTilePixels = new int[end];
		while (ptr < end) {
			int index = zrleInStream.readU8();
			int len = 1;
			if ((index & 128) != 0) {
				int b;
				do {
					b = zrleInStream.readU8();
					len += b;
				} while (b == 255);

				if (!(len <= end - ptr))
					throw new Exception("ZRLE decoder: assertion failed" + " (len <= end - ptr)");
			}

			index &= 127;
			int pix = palette[index];

			if (bytesPerPixel == 1) {
				while (len-- > 0)
					zrleTilePixels[ptr++] = colorPalette[0xFF & pix];
			} else {
				while (len-- > 0)
					zrleTilePixels[ptr++] = pix;
			}
		}
	}

	//
	// Copy pixels from zrleTilePixels8 or zrleTilePixels24, then update.
	//

	private void handleUpdatedZrleTile(int x, int y, int w, int h) {
		int offsetSrc = 0;
		int[] destPixels=bitmapData.bitmapPixels;
		for (int j = 0; j < h; j++) {
			System.arraycopy(zrleTilePixels, offsetSrc, destPixels, bitmapData.offset(x, y + j), w);
			offsetSrc += w;
		}

		bitmapData.updateBitmap(x, y, w, h);
	}
}