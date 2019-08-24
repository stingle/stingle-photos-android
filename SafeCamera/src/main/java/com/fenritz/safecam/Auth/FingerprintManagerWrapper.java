package com.fenritz.safecam.Auth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;

import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;
import com.fenritz.safecam.Crypto.CryptoException;
import com.fenritz.safecam.Util.Helpers;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class FingerprintManagerWrapper {

    private static final String KEY_NAME = "sc_key";
    private final String RANDOM_ALGORITHM = "SHA1PRNG";
    public static final int IV_LENGTH = 16;
    private byte[] iv;

    protected Activity context;
    protected KeyguardManager keyguardManager;
    protected KeyStore keyStore;
    protected KeyGenerator keyGenerator;
    protected Cipher cipher;
    private FingerprintManager.CryptoObject cryptoObject;
    private FingerprintManager fingerprintManager;

    public FingerprintManagerWrapper(Activity context){
        this.context = context;

        keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        fingerprintManager = (android.hardware.fingerprint.FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
    }

    public boolean isFingerprintAvailable(){
        if (fingerprintManager.isHardwareDetected()) {
            return true;
        }
        return false;
    }

    private byte[] generateIv() {
        SecureRandom random;
        try {
            random = SecureRandom.getInstance(RANDOM_ALGORITHM);
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            return iv;
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new byte[IV_LENGTH];
    }

    public boolean setupFingerprint(Preference preference){
        final Preference preferenceFinal = preference;
        // Obtain a reference to the Keystore using the standard Android keystore container identifier (“AndroidKeystore”)//
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            return false;
        }
        //Check whether the user has granted your app the USE_FINGERPRINT permission//
        if (context.checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            // If your app doesn't have this permission, then display the following text//
            showError(context.getString(R.string.error_fingerprint_perm), null);
            return false;
        }

        //Check that the user has registered at least one fingerprint//
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            // If the user hasn’t configured any fingerprints, then display the following message//
            showError(context.getString(R.string.error_fingerprint_enroll), null);
            return false;
        }

        //Check that the lockscreen is secured//
        if (!keyguardManager.isKeyguardSecure()) {
            // If the user hasn’t secured their lockscreen with a PIN password or pattern, then display the following text//
            showError(context.getString(R.string.error_fingerprint_screenguard), null);
            return false;
        }

        try {
            generateKey();
        }
        catch (FingerprintException e) {
            e.printStackTrace();
            return false;
        }

        if (initCipherEncrypt()) {

            //If the cipher is initialized successfully, then create a CryptoObject instance//
            cryptoObject = new FingerprintManager.CryptoObject(cipher);

            // Here, I’m referencing the FingerprintHandler class that we’ll create in the next section. This class will be responsible
            // for starting the authentication process (via the startAuth method) and processing the authentication process events//
            FingerprintHandler helper = new FingerprintHandler(context, new FingerprintHandler.OnFingerprintSuccess() {
                @Override
                public void onSuccess(final Cipher cipher) {
                    LoginManager.getPasswordFromUser(context, false, new PasswordReturnListener() {
                        @Override
                        public void passwordReceived(String password) {
                            try {
                                try{
                                    SafeCameraApplication.getCrypto().getPrivateKey(password);
                                }
                                catch (CryptoException e) {
                                    Helpers.showAlertDialog(context, context.getString(R.string.incorrect_password));
                                    return;
                                }

                                SharedPreferences preferences = context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
                                byte[] encPassword = cipher.doFinal(password.getBytes());

                                preferences.edit().putString(SafeCameraApplication.PASSWORD_FINGERPRINT, SafeCameraApplication.getCrypto().byte2hex(encPassword)).commit();
                                preferences.edit().putString(SafeCameraApplication.PASSWORD_FINGERPRINT_IV, SafeCameraApplication.getCrypto().byte2hex(iv)).commit();

                                SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                                defaultSharedPrefs.edit().putBoolean("fingerprint", true).commit();
                                ((SwitchPreference) preferenceFinal).setChecked(true);

                                LoginManager.dismissLoginDialog();
                            } catch (BadPaddingException e) {
                                e.printStackTrace();
                            } catch (IllegalBlockSizeException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });
            helper.startAuth(fingerprintManager, cryptoObject, null, false);
        }

        return false;
    }

    public static void turnOffFingerprint(Context context){
        SharedPreferences preferences = context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);
        preferences.edit().remove(SafeCameraApplication.PASSWORD_FINGERPRINT).commit();
        preferences.edit().remove(SafeCameraApplication.PASSWORD_FINGERPRINT_IV).commit();

        SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        defaultSharedPrefs.edit().putBoolean("fingerprint", false).commit();
    }

    public boolean unlock(final PasswordReceivedHandler passwordHandler, final LoginManager.UserLogedinCallback loginCallback){

        // Obtain a reference to the Keystore using the standard Android keystore container identifier (“AndroidKeystore”)//
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            return false;
        }
        //Check whether the user has granted your app the USE_FINGERPRINT permission//
        if (context.checkSelfPermission(Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            // If your app doesn't have this permission, then display the following text//
            showError(context.getString(R.string.error_fingerprint_perm), null);
            return false;
        }

        //Check that the user has registered at least one fingerprint//
        if (!fingerprintManager.hasEnrolledFingerprints()) {
            // If the user hasn’t configured any fingerprints, then display the following message//
            showError(context.getString(R.string.error_fingerprint_enroll), null);
            return false;
        }

        //Check that the lockscreen is secured//
        if (!keyguardManager.isKeyguardSecure()) {
            // If the user hasn’t secured their lockscreen with a PIN password or pattern, then display the following text//
            showError(context.getString(R.string.error_fingerprint_screenguard), null);
            return false;
        }

        final SharedPreferences preferences = context.getSharedPreferences(SafeCameraApplication.DEFAULT_PREFS, Context.MODE_PRIVATE);

        String storedIvHex = preferences.getString(SafeCameraApplication.PASSWORD_FINGERPRINT_IV, "");
        byte[] storedIv = SafeCameraApplication.getCrypto().hex2byte(storedIvHex);
        if (initCipherDecrypt(storedIv)) {



            //If the cipher is initialized successfully, then create a CryptoObject instance//
            cryptoObject = new FingerprintManager.CryptoObject(cipher);

            // Here, I’m referencing the FingerprintHandler class that we’ll create in the next section. This class will be responsible
            // for starting the authentication process (via the startAuth method) and processing the authentication process events//
            FingerprintHandler helper = new FingerprintHandler(context, new FingerprintHandler.OnFingerprintSuccess() {
                @Override
                public void onSuccess(Cipher cipher) {

                    String encPass = preferences.getString(SafeCameraApplication.PASSWORD_FINGERPRINT, "");

                    try {
                        byte[] decPassBytes = cipher.doFinal(SafeCameraApplication.getCrypto().hex2byte(encPass));
                        String decPass = new String(decPassBytes);

                        passwordHandler.onPasswordReceived(decPass);

                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    }
                }
            });
            helper.startAuth(fingerprintManager, cryptoObject, loginCallback, true);
        }



        return false;
    }

    private void generateKey() throws FingerprintException {
        try {
            //Generate the key//
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            //Initialize an empty KeyStore//
            keyStore.load(null);

            //Initialize the KeyGenerator//
            keyGenerator.init(new

                    //Specify the operation(s) this key can be used for//
                    KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)

                    //Configure this key so that the user has to confirm their identity with a fingerprint each time they want to use it//
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());

            //Generate the key//
            keyGenerator.generateKey();

        }
        catch (NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException
                | CertificateException
                | IOException e) {
            e.printStackTrace();
            throw new FingerprintException(e);
        }

    }

    protected boolean initCipherEncrypt() {
        return initCipher(true, null);
    }

    protected boolean initCipherDecrypt(byte[] iv) {
        return initCipher(false, iv);
    }

    protected boolean initCipher(boolean isEncryptMode, byte[] pIv) {
        try {
            //Obtain a cipher instance and configure it with the properties required for fingerprint authentication//
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME,null);

            if(isEncryptMode) {
                cipher.init(Cipher.ENCRYPT_MODE, key);
                iv = cipher.getIV();
            }
            else{
                iv = pIv;
                AlgorithmParameterSpec ivParamSpec = new IvParameterSpec(iv);
                cipher.init(Cipher.DECRYPT_MODE, key, ivParamSpec);
            }
            //Return true if the cipher has been initialized successfully//
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {

            //Return false if cipher initialization failed//
            return false;
        } catch (KeyStoreException | CertificateException
                | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException
                | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    protected class FingerprintException extends Exception {
        public FingerprintException(Exception e) {
            super(e);
        }
    }

    protected void showError(String title, String description){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(description);
        builder.setPositiveButton(context.getString(R.string.ok), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static abstract class PasswordReceivedHandler{
        public abstract void onPasswordReceived(String password);
    }
}
