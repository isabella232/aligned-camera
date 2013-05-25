package org.opendatakit.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

@SuppressLint("ViewConstructor")
public class CameraPreview extends SurfaceView implements
		SurfaceHolder.Callback, Camera.PictureCallback,
		Camera.AutoFocusCallback {
	private SurfaceHolder mHolder;
	private int currOrientation;
	private Camera.Parameters currParameters;
	private Camera mCamera;
	private boolean pictureTaken;
	private TestDimensions shapeDim;
	private Context context;
	private FrameLayout screenPreview;
	private File pictureFile;
	private byte[] cameraData;
	private boolean generateShapes;
	private final String directoryPath;
	private Callback parentRef;

	@SuppressWarnings("deprecation")
	public CameraPreview(Context context, Camera camera, FrameLayout preview,
			String directoryPath) {
		super(context);
		this.context = context;
		mCamera = camera;
		screenPreview = preview;
		this.directoryPath = directoryPath;
		generateShapes = true;
		// initialize blank shape parameters
		shapeDim = new TestDimensions(0, 0, 0, 0, 0, 0);
		pictureTaken = false;
		pictureFile = null;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		try {
			if (mCamera == null) {
				mCamera = Camera.open();
				mCamera.startPreview();
			} 
		} catch (Exception e) {
			e.printStackTrace();
			((Activity) context).setResult(Activity.RESULT_CANCELED);
			((Activity) context).finish();
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		if (parentRef != null) { // a picture was taken
			// the current image preview is being destroyed, so the 
			// camera buttons are reset
			parentRef.resetButtons(); 
		} if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		} 
	}

	private int determineDisplayOrientation() {
	     android.hardware.Camera.CameraInfo info =
	             new android.hardware.Camera.CameraInfo();
	     android.hardware.Camera.getCameraInfo(0, info);
	     WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
	     int rotation = wm.getDefaultDisplay().getRotation();
	     int degrees = 0;
	     switch (rotation) {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }

	     int result;
	     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	         result = (info.orientation + degrees) % 360;
	         result = (360 - result) % 360;  // compensate the mirror
	     } else {  // back-facing
	         result = (info.orientation - degrees + 360) % 360;
	     }
	     return result;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			e.printStackTrace();
			((Activity) context).setResult(Activity.RESULT_CANCELED);
			((Activity) context).finish();
		}

		// start preview with new settings
		try {
			Camera.Parameters parameters = mCamera.getParameters();
			List<String> focusModes = parameters.getSupportedFocusModes();
			if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			}

			parameters.setPreviewSize(w, h);
			if ( android.os.Build.VERSION.SDK_INT < 9 ) {
			     WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			     int rotation = Surface.ROTATION_0;
			     if ( android.os.Build.VERSION.SDK_INT >= 8 ) {
			    	 rotation = wm.getDefaultDisplay().getRotation();
			     }
			     int degrees = 0;
			     switch (rotation) {
			         case Surface.ROTATION_0:  degrees =   0;
			         	parameters.set("orientation", "portrait"); break;
			         case Surface.ROTATION_90: degrees =  90;
			         	parameters.set("orientation", "landscape"); break;
			         case Surface.ROTATION_180:degrees = 180;
			         	parameters.set("orientation", "portrait"); break;
			         case Surface.ROTATION_270:degrees = 270;
			         	parameters.set("orientation", "landscape"); break;
			     }
			     parameters.set("rotation",(90 - degrees + 360) % 360);
			} else {
				int orientation = determineDisplayOrientation();
				currOrientation = orientation;
				mCamera.setDisplayOrientation(orientation);
			}
			mCamera.setParameters(parameters);
			mCamera.setPreviewDisplay(holder);
			mCamera.startPreview();
			currParameters = parameters;
			
			// uses a boolean flag to make sure that shapes are only generated once
			if (generateShapes) {
				setTestShape(h, w, context);
			}
		} catch (Exception e) {
			e.printStackTrace();
			((Activity) context).setResult(Activity.RESULT_CANCELED);
			((Activity) context).finish();
		}
	}

	// print the test strip rectangles on the preview screen
	public void setTestShape(int screenW, int screenH, Context context) {
		generateShapes = false;
		// calculate test strip ratio (width/height)
		double outerRecRatio = (double) shapeDim.getOuterRecW()
				/ shapeDim.getOuterRecH();
		int outerRecWidth = (int) (screenW * 0.8);
		int outerRecHeight = (int) (outerRecWidth / outerRecRatio);

		boolean extraPadding = false;
		int horzOffset = 0;
		// if the test strip is square shaped re-calculate its size based on
		// the height of the screen rather than the width
		if (outerRecHeight >= (screenH * 0.8)) {
			outerRecHeight = (int) (screenH * 0.8);
			outerRecWidth = (int) (outerRecHeight * outerRecRatio);
			horzOffset = (int) (screenH * 0.1);
			extraPadding = true;
		}

		// Creates the outer rectangle shape
		GradientDrawable bigRec = new GradientDrawable();
		bigRec.setShape(0);
		bigRec.setStroke(4, -65536);
		bigRec.setColor(0);
		bigRec.setSize(outerRecHeight, outerRecWidth);
		ImageView imgBigRec = new ImageView(context);
		imgBigRec.setImageDrawable(bigRec);
		screenPreview.addView(imgBigRec);

		int vertOffset = (int) (screenW * 0.10);
		imgBigRec.setPadding(horzOffset, vertOffset, horzOffset, vertOffset);

		// Creates the inner rectangle shape and calculates the x and y offsets
		double recSizeCompare = (double) outerRecWidth
				/ shapeDim.getOuterRecW();
		// scales the x and y offsets appropriately
		int x = (int) (recSizeCompare * shapeDim.getXoffset());
		int y = (int) (recSizeCompare * shapeDim.getYoffset());
		int innerRecWidth = (int) (shapeDim.getInnerRecW() * recSizeCompare);
		int innerRecHeight = (int) (shapeDim.getInnerRecH() * recSizeCompare);

		GradientDrawable smallRec = new GradientDrawable();
		smallRec.setShape(0);
		smallRec.setStroke(4, -65536);
		smallRec.setColor(0);
		smallRec.setSize(innerRecHeight, innerRecWidth);
		ImageView imgSmallRec = new ImageView(context);
		imgSmallRec.setImageDrawable(smallRec);
		screenPreview.addView(imgSmallRec);

		// initialize the offset of the inner rectangle
		// inside of the larger rectangle
		int vertiOffsetUpper = 0;
		int vertiOffsetLower = 0;
		int horiOffsetLeft = 0;
		int horiOffsetRight = 0;

		// distance from the top/bottom of the screen to the
		// top/bottom edge of the outer rectangle
		int heightToRec = (screenW - outerRecWidth) / 2;

		// distance from the left/right of the screen to the
		// left/right edge of the outer rectangle
		int widthToRec = (screenH - outerRecHeight) / 2;

		if (extraPadding) { // this case is for square shaped test strips
			vertiOffsetUpper = heightToRec + (y - innerRecWidth / 2);
			vertiOffsetLower = heightToRec
					+ ((outerRecWidth - y) - innerRecWidth / 2);
			horiOffsetLeft = horzOffset + (x - innerRecHeight / 2);
			horiOffsetRight = horzOffset
					+ ((outerRecHeight - x) - innerRecHeight / 2);
		} else { // for rectangular shaped test strips
			vertiOffsetUpper = vertOffset + (y - innerRecWidth / 2);
			vertiOffsetLower = vertOffset
					+ ((outerRecWidth - y) - innerRecWidth / 2);
			horiOffsetLeft = widthToRec + (x - innerRecHeight / 2);
			horiOffsetRight = widthToRec
					+ ((outerRecHeight - x) - innerRecHeight / 2);
		}

		imgSmallRec.setPadding(horiOffsetLeft, vertiOffsetUpper,
				horiOffsetRight, vertiOffsetLower);
	}

	public void setShapeSize(TestDimensions shape) {
		shapeDim = shape;
	}

	public void takePic(Callback parent) {
		parentRef = parent;
		mCamera.autoFocus(this);
	}

	public void onPictureTaken(byte[] data, Camera camera) {	
		pictureTaken = true;
		pictureFile = getOutputMediaFile();
		cameraData = data;
		parentRef.enableSaveButton();
	}

	// If the "Save" button is pressed then the picture file is saved
	// and then this app returns to the caller that inovked it
	public void savePicture() {
		try {
			FileOutputStream fos = new FileOutputStream(pictureFile);
			// Rotate data -90 to compensate for preview rotation
			Bitmap bmp = BitmapFactory.decodeByteArray(cameraData, 0,
					cameraData.length);
			Matrix matrix = new Matrix();
			if ( android.os.Build.VERSION.SDK_INT < 9 ) {
				matrix.postRotate(90);
			} else {
				int orientation = determineDisplayOrientation();
				matrix.postRotate(orientation);
			}
			bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(),
					bmp.getHeight(), matrix, true);
			bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
			fos.flush();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			((Activity) context).setResult(Activity.RESULT_CANCELED);
			((Activity) context).finish();
		} catch (IOException e) {
			e.printStackTrace();
			((Activity) context).setResult(Activity.RESULT_CANCELED);
			((Activity) context).finish();
		}

		// release the camera
		mCamera.setPreviewCallback(null);
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;

		Intent result = new Intent();
		result.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
				pictureFile.getAbsolutePath());

		((Activity) context).setResult(Activity.RESULT_OK, result);
		// return to the app that called invoked this one
		((Activity) context).finish();
	}

	// If the "Retake" button is pressed then the picture file is
	// deleted and the camera preview is resumed
	public void retakePicture() {
		if (pictureFile != null) pictureFile.delete();
		cameraData = null;
		pictureTaken = false;
		mCamera.startPreview();	
	}

	/** Create a File for saving an image or video */
	@SuppressLint("SimpleDateFormat")
	private File getOutputMediaFile() {
		File mediaStorageDir = new File(directoryPath);
		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(directoryPath, "failed to create directory");
				((Activity) context).setResult(Activity.RESULT_CANCELED);
				((Activity) context).finish();
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyy_MMdd_HH_mmss")
				.format(new Date());
		File mediaFile;
		mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ "IMG_" + timeStamp + ".jpg");

		return mediaFile;
	}
	
	// inovked by the onPause method in 
	// the TakePicture class
	public void releaseCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
	
	// invoked by the onResume method in
	// the TakePicture class
	public void restoreCamera() {
		// currParameters, currOrientation, and mHolder are created
		// when the surface was originally created
		if (mCamera == null) {
			mCamera = Camera.open();
			mCamera.setParameters(currParameters);
			
			if (android.os.Build.VERSION.SDK_INT >= 9 ) {
				mCamera.setDisplayOrientation(currOrientation);
			} 
			try {
				mCamera.setPreviewDisplay(mHolder);
			} catch (IOException e) {
				e.printStackTrace();
				((Activity) context).setResult(Activity.RESULT_CANCELED);
				((Activity) context).finish();
			}
			
			if (!pictureTaken) {
				mCamera.startPreview();
			}
		}
	}

	public void onAutoFocus(boolean success, Camera camera) {
		mCamera.takePicture(null, null, this);
	}
}
