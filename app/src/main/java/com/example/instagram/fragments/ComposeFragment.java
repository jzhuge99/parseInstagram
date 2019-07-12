package com.example.instagram.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.instagram.BitmapScaler;
import com.example.instagram.R;
import com.example.instagram.model.Post;
import com.parse.ParseFile;
import com.parse.ParseUser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

import static android.app.Activity.RESULT_OK;

public class ComposeFragment extends Fragment {

    @BindView(R.id.etDescription) EditText etDescriptionInput;
    @BindView(R.id.btnCreate) Button btnCreate;
    @BindView(R.id.ivPreview) ImageView ivPreview;

    //this is for the stretch goal of the progress bar
    @BindView(R.id.pbLoading) ProgressBar pbLoading;

    private Unbinder unbinder;

    private final String TAG = "ComposeFragment";
    private final static int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1034;
    private String photoFileName = "photo.jpg";
    private File photoFile;
    private ParseFile parseFile;



    // onCreateView method is called when Fragment should create its View object hierarchy, either dynamically or via XML layout inflation
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_compose, container, false);
    }

    // this is triggered soon after onCreateView()
    // any view setup should occur here
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // setup handles to view objects here
        unbinder = ButterKnife.bind(this, view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Image taken!");

                Bitmap takenImage = rotateBitmapOrientation(photoFile.getAbsolutePath());
                Bitmap resizedBitmap = BitmapScaler.scaleToFitWidth(takenImage, 300);
                // write the smaller bitmap back to disk
                // configure byte output stream
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, bytes);
                File resizedFile = getPhotoFileUri(photoFileName + "_resized");
                try {
                    resizedFile.createNewFile();
                    FileOutputStream fos = new FileOutputStream(resizedFile);
                    // write the bytes of the bitmap to file
                    fos.write(bytes.toByteArray());
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error resizing image");
                    e.printStackTrace();
                }

                // load resized image into preview
                ivPreview.setImageBitmap(resizedBitmap);
                parseFile = new ParseFile(resizedFile);
            } else {
                Log.d(TAG, "Image was not taken");
            }
        }
    }

    @OnClick(R.id.btnCreate)
    void post() {
        Log.d(TAG, "Creating post...");
        final String description = etDescriptionInput.getText().toString();
        final ParseUser user = ParseUser.getCurrentUser();
        createPost(description, parseFile, user);
    }

    @OnClick(R.id.btnTakePhoto)
    void launchCamera() {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        photoFile = getPhotoFileUri("pho to.jpg");

        // wrap File object into a content provider
        Uri fileProvider = FileProvider.getUriForFile(getContext(), "com.codepath.fileprovider", photoFile);
        i.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider);

        // the app will crash if no app can handle the intent
        if (i.resolveActivity(getContext().getPackageManager()) != null) {
            startActivityForResult(i, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }
    }


    private void startTimelineFragment() {
        Fragment fragment = new TimelineFragment();

        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.flContainer, fragment, "FRAGMENT_TAG").commit();
    }

    // Returns the file for a photo stored on disk given the filename
    private File getPhotoFileUri(String filename) {
        // access package-specific directories without requesting external read/write runtime permissions
        File mediaStorageDir = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), TAG);

        // create storage directory if it doesn't exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d(TAG, "Failed to create directory");
        }
        // return file target for the photo based on filename
        File file = new File(mediaStorageDir.getPath() + File.separator + filename);
        return file;
    }

    private void createPost(String description, ParseFile imageFile, ParseUser user) {
        pbLoading.setVisibility(ProgressBar.VISIBLE);
        final Post newPost = new Post();
        newPost.setDescription(description);
        newPost.setImage(imageFile);
        newPost.setUser(user);

        newPost.saveInBackground(e -> {
            if (e == null) {
                Log.d(TAG, "Post successfully created!");
                startTimelineFragment();
                pbLoading.setVisibility(ProgressBar.INVISIBLE);
            } else {
                Log.e(TAG, "Post not created");
                e.printStackTrace();
                pbLoading.setVisibility(ProgressBar.INVISIBLE);
            }
        });
    }

    private Bitmap rotateBitmapOrientation(String photoFilePath) {
        // Create and configure BitmapFactory
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photoFilePath, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        Bitmap bm = BitmapFactory.decodeFile(photoFilePath, opts);
        // Read EXIF Data
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(photoFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String orientString = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
        int orientation = orientString != null ? Integer.parseInt(orientString) : ExifInterface.ORIENTATION_NORMAL;
        int rotationAngle = 0;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotationAngle = 90;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotationAngle = 180;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotationAngle = 270;
        // Rotate Bitmap
        Matrix matrix = new Matrix();
        matrix.setRotate(rotationAngle, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        Bitmap rotatedBitmap = Bitmap.createBitmap(bm, 0, 0, bounds.outWidth, bounds.outHeight, matrix, true);
        // Return result
        return rotatedBitmap;
    }


}

