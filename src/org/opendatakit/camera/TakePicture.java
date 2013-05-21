package org.opendatakit.camera;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;

interface Callback {
	public void enableSaveButton();
}

public class TakePicture extends Activity implements Callback {
	private Camera mCamera;
	private CameraPreview mPreview;
	private FrameLayout preview;
	public Button takePic;
	public Button retakePic;
	public Button savePic;
	private boolean retakeOption;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.take_picture);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		Intent intent = getIntent();
		Bundle data = intent.getExtras();

		// default app parameters
		retakeOption = true;
		File rootDir;
		if (android.os.Build.VERSION.SDK_INT < 8) {
			rootDir = new File(Environment.getExternalStorageDirectory(),
					"Pictures");
		} else {
			rootDir = Environment
					.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		}
		File defaultFile = new File(rootDir, "Default");
		String filePath = defaultFile.getAbsolutePath();

		// parameters coming in on intent override the defaults...
		if (data != null) {
			if (data.containsKey("filePath")) {
				filePath = data.getString("filePath");
			}

			if (data.containsKey("retakeOption")) {
				retakeOption = data.getBoolean("retakeOption");
			}
		}

		// Create an instance of Camera
		mCamera = getCameraInstance();
		if (mCamera == null) {
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle("Camera Not Found")
					.setMessage("Unable to access camera device")
					.setCancelable(false)
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									TakePicture.this.setResult(RESULT_CANCELED);
									TakePicture.this.finish();
								}
							});
			b.show();
			return;
		}

		preview = (FrameLayout) findViewById(R.id.camera_preview);
		mPreview = new CameraPreview(this, mCamera, preview, filePath);
		preview.addView(mPreview);

		// And see if we should set an alignment shape.
		/** Notes **/
		// In order to display the test strips rectangles use the following
		// method call:
		// mPreview.setShapeSize(...);
		//
		// Parameters for setShapeSize:
		// setShapeSize(outerRecWidth, outerRecHeight, innerRecWidth,
		// innerRecHeight, y-offset, x-offset)

		// Examples:

		// --Determine Test Parameters--
		// mPreview.setShapeSize(2214, 426, 738, 142, 1107, 213);

		// --DFA_Multiplex Test Parameters--
		// mPreview.setShapeSize(1300, 1245, 325, 311, 650, 623);

		// --SD_Malaria Test Parameters--
		// mPreview.setShapeSize(1647, 463, 549, 154, 824, 232);
		if (data != null) {
			if (data.containsKey("dimensions")) {
				setAlignmentShape(data.getIntArray("dimensions"));
			}
		}

		// initialize the buttons (Take Picture, Retake Picture, and Save
		// Picture)
		takePic = (Button) findViewById(R.id.button_capture);
		takePic.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				overrideClick();
			}
		});

		retakePic = (Button) findViewById(R.id.retake_picture);
		retakePic.setOnClickListener(retakePicButton);
		retakePic.setEnabled(false);

		savePic = (Button) findViewById(R.id.keep_picture);
		savePic.setOnClickListener(savePicButton);
		savePic.setEnabled(false);
	}

	// verifies that the test shape array parameters are valid
	// and sets the test shape
	private void setAlignmentShape(int shapeParam[]) {
		// check shape dimensions
		if (shapeParam != null) {
			boolean allPositive = true;
			for (int i = 0; i < 6; i++) {
				if (shapeParam[i] < 0)
					allPositive = false;
			}

			if (allPositive) {
				// check that innerRecHeight < outerRecHeight
				if (shapeParam[3] >= shapeParam[1])
					shapeParam[3] = shapeParam[1] / 2;

				// check that innerRecWidth < outerRecWidth
				if (shapeParam[2] >= shapeParam[0])
					shapeParam[2] = shapeParam[0] / 2;

				// check that x-offset < outerRecHeight
				if (shapeParam[5] >= shapeParam[1])
					shapeParam[5] = shapeParam[1] / 2;

				// check that y-offset < outerRecWidth
				if (shapeParam[4] >= shapeParam[0])
					shapeParam[4] = shapeParam[0] / 2;

				// check that the inner rectangle isn't passed the right edge
				// of the outer rectangle
				if (shapeParam[5] + shapeParam[3] / 2 > shapeParam[1])
					shapeParam[5] -= (shapeParam[5] + shapeParam[3] / 2)
							- shapeParam[1];

				// check that the inner rectangle isn't passed the left edge
				// of the outer rectangle
				if (shapeParam[5] - shapeParam[3] / 2 < 0)
					shapeParam[5] += Math
							.abs(shapeParam[5] - shapeParam[3] / 2);

				// check that the inner rectangle isn't passed the top
				// of the outer rectangle
				if (shapeParam[4] - shapeParam[2] / 2 < 0)
					shapeParam[4] += Math
							.abs(shapeParam[4] - shapeParam[2] / 2);

				// check that the inner rectangle isn't passed the bottom
				// of the outer rectangle
				if (shapeParam[4] + shapeParam[2] / 2 > shapeParam[0])
					shapeParam[4] -= shapeParam[4] + shapeParam[2] / 2
							- shapeParam[0];

				TestDimensions shape = new TestDimensions(shapeParam[0],
						shapeParam[1], shapeParam[2], shapeParam[3],
						shapeParam[4], shapeParam[5]);
				mPreview.setShapeSize(shape);
			}
		}
	}

	public void overrideClick() {
		// pass a reference to "this" class so that
		// a callback can be made to the enableSaveButton method
		mPreview.takePic(this);
		takePic.setEnabled(false);
	}

	public void enableSaveButton() {
		if (retakeOption)
			retakePic.setEnabled(true);

		savePic.setEnabled(true);
	}

	private OnClickListener retakePicButton = new OnClickListener() {
		public void onClick(View v) {
			mPreview.retakePicture();
			// adjust buttons accordingly
			takePic.setEnabled(true);
			retakePic.setEnabled(false);
			savePic.setEnabled(false);
		}
	};

	private OnClickListener savePicButton = new OnClickListener() {
		public void onClick(View v) {
			mPreview.savePicture();
		}
	};

	protected void onPause() {
		super.onPause();
		// Handled in CameraPreview
	}

	protected void onResume() {
		super.onResume();
		// Handled in CameraPreview
	}

	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return c;
	}
}
