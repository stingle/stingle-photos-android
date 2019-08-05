package com.fenritz.safecam.Auth;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import com.fenritz.safecam.GalleryActivity;
import com.fenritz.safecam.LoginActivity;
import com.fenritz.safecam.Net.HttpsClient;
import com.fenritz.safecam.Net.StingleResponse;
import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.SetUpActivity;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;

import java.util.HashMap;

public class LoginManager {

    public static final String FINGERPRINT_PREFERENCE = "fingerprint";
    public static final String LAST_LOCK_TIME = "lock_time";
    public static final String LOGINS_COUNT_FOR_POPUP = "logins_count_fp";
    public static final String DONT_SHOW_POPUP = "dont_show_popup";
    public static final String POPUP_LATERS_COUNT = "laters_count";

    private static AlertDialog dialog = null;

    public LoginManager(){

    }

    public static void checkLogin(Activity activity) {
        checkLogin(activity, null);
    }

    public static void checkLogin(final Activity activity, final UserLogedinCallback loginCallback) {

        if(!checkIfLoggedIn(activity)){
            return;
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int lockTimeout = Integer.valueOf(sharedPrefs.getString(LAST_LOCK_TIME, "60")) * 1000;

        long currentTimestamp = System.currentTimeMillis();
        long lockedTime = activity.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(LAST_LOCK_TIME, 0);

        if (lockedTime != 0) {
            if (currentTimestamp - lockedTime > lockTimeout) {
                lock(activity);
            }
        }

        if (SafeCameraApplication.getKey() == null) {


            SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean isFingerprintSetup = defaultSharedPrefs.getBoolean(FINGERPRINT_PREFERENCE, false);

            if(isFingerprintSetup) {

                FingerprintManagerWrapper fingerprintManager = new FingerprintManagerWrapper(activity);
                fingerprintManager.unlock(new FingerprintManagerWrapper.PasswordReceivedHandler() {
                    @Override
                    public void onPasswordReceived(String password) {
                        unlockWithPassword(activity, password, loginCallback);
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
        });
    }

    public static void unlockWithPassword(Activity activity, String password, UserLogedinCallback loginCallback){


        try{
            SafeCameraApplication.setKey(SafeCameraApplication.getCrypto().getPrivateKey(password));
            if(loginCallback != null) {
                if(dialog!=null) {
                    dialog.dismiss();
                    dialog = null;
                }
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

    public static boolean isKeyInMemory(){
        return (SafeCameraApplication.getKey() == null ? false : true);
    }

    public static void getPasswordFromUser(final Activity activity, final PasswordReturnListener listener){
        final Context myContext = activity;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(R.layout.password);
        builder.setCancelable(false);
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface mDialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK){
                    if(dialog != null){
                        dialog.dismiss();
                        dialog = null;
                    }
                    activity.finish();
                    return true;
                }
                return false;
            }
        });
        if(dialog != null){
            dialog.dismiss();
            dialog = null;
        }
        dialog = builder.create();
        dialog.show();

        Button okButton = (Button)dialog.findViewById(R.id.okButton);
        final EditText passwordField = (EditText)dialog.findViewById(R.id.password);
        Button logoutButton = (Button)dialog.findViewById(R.id.logoutButton);

        final InputMethodManager imm = (InputMethodManager) myContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                listener.passwordReceived(passwordField.getText().toString());
            }
        });

        passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    listener.passwordReceived(passwordField.getText().toString());
                    return true;
                }
                return false;
            }
        });


        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
               LoginManager.logout(activity);
            }
        });
    }

    public static boolean checkIfLoggedIn(Activity activity){
        String email = Helpers.getPreference(activity, SafeCameraApplication.USER_EMAIL, null);
        String token = KeyManagement.getApiToken(activity);

        if(email == null || token == null){
            redirectToLogin(activity);
            return false;
        }

        return true;
    }

    public static void setLockedTime(Context context) {
        context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, System.currentTimeMillis()).commit();
    }

    public static void disableLockTimer(Context context) {
        context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, 0).commit();
    }


    public static void lock(final Activity activity){
        SafeCameraApplication.setKey(null);
        Helpers.deleteTmpDir(activity);
    }

    public static void logout(final Activity activity){
        Helpers.showConfirmDialog(activity, activity.getString(R.string.confirm_logout), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface mDialog, int which) {
                final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.logging_out), null);
                HashMap<String, String> postParams = new HashMap<String, String>();
                postParams.put("token", KeyManagement.getApiToken(activity));
                HttpsClient.post(activity, activity.getString(R.string.api_server_url) + activity.getString(R.string.logout_path), postParams, new HttpsClient.OnNetworkFinish() {
                    @Override
                    public void onFinish(StingleResponse response) {
                        if (response.isStatusOk()) {
                            SafeCameraApplication.setKey(null);
                            KeyManagement.deleteLocalKeys();
                            Helpers.storePreference(activity, SafeCameraApplication.USER_EMAIL, null);
                            Helpers.deleteTmpDir(activity);
                            spinner.dismiss();
                            if(dialog != null){
                                dialog.dismiss();
                                dialog = null;
                            }
                            if(FingerprintHandler.dialog != null){
                                FingerprintHandler.dialog.dismiss();
                                FingerprintHandler.dialog = null;
                            }
                            redirectToLogin(activity);
                        }
                    }
                });
            }
        }, null);
    }

    public static void redirectToLogin(Activity activity) {
        Intent intent = new Intent();
        intent.setClass(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
    }


    public static abstract class UserLogedinCallback{
        public abstract void onUserLoginSuccess();
        public abstract void onUserLoginFail();
    }
}
