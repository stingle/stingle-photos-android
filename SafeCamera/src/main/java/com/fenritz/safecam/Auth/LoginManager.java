package com.fenritz.safecam.Auth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.fenritz.safecam.DashboardActivity;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.SetUpActivity;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;

public class LoginManager {

    public static final String FINGERPRINT_PREFERENCE = "fingerprint";
    public static final String LAST_LOCK_TIME = "lock_time";
    public static final String LOGINS_COUNT_FOR_POPUP = "logins_count_fp";
    public static final String DONT_SHOW_POPUP = "dont_show_popup";
    public static final String POPUP_LATERS_COUNT = "laters_count";

    public LoginManager(){

    }

    public static void checkLogin(Activity activity) {
        checkLogin(activity, null, true);
    }
    public static void checkLogin(Activity activity, final UserLogedinCallback loginCallback) {
        checkLogin(activity, loginCallback, true);
    }

    public static void checkLogin(Activity activity, final UserLogedinCallback loginCallback, boolean redirect) {


        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int lockTimeout = Integer.valueOf(sharedPrefs.getString(LAST_LOCK_TIME, "60")) * 1000;

        long currentTimestamp = System.currentTimeMillis();
        long lockedTime = activity.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(LAST_LOCK_TIME, 0);

        if (lockedTime != 0) {
            if (currentTimestamp - lockedTime > lockTimeout) {
                logout(activity, redirect);
            }
        }

        if (SafeCameraApplication.getKey() == null) {

            final Activity activityFinal = activity;

            SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean isFingerprintSetup = defaultSharedPrefs.getBoolean(FINGERPRINT_PREFERENCE, false);

            if(isFingerprintSetup) {

                FingerprintManagerWrapper fingerprintManager = new FingerprintManagerWrapper(activity);
                fingerprintManager.unlock(new FingerprintManagerWrapper.PasswordReceivedHandler() {
                    @Override
                    public void onPasswordReceived(String password) {
                        unlockWithPassword(activityFinal, password, loginCallback);
                    }
                }, loginCallback);
            }
            else{
                showEnterPasswordToUnlock(activity, loginCallback);
            }
        }
        else {
            if(loginCallback != null) {
                loginCallback.onUserLoginSuccess();
            }
        }
    }

    public static void showEnterPasswordToUnlock(Activity activity, final UserLogedinCallback loginCallback){
        final Activity activityFinal = activity;
        getPasswordFromUser(activity, new PasswordReturnListener() {
            @Override
            public void passwordReceived(String enteredPassword) {
                unlockWithPassword(activityFinal, enteredPassword, loginCallback);
            }

            @Override
            public void passwordCanceled() {

            }
        });
    }

    public static void unlockWithPassword(Activity activity, String password, UserLogedinCallback loginCallback){

        /*SharedPreferences preferences = activity.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
        if (!preferences.contains(SafeCameraApplication.PASSWORD)) {
            redirectToSetup(activity);
            return;
        }
        String savedHash = preferences.getString(SafeCameraApplication.PASSWORD, "");*/
        try{
           /* if(!SafeCameraApplication.getCrypto().verifyStoredPassword(savedHash, password)){

                return;
            }
            */
            SafeCameraApplication.setKey(SafeCameraApplication.getCrypto().getPrivateKey(password));
            if(loginCallback != null) {
                loginCallback.onUserLoginSuccess();
            }
        }
        catch (CryptoException e) {
            if(loginCallback != null) {
                loginCallback.onUserLoginFail();
            }
            Helpers.showAlertDialog(activity, activity.getString(R.string.incorrect_password));
        }
    }

    public static void getPasswordFromUser(Context context, final PasswordReturnListener listener){
        final Context myContext = context;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(R.layout.password);
        final AlertDialog dialog = builder.create();
        dialog.show();

        Button okButton = (Button)dialog.findViewById(R.id.okButton);
        final EditText passwordField = (EditText)dialog.findViewById(R.id.password);
        Button cancelButton = (Button)dialog.findViewById(R.id.cancelButton);

        final InputMethodManager imm = (InputMethodManager) myContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                listener.passwordReceived(passwordField.getText().toString());
                dialog.dismiss();
            }
        });

        passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    listener.passwordReceived(passwordField.getText().toString());
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });


        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                listener.passwordCanceled();
                dialog.cancel();
            }
        });
    }

    public static void setLockedTime(Context context) {
        context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, System.currentTimeMillis()).commit();
    }

    public static void disableLockTimer(Context context) {
        context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, 0).commit();
    }

    public static void logout(Activity activity){
        logout(activity, true);
    }
    public static void logout(Activity activity, boolean redirect){
        /*Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("com.fenritz.safecam.ACTION_LOGOUT");
        activity.sendBroadcast(broadcastIntent);*/

        SafeCameraApplication.setKey(null);

        Helpers.deleteTmpDir(activity);

        if(redirect) {
            //redirectToDashboard(activity);
        }
    }

    public static void redirectToDashboard(Activity activity) {
        Intent intent = new Intent();
        intent.setClass(activity, DashboardActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }
    public static void redirectToSetup(Activity activity) {
        Intent intent = new Intent();
        intent.setClass(activity, SetUpActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public static abstract class UserLogedinCallback{
        public abstract void onUserLoginSuccess();
        public abstract void onUserLoginFail();
    }
}
