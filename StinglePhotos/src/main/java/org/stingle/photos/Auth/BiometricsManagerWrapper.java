package org.stingle.photos.Auth;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
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
    private final String RANDOM_ALGORITHM = "SHA1PRNG";
    public static final int IV_LENGTH = 16;
    private final Executor executor;
    private final BiometricPrompt.PromptInfo promptInfo;

    protected AppCompatActivity activity;
    protected KeyguardManager keyguardManager;
    protected KeyStore keyStore;
    protected KeyGenerator keyGenerator;
    private FingerprintManager.CryptoObject cryptoObject;
    private BiometricManager biometricManager;

    public BiometricsManagerWrapper(AppCompatActivity activity){
        this.activity = activity;

        biometricManager = BiometricManager.from(activity);
        keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        executor = ContextCompat.getMainExecutor(activity);

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_auth))
                .setSubtitle(activity.getString(R.string.biometric_login_desc))
                .setNegativeButtonText(activity.getString(R.string.login_using_password))
                .build();
    }

    public boolean isBiometricsAvailable(){
        if(biometricManager.canAuthenticate() != BiometricManager.BIOMETRIC_SUCCESS){
            return true;
        }
        return false;
    }


    public boolean setupBiometrics(Preference preference){
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

                    LoginManager.getPasswordFromUser(activity, false, new PasswordReturnListener() {
                        @Override
                        public void passwordReceived(String password) {
                            try {
                                try{
                                    StinglePhotosApplication.getCrypto().getPrivateKey(password);
                                }
                                catch (CryptoException e) {
                                    Helpers.showAlertDialog(activity, activity.getString(R.string.incorrect_password));
                                    return;
                                }

                                SharedPreferences preferences = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
                                byte[] encPassword = Objects.requireNonNull(cipher).doFinal(password.getBytes());

                                preferences.edit().putString(StinglePhotosApplication.PASSWORD_BIOMETRICS, Crypto.byte2hex(encPassword)).apply();
                                preferences.edit().putString(StinglePhotosApplication.PASSWORD_BIOMETRICS_IV, Crypto.byte2hex(iv)).apply();

                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
                                defaultSharedPrefs.edit().putBoolean(LoginManager.BIOMETRIC_PREFERENCE, true).apply();
                                ((SwitchPreference) preferenceFinal).setChecked(true);

                                LoginManager.dismissLoginDialog();
                            } catch (BadPaddingException | IllegalBlockSizeException e) {
                                Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_setup));
                            }
                        }
                    });
                }


                @Override
                public void onAuthenticationError(int errorCode,
                                                  @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Toast.makeText(activity,
                            "Authentication error: " + errString, Toast.LENGTH_SHORT)
                            .show();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(activity, "Authentication failed",
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
            biometricPrompt.authenticate(promptInfo,
                    new BiometricPrompt.CryptoObject(cipher));



        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | IOException | CertificateException | UnrecoverableKeyException | InvalidKeyException | NoSuchPaddingException | KeyStoreException e) {
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

    public boolean unlock(final PasswordReceivedHandler passwordHandler, final LoginManager.UserLogedinCallback loginCallback, boolean showLogout){
        try{
            SharedPreferences preferences = activity.getSharedPreferences(StinglePhotosApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
            String storedIvHex = preferences.getString(StinglePhotosApplication.PASSWORD_BIOMETRICS_IV, "");
            byte[] storedIv = Crypto.hex2byte(storedIvHex);

            Cipher cipher = getCipher();
            SecretKey secretKey = getSecretKey();
            AlgorithmParameterSpec ivParamSpec = new IvParameterSpec(storedIv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParamSpec);

            BiometricPrompt biometricPrompt = new BiometricPrompt((FragmentActivity) activity,
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

                        passwordHandler.onPasswordReceived(decPass);

                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                        Helpers.showAlertDialog(activity, activity.getString(R.string.failed_biometrics_auth));
                    }
                }


                @Override
                public void onAuthenticationError(int errorCode,
                                                  @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Toast.makeText(activity,
                            "Authentication error: " + errString, Toast.LENGTH_SHORT)
                            .show();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(activity, "Authentication failed",
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
            biometricPrompt.authenticate(promptInfo,
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





    protected void showError(String title, String description){
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(description);
        builder.setPositiveButton(activity.getString(R.string.ok), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static abstract class PasswordReceivedHandler{
        public abstract void onPasswordReceived(String password);
    }
}
