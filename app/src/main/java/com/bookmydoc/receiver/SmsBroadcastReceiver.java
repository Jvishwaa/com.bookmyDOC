package com.bookmydoc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

public class SmsBroadcastReceiver extends BroadcastReceiver {

    private OtpReceiveListener otpReceiveListener;

    public interface OtpReceiveListener {
        void onOtpReceived(String otp);
    }

    public void init(OtpReceiveListener listener) {
        this.otpReceiveListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status != null && status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                    String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                    String otp = message.replaceAll("[^0-9]", "").substring(0, 6);
                    if (otpReceiveListener != null) otpReceiveListener.onOtpReceived(otp);
                }
            }
        }
    }
}