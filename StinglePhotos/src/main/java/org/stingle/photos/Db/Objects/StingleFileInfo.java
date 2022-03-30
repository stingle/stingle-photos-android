package org.stingle.photos.Db.Objects;

import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.StinglePhotosApplication;

import java.util.List;

public class StingleFileInfo {

    public List<String> objects;
    public List<String> faceIds;
    public String locId;
    public String coords;

    public StingleFileInfo() {}

    public StingleFileInfo(List<String> objects, List<String> faceIds,
                           String locId, String coords) {
        this.objects = objects;
        this.faceIds = faceIds;
        this.locId = locId;
        this.coords = coords;
    }

    public String encrypt() {
        return StinglePhotosApplication.getCrypto().encryptObject(this,
                StinglePhotosApplication.getCrypto().getPublicKey());
    }

    public static StingleFileInfo decrypt(String hash) {
        try {
            return (StingleFileInfo) StinglePhotosApplication.getCrypto().decryptObject(hash,
                    StinglePhotosApplication.getKey(),
                    StinglePhotosApplication.getCrypto().getPublicKey(), StingleFileInfo.class);
        } catch (CryptoException e) {
            e.printStackTrace();
        }
        return null;
    }
}
