package com.bookmydoc;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bookmydoc.manager.EncryptionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_GALLERY = 200;

    private Uri imageUri = null;

    private EditText fullNameInput, emailInput, ageInput;
    private TextView dobInput;
    private RadioGroup genderGroup;
    private ImageView profileImageView;
    private Button saveBtn, cameraBtn, galleryBtn;

    private EncryptionManager encryptionManager;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        try {
            encryptionManager = new EncryptionManager();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Encryption setup failed", Toast.LENGTH_SHORT).show();
            finish();
        }

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        fullNameInput = findViewById(R.id.fullNameInput);
        emailInput = findViewById(R.id.emailInput);
        dobInput = findViewById(R.id.dobInput);
        ageInput = findViewById(R.id.ageInput);
        genderGroup = findViewById(R.id.genderGroup);
        profileImageView = findViewById(R.id.profileImageView);
        saveBtn = findViewById(R.id.saveProfileBtn);
//        cameraBtn = findViewById(R.id.cameraBtn);
        galleryBtn = findViewById(R.id.selectImageBtn);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Saving profile...");
        progressDialog.setCancelable(false);

        cameraBtn.setOnClickListener(v -> openCamera());
        galleryBtn.setOnClickListener(v -> openGallery());

        dobInput.setOnClickListener(v -> showDatePicker());

        saveBtn.setOnClickListener(v -> saveProfile());
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, REQUEST_GALLERY);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog pickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    String dob = dayOfMonth + "/" + (month + 1) + "/" + year;
                    dobInput.setText(dob);

                    // Calculate age
                    int age = Calendar.getInstance().get(Calendar.YEAR) - year;
                    ageInput.setText(String.valueOf(age));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        pickerDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA && data != null) {
                Bitmap photo = (Bitmap) data.getExtras().get("data");
                imageUri = getImageUriFromBitmap(photo);
                profileImageView.setImageBitmap(photo);
            } else if (requestCode == REQUEST_GALLERY && data != null) {
                imageUri = data.getData();
                profileImageView.setImageURI(imageUri);
            }
        }
    }

    private Uri getImageUriFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "Title", null);
        return Uri.parse(path);
    }

    private void saveProfile() {
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String dob = dobInput.getText().toString().trim();
        String ageStr = ageInput.getText().toString().trim();

        if (fullName.isEmpty()) {
            fullNameInput.setError("Enter full name");
            fullNameInput.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            emailInput.setError("Enter email");
            emailInput.requestFocus();
            return;
        }
        if (dob.isEmpty()) {
            dobInput.setError("Select DOB");
            dobInput.requestFocus();
            return;
        }
        if (ageStr.isEmpty()) {
            ageInput.setError("Age required");
            ageInput.requestFocus();
            return;
        }

        int selectedGenderId = genderGroup.getCheckedRadioButtonId();
        if (selectedGenderId == -1) {
            Toast.makeText(this, "Select gender", Toast.LENGTH_SHORT).show();
            return;
        }
        RadioButton selectedGenderBtn = findViewById(selectedGenderId);
        String gender = selectedGenderBtn.getText().toString();

        progressDialog.show();

        if (imageUri != null) {
            uploadImageAndSaveProfile(imageUri, fullName, email, dob, Integer.parseInt(ageStr), gender);
        } else {
            // No image chosen - use default URL and save profile
            saveProfileData(fullName, email, dob, Integer.parseInt(ageStr), gender, "https://yourdomain.com/default_profile.png");
        }
    }

    private void uploadImageAndSaveProfile(Uri uri, String fullName, String email, String dob, int age, String gender) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        StorageReference imageRef = storageReference.child("profileImages/" + uid + ".jpg");

        imageRef.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri1 -> {
                            String imageUrl = uri1.toString();
                            saveProfileData(fullName, email, dob, age, gender, imageUrl);
                        })
                        .addOnFailureListener(e -> {
                            progressDialog.dismiss();
                            Toast.makeText(ProfileSetupActivity.this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(ProfileSetupActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveProfileData(String fullName, String email, String dob, int age, String gender, String profileImageUrl) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> profile = new HashMap<>();
        try {
            profile.put("fullName", encryptionManager.encrypt(fullName));
            profile.put("email", encryptionManager.encrypt(email));
            profile.put("dob", encryptionManager.encrypt(dob));
            profile.put("age", age);
            profile.put("gender", encryptionManager.encrypt(gender));
            profile.put("profileImage", encryptionManager.encrypt(profileImageUrl));
        } catch (Exception e) {
            progressDialog.dismiss();
            e.printStackTrace();
            Toast.makeText(this, "Encryption error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("Users").document(uid).collection("profile").document("info")
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(ProfileSetupActivity.this, "Profile saved!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                    // Navigate next or finish
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(ProfileSetupActivity.this, "Failed to save profile: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}