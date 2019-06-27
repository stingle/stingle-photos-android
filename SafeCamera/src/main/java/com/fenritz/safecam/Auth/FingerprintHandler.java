package com.fenritz.safecam.Auth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fenritz.safecam.R;

import javax.crypto.Cipher;

public class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

    // You should use the CancellationSignal method whenever your app can no longer process user input, for example when your app goes
    // into the background. If you don’t use this method, then other apps will be unable to access the touch sensor, including the lockscreen!//

    private CancellationSignal cancellationSignal;
    private Context context;
    private AlertDialog dialog = null;
    private Cipher cipher;
    private OnFingerprintSuccess successHandler;

    public FingerprintHandler(Context mContext, OnFingerprintSuccess successHandler) {
        context = mContext;
        this.successHandler = successHandler;
    }

    //Implement the startAuth method, which is responsible for starting the fingerprint authentication process//

    public void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject, final LoginManager.UserLogedinCallback loginCallback, boolean showPasswordOption) {

        this.cipher = cryptoObject.getCipher();

        cancellationSignal = new CancellationSignal();
        if (context.checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        manager.authenticate(cryptoObject, cancellationSignal, 0, this, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.fingerprint);
        dialog = builder.create();
        dialog.show();

        if(showPasswordOption) {
            Button usePasswordButton = (Button) dialog.findViewById(R.id.usePasswordButton);
            usePasswordButton.setVisibility(View.VISIBLE);
            usePasswordButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.cancel();
                    dialog = null;
                    LoginManager.showEnterPasswordToUnlock((Activity) context, loginCallback);
                }
            });
        }

        Button cancelButton = (Button)dialog.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
                dialog = null;
            }
        });


    }

    @Override
    //onAuthenticationError is called when a fatal error has occurred. It provides the error code and error message as its parameters//

    public void onAuthenticationError(int errMsgId, CharSequence errString) {

        //I’m going to display the results of fingerprint authentication as a series of toasts.
        //Here, I’m creating the message that’ll be displayed if an error occurs//

        if(dialog != null){
            TextView errorText = (TextView)dialog.findViewById(R.id.errorTextView);
            errorText.setText(context.getString(R.string.fingerprint_auth_error, errString));
        }
    }

    @Override

    //onAuthenticationFailed is called when the fingerprint doesn’t match with any of the fingerprints registered on the device//

    public void onAuthenticationFailed() {
        if(dialog != null){
            TextView errorText = (TextView)dialog.findViewById(R.id.errorTextView);
            errorText.setText(context.getString(R.string.fingerprint_auth_failed));
        }
        Toast.makeText(context, "Authentication failed", Toast.LENGTH_LONG).show();
    }

    @Override
    //onAuthenticationHelp is called when a non-fatal error has occurred. This method provides additional information about the error,
    //so to provide the user with as much feedback as possible I’m incorporating this information into my toast//
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        if(dialog != null){
            TextView errorText = (TextView)dialog.findViewById(R.id.errorTextView);
            errorText.setText(context.getString(R.string.fingerprint_auth_help, helpString));
        }
    }

    @Override
    //onAuthenticationSucceeded is called when a fingerprint has been successfully matched to one of the fingerprints stored on the user’s device//
    public void onAuthenticationSucceeded(
            FingerprintManager.AuthenticationResult result) {

        if(dialog != null){
            TextView errorText = (TextView)dialog.findViewById(R.id.errorTextView);
            errorText.setText(context.getString(R.string.fingerprint_auth_success));

            dialog.dismiss();
        }

        successHandler.onSuccess(cipher);
    }

    public static abstract class OnFingerprintSuccess{
        public abstract void onSuccess(final Cipher cipher);
    }

}
