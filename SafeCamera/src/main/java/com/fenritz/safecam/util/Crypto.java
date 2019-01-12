package com.fenritz.safecam.util;

import android.os.AsyncTask;
import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.SecretStream;
import com.goterl.lazycode.lazysodium.utils.Key;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Crypto {

    protected SodiumAndroid so;
    protected LazySodiumAndroid ls;
    protected SecretStream.Lazy secretStream;
    protected int bufSize = 4096;
    protected int bufDecSize = bufSize + SecretStream.ABYTES;
    protected byte[] buf = new byte[bufSize];
    protected byte[] bufDec = new byte[bufDecSize];

    public Crypto(){
        so = new SodiumAndroid();
        ls = new LazySodiumAndroid(so);
        secretStream = (SecretStream.Lazy) ls;
    }

    public void encrypt(String keyStr, InputStream in, OutputStream out) {
        this.encrypt(keyStr, in, out, null, null);
    }

    public void encrypt(String keyStr, InputStream in, OutputStream out, CryptoProgress progress) {
        this.encrypt(keyStr, in, out, null, null);
    }

    public void encrypt(String keyStr, InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
        Key key;
        if(keyStr == null) {
            key = secretStream.cryptoSecretStreamKeygen();
        }
        else{
            key = Key.fromHexString(keyStr);
        }

        byte[] keyBytes = key.getAsBytes();
        Log.e("key", key.getAsHexString());
        byte[] header = new byte[SecretStream.HEADERBYTES];
        try {
            SecretStream.State state = new SecretStream.State.ByReference();
            so.crypto_secretstream_xchacha20poly1305_init_push(state, header, keyBytes);
            //SecretStream.State state = secretStream.cryptoSecretStreamInitPush(header, key);
            out.write(header);

            long totalRead = 0;
            long totalEnc = 0;
            int numRead = 0;
            while ((numRead = in.read(buf)) >= 0) {

                byte tag = SecretStream.XCHACHA20POLY1305_TAG_MESSAGE;
                if(numRead < bufSize) {
                    tag = SecretStream.XCHACHA20POLY1305_TAG_FINAL;
                    Log.e("final", String.valueOf(tag));
                }


                totalRead+=numRead;
                byte[] encBytes = new byte[bufSize + SecretStream.ABYTES];
                long[] encSize = new long[1];
                int res = so.crypto_secretstream_xchacha20poly1305_push(state, encBytes, encSize, buf, numRead, null, 0, tag);
                Log.e("tag", String.valueOf(totalRead) + " - " + String.valueOf(tag) + " - " + String.valueOf(encSize[0]));
                if (res != 0) {
                    throw new SodiumException("Error when encrypting a message using secret stream.");
                }

                //String enc = secretStream.cryptoSecretStreamPush(state, ls.str(buf), tag);
                totalEnc += encBytes.length;
                out.write(encBytes, 0, (int)encSize[0]);

                if(task != null){
                    if(task.isCancelled()){
                        break;
                    }
                }
            }
            Log.e("totalRead", String.valueOf(totalRead));
            Log.e("totalEnc", String.valueOf(totalEnc));
            out.close();
            in.close();
        } catch (SodiumException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void decrypt(String keyStr, InputStream in, OutputStream out) {
        this.decrypt(keyStr, in, out, null, null);
    }
    public void decrypt(String keyStr, InputStream in, OutputStream out, CryptoProgress progress, AsyncTask<?,?,?> task) {
        Key key = Key.fromHexString(keyStr);
        long totalRead = SecretStream.HEADERBYTES;
        long totalDec = SecretStream.HEADERBYTES;
        int count = 0;
        int count1 = 0;
        byte[] keyBytes = key.getAsBytes();
        byte[] header = new byte[SecretStream.HEADERBYTES];
        try {
            in.read(header);
            SecretStream.State state = new SecretStream.State.ByReference();
            int resInit = so.crypto_secretstream_xchacha20poly1305_init_pull(state, header, keyBytes);

            if (resInit != 0) {
                throw new SodiumException("Could not initialise a decryption state.");
            }
            //SecretStream.State state = secretStream.cryptoSecretStreamInitPull(header, key);
            //out.write(header);

            int numRead = 0;


            byte[] tag = new byte[1];
            while ((numRead = in.read(bufDec)) >= 0) {
                count1++;
                byte[] decBytes = new byte[bufSize];
                long[] decSize = new long[1];
                totalRead+=numRead;
                int res = so.crypto_secretstream_xchacha20poly1305_pull(state, decBytes, decSize, tag, bufDec, numRead, null, 0);

                Log.e("tag", String.valueOf(totalRead) + " - " + String.valueOf(numRead) + " - " + String.valueOf(res) + " - " + String.valueOf(tag[0]));

                totalDec += decBytes.length;
                if(res != 0){
                    throw new SodiumException("Error when decrypting a message using secret stream.");
                }

                out.write(decBytes, 0, (int)decSize[0]);
                //String dec = secretStream.cryptoSecretStreamPull(state, ls.toHex(bufDec), tag);
                if(tag[0] == SecretStream.XCHACHA20POLY1305_TAG_FINAL){
                    break;
                }


                if(task != null){
                    if(task.isCancelled()){
                        break;
                    }
                }
                count++;
            }

            out.close();
            in.close();
        } catch (SodiumException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.e("totalRead", String.valueOf(totalRead));
        Log.e("totalDec", String.valueOf(totalDec));
        Log.e("count", String.valueOf(count));
        Log.e("count1", String.valueOf(count1));
    }
}
