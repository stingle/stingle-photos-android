package org.stingle.photos.Auth;

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

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.WelcomeActivity;

import java.util.HashMap;

public class LoginManager {

    public static final String BIOMETRIC_PREFERENCE = "biometrics";
    public static final String LAST_LOCK_TIME = "lock_time";

    public static void checkLogin(AppCompatActivity activity) {
        checkLogin(activity, null);
    }

    public static void checkLogin(final AppCompatActivity activity, final UserLogedinCallback loginCallback) {

        if(!checkIfLoggedIn(activity)){
            if(loginCallback != null) {
                loginCallback.onNotLoggedIn();
            }
            return;
        }

        if(loginCallback != null) {
            loginCallback.onLoggedIn();
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int lockTimeout = Integer.valueOf(sharedPrefs.getString(LAST_LOCK_TIME, "60")) * 1000;

        long currentTimestamp = System.currentTimeMillis();
        long lockedTime = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(LAST_LOCK_TIME, 0);

        if (lockedTime != 0) {
            if (currentTimestamp - lockedTime > lockTimeout) {
                lock(activity);
            }
        }

        if (StinglePhotosApplication.getKey() == null) {


            SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean isBiometricSetup = defaultSharedPrefs.getBoolean(BIOMETRIC_PREFERENCE, false);

            if(isBiometricSetup) {

                BiometricsManagerWrapper biometricsManagerWrapper = new BiometricsManagerWrapper(activity);
                biometricsManagerWrapper.unlock(new BiometricsManagerWrapper.BiometricsCallback() {
                    @Override
                    public void onPasswordReceived(String password) {
                        unlockWithPassword(activity, password, loginCallback);
                    }

                    @Override
                    public void onAuthUsingPassword() {
                        showEnterPasswordToUnlock(activity, loginCallback);
                    }

                    @Override
                    public void onAuthFailed() {
                        loginCallback.onUserAuthFail();
                        activity.finish();
                    }
                }, loginCallback, true);
            }
            else{
                showEnterPasswordToUnlock(activity, loginCallback);
            }
        }
        else {
            if(loginCallback != null) {
                loginCallback.onUserAuthSuccess();
            }
        }
    }

    public static void dismissLoginDialog(AlertDialog dialog){
        if(dialog != null){
            dialog.dismiss();
        }
    }

    public static void showEnterPasswordToUnlock(Activity activity, final UserLogedinCallback loginCallback){
        final Activity activityFinal = activity;
        getPasswordFromUser(activity, true, new PasswordReturnListener() {
            @Override
            public void passwordReceived(String enteredPassword, AlertDialog dialog) {
                unlockWithPassword(activityFinal, enteredPassword, new UserLogedinCallback() {
                    @Override
                    public void onUserAuthSuccess() {
                        dismissLoginDialog(dialog);
                        loginCallback.onUserAuthSuccess();
                    }

                    @Override
                    public void onUserAuthFail() {
                        if(dialog != null) {
                            ((EditText) dialog.findViewById(R.id.password)).setText("");
                        }
                        loginCallback.onUserAuthFail();
                    }

                    @Override
                    public void onNotLoggedIn() {
                        loginCallback.onNotLoggedIn();
                    }

                    @Override
                    public void onLoggedIn() {
                        loginCallback.onLoggedIn();
                    }
                });
            }

            @Override
            public void passwordReceiveFailed(AlertDialog dialog) {
                dismissLoginDialog(dialog);
            }
        });
    }

    public static void unlockWithPassword(Activity activity, String password, UserLogedinCallback loginCallback){
        try{
            StinglePhotosApplication.setKey(StinglePhotosApplication.getCrypto().getPrivateKey(password));
            if(loginCallback != null) {
                loginCallback.onUserAuthSuccess();
            }
        }
        catch (CryptoException e) {
            if(loginCallback != null) {
                loginCallback.onUserAuthFail();
            }
            Helpers.showAlertDialog(activity, activity.getString(R.string.incorrect_password));
        }
    }

    public static boolean isKeyInMemory(){
        return (StinglePhotosApplication.getKey() == null ? false : true);
    }

    public static void getPasswordFromUser(final Activity activity, final boolean showLogout, final PasswordReturnListener listener){
        final Context myContext = activity;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(R.layout.password);
        builder.setCancelable(showLogout == false);
        AlertDialog dialog = builder.create();
        dialog.show();

        Button okButton = dialog.findViewById(R.id.okButton);
        final EditText passwordField = dialog.findViewById(R.id.password);
        Button logoutButton = dialog.findViewById(R.id.logoutButton);

        final InputMethodManager imm = (InputMethodManager) myContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);

        okButton.setOnClickListener(v -> {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            listener.passwordReceived(passwordField.getText().toString(), dialog);
        });

        dialog.setOnCancelListener(dialog1 -> listener.passwordReceiveFailed(dialog));

        dialog.setOnKeyListener((mDialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK){
                dialog.dismiss();
                if(showLogout) {
                    activity.finish();
                }
                return true;
            }
            return false;
        });

        passwordField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                listener.passwordReceived(passwordField.getText().toString(), dialog);
                if(dialog != null){
                    dialog.dismiss();
                }
                return true;
            }
            return false;
        });


        if(showLogout) {
            logoutButton.setOnClickListener(v -> {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                if(dialog != null){
                    dialog.dismiss();
                }
                LoginManager.logout(activity);
            });
        }
        else{
            logoutButton.setVisibility(View.GONE);
        }
    }

    public static boolean isLoggedIn(Context context){
        String email = Helpers.getPreference(context, StinglePhotosApplication.USER_EMAIL, null);
        String token = KeyManagement.getApiToken(context);

        if(email == null || token == null){
            return false;
        }

        return true;
    }

    public static boolean checkIfLoggedIn(Activity activity){
        if(!isLoggedIn(activity)){
            redirectToLogin(activity);
            return false;
        }

        return true;
    }

    public static void setLockedTime(Context context) {
        context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, System.currentTimeMillis()).apply();
    }

    public static void disableLockTimer(Context context) {
        context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_LOCK_TIME, 0).apply();
    }


    public static void lock(final Activity activity){
        StinglePhotosApplication.setKey(null);
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
                            spinner.dismiss();
                            logoutLocally(activity);
                        }
                    }
                });
            }
        }, null);
    }

    public static void logoutLocally(Context context){
        if(!isLoggedIn(context)){
            return;
        }
        StinglePhotosApplication.setKey(null);
        KeyManagement.deleteLocalKeys();
        KeyManagement.removeApiToken(context);
        BiometricsManagerWrapper.turnOffBiometrics(context);
        Helpers.storePreference(context, StinglePhotosApplication.USER_EMAIL, null);
        Helpers.deleteTmpDir(context);

        SyncManager.resetAndStopSync(context);

        Intent intent = new Intent(context, SyncService.class);
        context.stopService(intent);

        if(context instanceof Activity){
            redirectToLogin((Activity)context);
        }

        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("ACTION_LOGOUT"));
    }

    public static void redirectToLogin(Activity activity) {
        Intent intent = new Intent();
        intent.setClass(activity, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    public static abstract class UserLogedinCallback{
        public abstract void onUserAuthSuccess();
        public abstract void onUserAuthFail();
        public abstract void onNotLoggedIn();
        public abstract void onLoggedIn();
    }
}
