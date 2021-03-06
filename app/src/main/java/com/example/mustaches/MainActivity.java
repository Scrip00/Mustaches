package com.example.mustaches;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mustaches.Adapter.adapter_helper;
import com.example.mustaches.Adapter.recycler_adapter;
import com.example.mustaches.EntityClass.UserModel;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.AugmentedFaceNode;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements recycler_adapter.ListItemClickListener {
    private ModelRenderable modelRenderable;
    private Texture mustache;
    private boolean isAdded = false;
    private final HashMap<AugmentedFace, AugmentedFaceNode> faceNodeMap = new HashMap<>();
    private Button recordingBtn, recordingsListBtn;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CustomArFragment customArFragment;
    private String tag;
    private long tStart;
    private long tEnd;
    private File video;
    private RecyclerView phoneRecycler;
    private RecyclerView.Adapter adapter;
    private int mustIndex = 0;
    private AugmentedFaceNode augmentedFaceMode;
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE,  Manifest.permission.CAMERA};


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkPermissions();
        setContentView(R.layout.activity_main);

        phoneRecycler = findViewById(R.id.my_recycler);
        phoneRecycler();

        customArFragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        //        cameraProviderFuture.addListener(() -> {
        //            try {
        //                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        //                startCameraX(cameraProvider);
        //            } catch (ExecutionException e) {
        //                e.printStackTrace();
        //            } catch (InterruptedException e) {
        //                e.printStackTrace();
        //            }
        //        }, getExecutor());
        faceTrack();


        recordingsListBtn = findViewById(R.id.recordingsListBtn);
        recordingsListBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getBaseContext(), RecordingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });

        recordingBtn = findViewById(R.id.recordingBtn);
        recordingBtn.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onClick(View view) {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    startCameraX(cameraProvider);
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (recordingBtn.getText().equals("Record video")) {
                    recordingBtn.setText("Stop recording");
                    recordVideo();
                } else {
                    recordingBtn.setText("Record video");
                    videoCapture.stopRecording();
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        cameraProvider.unbindAll();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                // all permissions were granted
                break;
        }
    }

    public void saveTag() {
        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.save_tag_popup, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
        // dismiss the popup window when touched
        Button tagBtn = popupView.findViewById(R.id.tagBtn);
        tagBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText tagField = popupView.findViewById(R.id.tagField);
                tag = tagField.getText().toString();
                tEnd = System.currentTimeMillis();
                Log.d("DUR", String.valueOf((tEnd - tStart) / 1000.0));
                String duration = String.valueOf((tEnd - tStart) / 1000.0);
                Log.d("DATA", duration);
                Log.d("DATA", tag);
                Log.d("DATA", video.getAbsolutePath());
                Toast.makeText(MainActivity.this, "Video has been saved", Toast.LENGTH_SHORT).show();
                UserModel model = new UserModel();
                model.setVideo(video.getAbsolutePath());
                model.setDuration(duration);
                model.setTag(tag);
                DatabaseClass.getDatabase(getApplicationContext()).getDao().insertAllData(model);
                popupWindow.dismiss();
                Intent intent = new Intent(getBaseContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });
    }

    private void phoneRecycler() {

        //All Gradients
        GradientDrawable gradient2 = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xffe53c3c, 0xffe57373});
        GradientDrawable gradient1 = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xffeb9234, 0xfff1b370});
        GradientDrawable gradient3 = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xfff7c59f, 0xFFf7c59f});
        GradientDrawable gradient4 = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xffeff478, 0xfff9f9be});


        phoneRecycler.setHasFixedSize(true);
        phoneRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        ArrayList<adapter_helper> phonelocations = new ArrayList<>();
        phonelocations.add(new adapter_helper(gradient1, R.drawable.must_1_prev));
        phonelocations.add(new adapter_helper(gradient4, R.drawable.must_2_prev));
        phonelocations.add(new adapter_helper(gradient2, R.drawable.must_3_prev));
        phonelocations.add(new adapter_helper(gradient4, R.drawable.must_4_prev));
        phonelocations.add(new adapter_helper(gradient2, R.drawable.must_5_prev));


        adapter = new recycler_adapter(phonelocations, (recycler_adapter.ListItemClickListener) this);
        phoneRecycler.setAdapter(adapter);

    }

    @Override
    public void onphoneListClick(int clickedItemIndex) {
        Log.d("INDEX", "HUI");
        switch (clickedItemIndex) {
            case 0:
                faceNodeMap.clear();
                try {
                    augmentedFaceMode.setParent(null);
                } catch (Exception e) {
                }
                AugmentedFaceDisplay(R.drawable.must_1);
                break;
            case 1:
                faceNodeMap.clear();
                try {
                    augmentedFaceMode.setParent(null);
                } catch (Exception e) {
                }
                AugmentedFaceDisplay(R.drawable.must_2);
                break;
            case 2:
                faceNodeMap.clear();
                try {
                    augmentedFaceMode.setParent(null);
                } catch (Exception e) {
                }
                AugmentedFaceDisplay(R.drawable.must_3);
                break;
            case 3:
                faceNodeMap.clear();
                try {
                    augmentedFaceMode.setParent(null);
                } catch (Exception e) {
                }
                AugmentedFaceDisplay(R.drawable.must_4);
                break;
            case 4:
                faceNodeMap.clear();
                try {
                    augmentedFaceMode.setParent(null);
                } catch (Exception e) {
                }
                AugmentedFaceDisplay(R.drawable.must_5);
                break;
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void faceTrack() {
//        ModelRenderable.builder()
//                .setSource(this, R.raw.scene)
//                .build()
//                .thenAccept(rendarable -> {
//                    this.modelRenderable = rendarable;
//                    this.modelRenderable.setShadowCaster(false);
//                    this.modelRenderable.setShadowReceiver(false);
//
//                })
//                .exceptionally(throwable -> {
//                    Toast.makeText(this, "error loading model", Toast.LENGTH_SHORT).show();
//                    return null;
//                });

        // Load the face mesh texture.(2D texture on face)
        // Save the texture(.png file) in drawable folder.

        assert customArFragment != null;

        // This is important to make sure that the camera
        // stream renders first so that the face mesh
        // occlusion works correctly.
        customArFragment.getArSceneView().setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);
        customArFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
//            if (modelRenderable == null || texture == null) {
//                return;
//            }
            Frame frame = customArFragment.getArSceneView().getArFrame();
            assert frame != null;

            // Render the effect for the face Rendering the effect involves these steps:
            // 1.Create the Sceneform face node.
            // 2.Add the face node to the Sceneform scene.
            // 3.Set the face region Renderable. Extracting the face mesh and
            // rendering the face effect is added to a listener on
            // the scene that gets called on every processed camera frame.
            Collection<AugmentedFace> augmentedFaces = frame.getUpdatedTrackables(AugmentedFace.class);

            // Make new AugmentedFaceNodes for any new faces.
            for (AugmentedFace augmentedFace : augmentedFaces) {
                if (faceNodeMap.containsKey(augmentedFace)) return;

                augmentedFaceMode = new AugmentedFaceNode(augmentedFace);
                augmentedFaceMode.setParent(customArFragment.getArSceneView().getScene());
                augmentedFaceMode.setFaceMeshTexture(mustache);
//                augmentedFaceMode.setFaceRegionsRenderable(modelRenderable);
                faceNodeMap.put(augmentedFace, augmentedFaceMode);

                // Remove any AugmentedFaceNodes associated with
                // an AugmentedFace that stopped tracking.
                Iterator<Map.Entry<AugmentedFace, AugmentedFaceNode>> iterator = faceNodeMap.entrySet().iterator();
                Map.Entry<AugmentedFace, AugmentedFaceNode> entry = iterator.next();
                AugmentedFace face = entry.getKey();
                while (face.getTrackingState() == TrackingState.STOPPED) {
                    AugmentedFaceNode node = entry.getValue();
                    node.setParent(null);
                    iterator.remove();
                }
            }
        });
    }

    private void AugmentedFaceDisplay(int resource) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Texture.builder()
                    .setSource(this, resource)
                    .build()
                    .thenAccept(texture -> this.mustache = texture)
                    .exceptionally(throwable -> {
                        Toast.makeText(this, "cannot load texture", Toast.LENGTH_SHORT).show();
                        return null;
                    });
        }


    }


    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        //Preview preview = new Preview.Builder().build();
        //preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .build();

        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageCapture, videoCapture);
    }

    @Override
    public void onResume() {
        super.onResume();


    }

    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        tStart = System.currentTimeMillis();
        if (videoCapture != null) {
            File movieDir = new File(String.valueOf(Environment.getExternalStorageDirectory()) + "/movies");

            if (!movieDir.exists()) {
                movieDir.mkdir();
            }

            Date date = new Date();
            String timestamp = String.valueOf(date.getTime());
            String vidFilePath = movieDir.getAbsolutePath() + "/" + timestamp + ".mp4";

            File vidFile = new File(vidFilePath);


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            videoCapture.startRecording(
                    new VideoCapture.OutputFileOptions.Builder(vidFile).build(),
                    getExecutor(),
                    new VideoCapture.OnVideoSavedCallback() {
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                            video = vidFile;
                            saveTag();
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            Toast.makeText(MainActivity.this, "Video hasn't been saved: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        }
    }




    private void capturePhoto() {
        File photoDir = new File("/mnt/sdcard/Pictures/CameraXPhotos");

        if (!photoDir.exists()) {
            photoDir.mkdir();
        }

        Date date = new Date();
        String timestamp = String.valueOf(date.getTime());
        String photoFilePath = photoDir.getAbsolutePath() + "/" + timestamp + ".jpg";

        File photoFile = new File(photoFilePath);

        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(photoFile).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this, "Photo has been saved", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Photo hasn't been saved: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    public Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }
}