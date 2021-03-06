package im.recognize.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import im.recognize.android.Event.FailureEvent;
import im.recognize.android.Event.RecognizeEvent;
import im.recognize.android.Event.SuccessEvent;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_TAKE_PHOTO = 1;
    public static final String PREF_ID = "USER_ID";
    public static final String PREF_KEY = "USER_KEY";
    public static final String PREF_MODE = "USER_MODE";
    public static final String PREF_SIZE = "USER_SIZE";
    public static final String FILE_PROVIDER = "im.recognize.fileprovider";

    String capturedPhotoPath;
    String recognizedPhotoPath;
    File resultFile;
    Spinner spinnerModes;
    Spinner spinnerSizes;
    Spinner spinnerFilter;
    Button btnViewResult;
    Button btnTakePic;
    TextView textViewId;
    TextView textViewKey;
    TextView textViewResult;
    ProgressBar progressBar;
    SharedPreferences sharedPreferences;
    int sampleSize;
    ItraffApi iTraffApi;

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        TextView privacy = (TextView) findViewById(R.id.privacy);
        privacy.setClickable(true);
        privacy.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_action_main);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnTakePic = (Button)findViewById(R.id.btn_take_pic);
        btnTakePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
                dispatchTakePictureIntent();
            }
        });

        // Build SharedPreferences object
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);

        // Assign form inputs, buttons and actions
        textViewId = (TextView) findViewById(R.id.edit_id);
        textViewKey = (TextView) findViewById(R.id.edit_key);
        textViewResult = (TextView) findViewById(R.id.txt_result);
        textViewResult.setVisibility(View.GONE);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        spinnerModes = populateSpinner(R.id.spinner_modes, R.array.array_modes);
        spinnerSizes = populateSpinner(R.id.spinner_sizes, R.array.array_sizes_single);
        spinnerFilter = populateSpinner(R.id.spinner_filter, R.array.array_filter);
        spinnerModes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                MainActivity.this.updateSizesSpinner(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // Do nothing
            }
        });
        btnViewResult = (Button) findViewById(R.id.btn_view_result);
        btnViewResult.setVisibility(View.GONE);
        btnViewResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Display picture using built-in photo browsing app
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = FileProvider.getUriForFile(MainActivity.this, FILE_PROVIDER, resultFile);
                intent.setDataAndType(uri, "image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(intent);
            }
        });

        // Updates form fields with data from SharedPreferences
        readSettings();

        // Assigns onClickListeners to hint buttons
        assignHintActions();
    }

    /**
     * Handles "?" buttons and Toast messages displayed when clicked
     */
    protected void assignHintActions() {
        ImageButton hintId = (ImageButton) findViewById(R.id.btn_hint_id);
        ImageButton hintKey = (ImageButton) findViewById(R.id.btn_hint_key);
        View.OnClickListener oclCredentials = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, getString(R.string.hint_credentials), Toast.LENGTH_LONG).show();
            }
        };
        hintId.setOnClickListener(oclCredentials);
        hintKey.setOnClickListener(oclCredentials);
        ImageButton hintMode = (ImageButton) findViewById(R.id.btn_hint_mode);
        hintMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, getString(R.string.hint_mode), Toast.LENGTH_LONG).show();
            }
        });
        ImageButton hintSize = (ImageButton) findViewById(R.id.btn_hint_size);
        hintSize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, getString(R.string.hint_size), Toast.LENGTH_LONG).show();
            }
        });
        ImageButton hintFilter = (ImageButton) findViewById(R.id.btn_hint_filter);
        hintFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, getString(R.string.hint_filter), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Populates sizes spinner with data basing on recognition mode selected
     * @param mode - recognition mode
     */
    public void updateSizesSpinner(int mode) {
        spinnerSizes.setEnabled(mode != 2);
        TextView hintShelf = (TextView) findViewById(R.id.txt_hint_shelf);
        hintShelf.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
        spinnerSizes.setAdapter(null);
        if (mode == 0) {
            spinnerSizes = populateSpinner(R.id.spinner_sizes, R.array.array_sizes_single);
        } else if (mode == 1) {
            spinnerSizes = populateSpinner(R.id.spinner_sizes, R.array.array_sizes_multi);
        }
        spinnerSizes.setSelection(sharedPreferences.getInt(PREF_SIZE, 0));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRequestFailure(FailureEvent event) {
        progressBar.setVisibility(View.GONE);
        Toast.makeText(this, event.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRequestSuccess(SuccessEvent event) {
        progressBar.setVisibility(View.GONE);

        // Draw bounding boxes on picture
        Bitmap resultBitmap = iTraffApi.drawBoundingBoxes(event.getObjects(), capturedPhotoPath, sampleSize);

        // Save new picture to the External Storage
        try {
            saveBitmapToExternalStorage(resultBitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Display "view result" button
        btnViewResult.setVisibility(View.VISIBLE);

        // Display string-formatted recognition data
        try {
            textViewResult.setVisibility(View.VISIBLE);
            textViewResult.setText(iTraffApi.getFormattedRecognitionData(event.getObjects()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves given image to ExternalStorage
     * @param bitmap - image to be saved
     * @throws IOException
     */
    protected void saveBitmapToExternalStorage(Bitmap bitmap) throws IOException {
        File image = getFileToSave();
        recognizedPhotoPath = image.getAbsolutePath();
        FileOutputStream out = new FileOutputStream(image);
        resultFile = image;
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.flush();
        out.close();
        bitmap.recycle();
    }

    /**
     * Populates given Spinner object found by spinnerViewId resource id, populates it with data
     * from spinnerData resource id and returns the Spinner instance
     * @param spinnerViewId - resource id of the spinner to be populated with data
     * @param spinnerData - resource id of the data to populate the spinner with
     * @return Spinner
     */
    protected Spinner populateSpinner(int spinnerViewId, int spinnerData) {
        Spinner spinner = (Spinner) findViewById(spinnerViewId);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, spinnerData, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    /**
     * Opens camera intent and captures the photo to private app's Pictures directory
     */
    protected void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, FILE_PROVIDER, photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    /**
     * Creates temporary file in application's private file storage
     * @return temporary file path
     * @throws IOException
     */
    protected File createImageFile() throws IOException {
        File image = getFileToSave();
        // Save a file: path for use with ACTION_VIEW intents
        capturedPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {

            Bitmap bitmap;
            int modeId = (int) spinnerModes.getSelectedItemId();
            int sizeId = (int) spinnerSizes.getSelectedItemId();

            if (modeId == 2) {
                bitmap = BitmapFactory.decodeFile(capturedPhotoPath);
            } else {
                bitmap = getDownscaledBitmap(capturedPhotoPath, ItraffApi.SIZES[modeId][sizeId]);
            }

            // Do the API request
            String userKey = textViewKey.getText().toString();
            if (iTraffApi == null) {
                iTraffApi = new ItraffApi(textViewId.getText().toString(), userKey, (int) spinnerModes.getSelectedItemId(), (int) spinnerFilter.getSelectedItemId());
            } else {
                iTraffApi
                    .setClientId(textViewId.getText().toString())
                    .setClientKey(userKey)
                    .setMode((int) spinnerModes.getSelectedItemId())
                    .setFilter((int) spinnerFilter.getSelectedItemId())
                    ;
            }
            progressBar.setVisibility(View.VISIBLE);
            btnViewResult.setVisibility(View.GONE);
            textViewResult.setVisibility(View.GONE);
            try {
                EventBus.getDefault().post(new RecognizeEvent(bitmap2byteArray(bitmap), userKey, iTraffApi.getRequestUrl()));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Loads scaled down image to the memory to prevent our of memory exception
     * @param photoPath - absolute path to the image resource
     * @param targetLongEdge - dimension of the longer edge that the image should be rescaled to
     * @return
     */
    protected Bitmap getDownscaledBitmap(String photoPath, int targetLongEdge) {

        // Get current dimensions of the Bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine the scale multiplier for the image
        sampleSize = Math.max(photoW, photoH) / targetLongEdge;

        // Decode the image file into a downscaled Bitmap
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(photoPath, bmOptions);
    }

    /**
     * Converts bitmap to byte array
     * @param bitmap - bitmap to be converted
     * @return JPEG compressed byte array
     */
    protected byte[] bitmap2byteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    /**
     * Reads data from private SharedPreferences storage if available and assigns values to form inputs
     */
    protected void readSettings() {
        textViewId.setText(sharedPreferences.getString(PREF_ID, ""));
        textViewKey.setText(sharedPreferences.getString(PREF_KEY, ""));
        int mode = sharedPreferences.getInt(PREF_MODE, 0);
        spinnerModes.setSelection(mode);
        updateSizesSpinner(mode);
    }

    /**
     * Reads data from inputs and saves it to private SharedPreferences storage
     */
    protected void saveSettings() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(PREF_ID, textViewId.getText().toString());
        editor.putString(PREF_KEY, textViewKey.getText().toString());
        editor.putInt(PREF_MODE, spinnerModes.getSelectedItemPosition());
        editor.putInt(PREF_SIZE, spinnerSizes.getSelectedItemPosition());
        editor.commit();
    }

    protected File getFileToSave() throws IOException {
        String[] modeNames = getResources().getStringArray(R.array.array_modes);
        String fileName = "RecognizeIm_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + "_" + modeNames[(int) spinnerModes.getSelectedItemId()];
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(fileName, ".jpg", storageDir);
    }
}
