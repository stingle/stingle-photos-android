package org.stingle.photos.Db.Objects;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.StinglePhotosApplication;

public class StingleLocation {

    public String placeName;
    public String city;
    public String country;

    public StingleLocation() {}

    public StingleLocation(String placeName, String city, String country) {
        this.placeName = placeName;
        this.city = city;
        this.country = country;
    }

    public String encrypt() {
        return StinglePhotosApplication.getCrypto().encryptObject(this,
                StinglePhotosApplication.getCrypto().getPublicKey());
    }

    public static StingleLocation decrypt(String hash) {
        try {
            return (StingleLocation) StinglePhotosApplication.getCrypto().decryptObject(hash,
                    StinglePhotosApplication.getKey(),
                    StinglePhotosApplication.getCrypto().getPublicKey(), StingleLocation.class);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return null;
    }
}
