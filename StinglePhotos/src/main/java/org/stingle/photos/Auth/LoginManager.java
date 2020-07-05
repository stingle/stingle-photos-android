package org.stingle.photos.Auth;

import android.app.Activity;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Net.HttpsClient;
import org.stingle.photos.Net.StingleResponse;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Sync.SyncService;
import org.stingle.photos.Util.Helpers;
import org.stingle.photos.WelcomeActivity;

import java.lang.ref.WeakReference;
import java.util.HashMap;

public class LoginManager {

    public static final String BIOMETRIC_PREFERENCE = "biometrics";
    public static final String LAST_LOCK_TIME = "lock_time";
    private static WeakReference<AlertDialog> loginDialogRef;

    public static void checkLogin(AppCompatActivity activity) {
        checkLogin(activity, new LoginConfig(), null);
    }

    public static void checkLogin(AppCompatActivity activity, UserLogedinCallback loginCallback) {
        checkLogin(activity, new LoginConfig(), loginCallback);
    }

    public static void checkLogin(final AppCompatActivity activity, LoginConfig loginConfig, final UserLogedinCallback loginCallback) {

        if(!checkIfLoggedIn(activity)){
            if(loginCallback != null) {
                loginCallback.onNotLoggedIn();
            }
            return;
        }

        if(loginCallback != null) {
            loginCallback.onLoggedIn();
        }

        if(loginConfig == null){
            loginConfig = new LoginConfig();
        }
        loginConfig.okButtonText = activity.getString(R.string.unlock);
        loginConfig.cancelButtonText = activity.getString(R.string.exit);
        loginConfig.showCancel = true;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        int lockTimeout = Integer.valueOf(sharedPrefs.getString(LAST_LOCK_TIME, "60")) * 1000;

        long currentTimestamp = System.currentTimeMillis();
        long lockedTime = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE).getLong(LAST_LOCK_TIME, 0);

        if (lockedTime != 0) {
            if (currentTimestamp - lockedTime > lockTimeout) {
                lock(activity);
            }
        }

