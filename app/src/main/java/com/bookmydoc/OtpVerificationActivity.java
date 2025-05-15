package com.bookmydoc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bookmydoc.databinding.ActivityOtpVerificationBinding;
import com.bookmydoc.receiver.SmsBroadcastReceiver;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class OtpVerificationActivity extends AppCompatActivity {

    EditText[] otpFields = new EditText[6];
    Button verifyOtpButton;
    TextView resendOtp;
    String verificationId, phone;
    FirebaseAuth mAuth;
    SmsBroadcastReceiver smsReceiver;
    CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        verificationId = getIntent().getStringExtra("verificationId");
        phone = getIntent().getStringExtra("phone");

        otpFields[0] = findViewById(R.id.otpDigit1);
        otpFields[1] = findViewById(R.id.otpDigit2);
        otpFields[2] = findViewById(R.id.otpDigit3);
        otpFields[3] = findViewById(R.id.otpDigit4);
        otpFields[4] = findViewById(R.id.otpDigit5);
        otpFields[5] = findViewById(R.id.otpDigit6);

        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resendOtp = findViewById(R.id.resendOtp);

        setOtpFieldLogic();
        startSmsRetriever();
        startResendCountdown();

        verifyOtpButton.setOnClickListener(v -> {
            StringBuilder code = new StringBuilder();
            for (EditText field : otpFields) code.append(field.getText().toString());

            if (code.length() == 6 && verificationId != null) {
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code.toString());
                signInWithCredential(credential);
            } else {
                Toast.makeText(this, "Enter full OTP", Toast.LENGTH_SHORT).show();
            }
        });

        resendOtp.setOnClickListener(v -> {
            resendOtp.setEnabled(false);
            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber("+91" + phone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {}
                        @Override
                        public void onVerificationFailed(@NonNull FirebaseException e) {}
                        @Override
                        public void onCodeSent(@NonNull String verificationId,
                                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
                            OtpVerificationActivity.this.verificationId = verificationId;
                            startResendCountdown();
                        }
                    })
                    .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                startActivity(new Intent(this, ProfileSetupActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setOtpFieldLogic() {
        for (int i = 0; i < otpFields.length; i++) {
            final int index = i;
            otpFields[i].addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) {}
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < 5) otpFields[index + 1].requestFocus();
                    else if (s.length() == 0 && index > 0) otpFields[index - 1].requestFocus();
                }
            });
            otpFields[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (otpFields[index].getText().toString().isEmpty() && index > 0) {
                        otpFields[index - 1].requestFocus();
                    }
                }
                return false;
            });
        }
    }

    private void startSmsRetriever() {
        SmsRetrieverClient client = SmsRetriever.getClient(this);
        client.startSmsRetriever();
    }

    private void startResendCountdown() {
        resendOtp.setEnabled(false);
        countDownTimer = new CountDownTimer(60000, 1000) {
            public void onTick(long millis) {
                resendOtp.setText("Resend in " + millis / 1000 + "s");
            }
            public void onFinish() {
                resendOtp.setText("Resend OTP");
                resendOtp.setEnabled(true);
            }
        }.start();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        smsReceiver = new SmsBroadcastReceiver();
        smsReceiver.init(otp -> {
            for (int i = 0; i < otp.length(); i++) {
                otpFields[i].setText(String.valueOf(otp.charAt(i)));
            }
        });
        registerReceiver(smsReceiver, new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(smsReceiver);
    }
}