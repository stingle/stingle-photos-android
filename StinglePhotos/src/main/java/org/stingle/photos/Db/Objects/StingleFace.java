package org.stingle.photos.Db.Objects;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.StinglePhotosApplication;

public class StingleFace {

    public int objectVersion = 1;
    public float [] data;
    public int iteration;
    public String personName;
    public String personEmail;
    public String thumbnail;

    public StingleFace() {}

    public StingleFace(int objectVersion, float[] data, int iteration,
                       String personName, String personEmail,
                       String thumbnail) {
        this.objectVersion = objectVersion;
        this.data = data;
        this.iteration = iteration;
        this.personName = personName;
        this.personEmail = personEmail;
        this.thumbnail = thumbnail;
    }

    public String encrypt() {
        return StinglePhotosApplication.getCrypto().encryptObject(this,
                StinglePhotosApplication.getCrypto().getPublicKey());
    }

    public static StingleFace decrypt(String hash) {
        try {
            return (StingleFace) StinglePhotosApplication.getCrypto().decryptObject(hash,
                    StinglePhotosApplication.getKey(),
                    StinglePhotosApplication.getCrypto().getPublicKey(), StingleFace.class);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return null;
    }
}