        final LoginConfig loginConfigF = loginConfig;

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
                        showEnterPasswordToUnlock(activity, loginConfigF, loginCallback);
                    }

                    @Override
                    public void onAuthFailed() {
                        loginCallback.onUserAuthFail();
                        activity.finish();
                    }
                }, loginCallback, true);
            }
            else{
                showEnterPasswordToUnlock(activity, loginConfigF, loginCallback);
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
        showEnterPasswordToUnlock(activity, new LoginConfig(), loginCallback);
    }

    public static void showEnterPasswordToUnlock(Activity activity, LoginConfig config, final UserLogedinCallback loginCallback){
        final Activity activityFinal = activity;
        getPasswordFromUser(activity, config, new PasswordReturnListener() {
            @Override
            public void passwordReceived(String enteredPassword, AlertDialog dialog) {
                unlockWithPassword(activityFinal, enteredPassword, new UserLogedinCallback() {
                    @Override
                    public void onUserAuthSuccess() {
                        dismissLoginDialog(dialog);
                        if(loginCallback != null) {
                            loginCallback.onUserAuthSuccess();
                        }
                    }

                    @Override
                    public void onUserAuthFail() {
                        if(dialog != null) {
                            ((EditText) dialog.findViewById(R.id.password)).setText("");
                        }
                        if(loginCallback != null) {
                            loginCallback.onUserAuthFail();
                        }
                    }
                });
            }

            @Override
            public void passwordReceiveFailed(AlertDialog dialog) {
                dismissLoginDialog(dialog);
                if(loginCallback != null) {
                    loginCallback.onLoginCancelled();
                }
                if(config.quitActivityOnCancel) {
                    activity.finish();
                }
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
            Helpers.showAlertDialog(activity, activity.getString(R.string.error), activity.getString(R.string.incorrect_password));
        }
    }

    public static boolean isKeyInMemory(){
        return (StinglePhotosApplication.getKey() == null ? false : true);
    }

    public static void getPasswordFromUser(final Activity activity, LoginConfig config, final PasswordReturnListener listener){
        final Context myContext = activity;

        if(loginDialogRef != null && loginDialogRef.get() != null){
            loginDialogRef.get().dismiss();
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(R.layout.dialog_unlock_password);
        builder.setCancelable(config.cancellable);
        AlertDialog loginDialog = builder.create();
        loginDialogRef = new WeakReference<>(loginDialog);
        loginDialog.show();

        Button okButton = loginDialog.findViewById(R.id.okButton);
        final EditText passwordField = loginDialog.findViewById(R.id.password);
        Button logoutButton = loginDialog.findViewById(R.id.logoutButton);
        Button cancelButton = loginDialog.findViewById(R.id.cancelButton);

        final InputMethodManager imm = (InputMethodManager) myContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }

        if(config.okButtonText != null) {
            okButton.setText(config.okButtonText);
        }
        if(config.cancelButtonText != null) {
            cancelButton.setText(config.cancelButtonText);
        }
        if(config.titleText != null) {
            ((TextView)loginDialog.findViewById(R.id.titleTextView)).setText(config.titleText);
        }
        if(config.subTitleText != null) {
            ((TextView)loginDialog.findViewById(R.id.subtitleTextView)).setText(config.subTitleText);
        }
        if(!config.showLockIcon) {
            loginDialog.findViewById(R.id.loginLockIcon).setVisibility(View.GONE);
        }


        okButton.setOnClickListener(v -> {
            if(imm != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            listener.passwordReceived(passwordField.getText().toString(), loginDialog);
        });

        passwordField.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    if(imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    listener.passwordReceived(passwordField.getText().toString(), loginDialog);
                    return true;
                }
                return false;
            }
        });

        loginDialog.setOnCancelListener(dialog1 -> listener.passwordReceiveFailed(loginDialog));
        loginDialog.setOnKeyListener((mDialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK){
                loginDialog.dismiss();
                listener.passwordReceiveFailed(loginDialog);
                return true;
            }
            return false;
        });

        passwordField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                if(imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                listener.passwordReceived(passwordField.getText().toString(), loginDialog);
                if(loginDialog != null){
                    loginDialog.dismiss();
                }
                return true;
            }
            return false;
        });


        if(config.showLogout) {
            logoutButton.setOnClickListener(v -> {
                if(imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                if(loginDialog != null){
                    loginDialog.dismiss();
                }
                LoginManager.logout(activity, (dialog12, which) -> {
                    listener.passwordReceiveFailed(loginDialog);
                }, (dialog13, which) -> {
                    listener.passwordReceiveFailed(loginDialog);
                });

            });
        }
        else{
            logoutButton.setVisibility(View.GONE);
        }

        if(config.showCancel) {
            cancelButton.setOnClickListener(v -> {
                if(imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
                if(loginDialog != null){
                    loginDialog.dismiss();
                }
                listener.passwordReceiveFailed(loginDialog);
            });
        }
        else{
            cancelButton.setVisibility(View.GONE);
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
        logout(activity, null,null);
    }

    public static void logout(final Activity activity, DialogInterface.OnClickListener yes, DialogInterface.OnClickListener no){
        Helpers.showConfirmDialog(activity, activity.getString(R.string.log_out), activity.getString(R.string.confirm_logout), R.drawable.ic_logout, (mDialog, which) -> {
            final ProgressDialog spinner = Helpers.showProgressDialog(activity, activity.getString(R.string.logging_out), null);
            HashMap<String, String> postParams = new HashMap<String, String>();
            postParams.put("token", KeyManagement.getApiToken(activity));
            HttpsClient.post(activity, StinglePhotosApplication.getApiUrl() + activity.getString(R.string.logout_path), postParams, new HttpsClient.OnNetworkFinish() {
                @Override
                public void onFinish(StingleResponse response) {
                    if (response.isStatusOk()) {
                        spinner.dismiss();
                        logoutLocally(activity);
                    }
                    if(yes != null) {
                        yes.onClick(mDialog, which);
                    }
                }
            });
        }, no);
    }

    public static void logoutLocally(Context context){
        if(!isLoggedIn(context)){
            return;
        }
        StinglePhotosApplication.setKey(null);
        KeyManagement.deleteLocalKeys();
        KeyManagement.removeApiToken(context);
        BiometricsManagerWrapper.turnOffBiometrics(context);
        Helpers.deleteAllPreferences(context);
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
        public void onUserAuthSuccess(){}
        public void onUserAuthFail(){}
        public void onNotLoggedIn(){}
        public void onLoggedIn(){}
        public void onLoginCancelled(){}
    }

    public static class LoginConfig{
        public boolean showLogout = true;
        public boolean showCancel = false;
        public boolean quitActivityOnCancel = true;
        public boolean cancellable = false;
        public boolean showLockIcon = true;
        public String okButtonText = null;
        public String cancelButtonText = null;
        public String titleText = null;
        public String subTitleText = null;
    }
}
