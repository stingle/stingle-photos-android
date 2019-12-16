package org.stingle.photos.Auth;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Util.Helpers;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class BiometricsManagerWrapper {

    private static final String KEY_NAME = "sc_key";
    private final Executor executor;

    protected AppCompatActivity activity;
    protected KeyguardManager keyguardManager;
    private BiometricManager biometricManager;

    public BiometricsManagerWrapper(AppCompatActivity activity){
        this.activity = activity;

        biometricManager = BiometricManager.from(activity);
        keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        executor = ContextCompat.getMainExecutor(activity);
    }

    public boolean isBiometricsAvailable(){
        if(biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS){
            return true;
        }
        return false;
    }

    private BiometricPrompt.PromptInfo getPrompt(boolean showPasswordOption){
        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_auth))
                .setSubtitle(activity.getString(R.string.biometric_login_desc));
        if(showPasswordOption) {
            promptBuilder.setNegativeButtonText(activity.getString(R.string.login_using_password));
        }
        else{
            promptBuilder.setNegativeButtonText(activity.getString(R.string.cancel));
        }
        return promptBuilder.build();
    }


    public boolean setupBiometrics(Preference preference, String password){
        return setupBiometrics(preference, password, new BiometricsSetupCallback() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onFailed() {

            }
        });
    }

    public boolean setupBiometrics(Preference preference, String password, @NonNull BiometricsSetupCallback callback){
        final Preference preferenceFinal = preference;

        try {

            KeyGenParameterSpec.Builder keyGenParameterSpecBuilder = new KeyGenParameterSpec.Builder(KEY_NAME,KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                keyGenParameterSpecBuilder.setInvalidatedByBiometricEnrollment(true);
            }
            generateSecretKey(keyGenParameterSpecBuilder.build());

            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey();
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                    executor, new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationSucceeded(
                        @NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);

                    Cipher cipher = result.getCryptoObject().getCipher();

                    if(password != null){
                        if(encryptAndSavePassword(cipher, iv, password)) {
                            if (preferenceFinal != null) {
                                ((SwitchPreference) preferenceFinal).setChecked(true);
                            }
                            callback.onSuccess();
                        }
                        else {
                            callback.onFailed();
                        }
                    }
                    LoginManager.getPasswordFromUser(activity, false, new PasswordReturnListener() {
                        @Override
                        public void passwordReceived(String enteredPassword, AlertDialog dialog) {
                            if(encryptAndSavePassword(cipher, iv, enteredPassword)) {
                                if (preferenceFinal != null) {
                                    ((SwitchPreference) preferenceFinal).setChecked(true);
                                }

                                LoginManager.dismissLoginDialog(dialog);
                                callback.onSuccess();
                            }
                            else{
                                ((EditText) dialog.findViewById(R.id.password)).setText("");
                                callback.onFailed();
                            }
                        }

                        @Override
                        public void passwordReceiveFailed(AlertDialog dialog) {
                            LoginManager.dismissLoginDialog(dialog);
                        }
                    });
                }


                @Override
                public void onAuthenticationError(int errorCode,
                                                  @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    callback.onFailed();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    callback.onFailed();
                }
            });

            biometricPrompt.authenticate(getPrompt(false),
                    new BiometricPrompt.CryptoObject(cipher));

            return true;
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | IOException | CertificateException | UnrecoverableKeyException | InvalidKeyException | NoSuchPaddingException | KeyStoreException e) {
            Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_setup));
            callback.onFailed();
        }

        return false;
    }

    private boolean encryptAndSavePassword(Cipher cipher, byte[] iv, String password){
        try {
            try{
                StinglePhotosApplication.getCrypto().getPrivateKey(password);
            }
            catch (CryptoException e) {
                Helpers.showAlertDialog(activity, activity.getString(R.string.incorrect_password));
                return false;
            }

            SharedPreferences preferences = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
            byte[] encPassword = Objects.requireNonNull(cipher).doFinal(password.getBytes());

            preferences.edit().putString(StinglePhotosApplication.PASSWORD_BIOMETRICS, Crypto.byte2hex(encPassword)).apply();
            preferences.edit().putString(StinglePhotosApplication.PASSWORD_BIOMETRICS_IV, Crypto.byte2hex(iv)).apply();

            SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
            defaultSharedPrefs.edit().putBoolean(LoginManager.BIOMETRIC_PREFERENCE, true).apply();

            return true;
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_setup));
        }
        return false;
    }

    public static void turnOffBiometrics(Context context){
        SharedPreferences preferences = context.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
        preferences.edit().remove(StinglePhotosApplication.PASSWORD_BIOMETRICS).apply();
        preferences.edit().remove(StinglePhotosApplication.PASSWORD_BIOMETRICS_IV).apply();

        SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        defaultSharedPrefs.edit().putBoolean("biometrics", false).apply();
    }

    public boolean unlock(final BiometricsCallback biometricsCallback, final LoginManager.UserLogedinCallback loginCallback, boolean showLogout){
        try{
            SharedPreferences preferences = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
            String storedIvHex = preferences.getString(StinglePhotosApplication.PASSWORD_BIOMETRICS_IV, "");
            byte[] storedIv = Crypto.hex2byte(storedIvHex);

            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey();
            AlgorithmParameterSpec ivParamSpec = new IvParameterSpec(storedIv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParamSpec);

            BiometricPrompt biometricPrompt = new BiometricPrompt(activity,
                    executor, new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationSucceeded(
                        @NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);

                    Cipher cipher = result.getCryptoObject().getCipher();

                    String encPass = preferences.getString(StinglePhotosApplication.PASSWORD_BIOMETRICS, "");

                    try {
                        byte[] decPassBytes = Objects.requireNonNull(cipher).doFinal(Crypto.hex2byte(encPass));
                        String decPass = new String(decPassBytes);

                        biometricsCallback.onPasswordReceived(decPass);

                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                        Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_auth));
                    }
                }


                @Override
                public void onAuthenticationError(int errorCode,
                                                  @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if(errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON){
                        biometricsCallback.onAuthUsingPassword();
                    }
                    else{
                        biometricsCallback.onAuthFailed();
                    }
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(activity, "Authentication failed",
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
            biometricPrompt.authenticate(getPrompt(true),
                    new BiometricPrompt.CryptoObject(cipher));
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | IOException | CertificateException | UnrecoverableKeyException | InvalidKeyException | NoSuchPaddingException | KeyStoreException e) {
            Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_auth));
        }
        return false;
    }

    private void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) throws InvalidAlgorithmParameterException, NoSuchProviderException, NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(keyGenParameterSpec);
        keyGenerator.generateKey();
    }

    private SecretKey getSecretKey() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");

        // Before the keystore can be accessed, it must be loaded.
        keyStore.load(null);
        return ((SecretKey)keyStore.getKey(KEY_NAME, null));
    }

    private Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    }

    public static abstract class BiometricsCallback{
        public abstract void onPasswordReceived(String password);
        public abstract void onAuthUsingPassword();
        public abstract void onAuthFailed();
    }

    public static abstract class BiometricsSetupCallback{
        public abstract void onSuccess();
        public abstract void onFailed();
    }
}
