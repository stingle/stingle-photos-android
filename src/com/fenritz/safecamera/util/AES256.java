package com.fenritz.safecamera.util;


public class AES256 {
	
	public AES256(){
		
	}
	
	/*public void encrypt(InputStream fin, OutputStream fout, String password) {
	    try {
	        PKCS12ParametersGenerator pGen = new PKCS12ParametersGenerator(new SHA256Digest());
	        
	        char[] passwordChars = password.toCharArray();
	        final byte[] pkcs12PasswordBytes = PBEParametersGenerator.PKCS12PasswordToBytes(passwordChars);
	        
	        pGen.init(pkcs12PasswordBytes, salt.getBytes(), iterationCount);
	        
	        CBCBlockCipher aesCBC = new CBCBlockCipher(new AESEngine());
	        ParametersWithIV aesCBCParams = (ParametersWithIV) pGen.generateDerivedParameters(256, 128);
	        aesCBC.init(true, aesCBCParams);
	        PaddedBufferedBlockCipher aesCipher = new PaddedBufferedBlockCipher(aesCBC, new PKCS7Padding());
	        aesCipher.init(true, aesCBCParams);

	        // Read in the decrypted bytes and write the cleartext to out
	        int numRead = 0;
	        while ((numRead = fin.read(buf)) >= 0) {
	            if (numRead == 1024) {
	                byte[] plainTemp = new byte[aesCipher.getUpdateOutputSize(numRead)];
	                int offset = aesCipher.processBytes(buf, 0, numRead, plainTemp, 0);
	                final byte[] plain = new byte[offset];
	                System.arraycopy(plainTemp, 0, plain, 0, plain.length);
	                fout.write(plain, 0, plain.length);
	            } else {
	                byte[] plainTemp = new byte[aesCipher.getOutputSize(numRead)];
	                int offset = aesCipher.processBytes(buf, 0, numRead, plainTemp, 0);
	                int last = aesCipher.doFinal(plainTemp, offset);
	                final byte[] plain = new byte[offset + last];
	                System.arraycopy(plainTemp, 0, plain, 0, plain.length);
	                fout.write(plain, 0, plain.length);
	            }
	        }
	        fout.close();
	        fin.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	}

	public void decrypt(InputStream fin, OutputStream fout, String password) {
	    try {
	        PKCS12ParametersGenerator pGen = new PKCS12ParametersGenerator(new SHA256Digest());
	        char[] passwordChars = password.toCharArray();
	        final byte[] pkcs12PasswordBytes = PBEParametersGenerator.PKCS12PasswordToBytes(passwordChars);
	        pGen.init(pkcs12PasswordBytes, salt.getBytes(), iterationCount);
	        CBCBlockCipher aesCBC = new CBCBlockCipher(new AESEngine());
	        ParametersWithIV aesCBCParams = (ParametersWithIV) pGen.generateDerivedParameters(256, 128);
	        aesCBC.init(false, aesCBCParams);
	        PaddedBufferedBlockCipher aesCipher = new PaddedBufferedBlockCipher(aesCBC, new PKCS7Padding());
	        aesCipher.init(false, aesCBCParams);

	        // Read in the decrypted bytes and write the cleartext to out
	        int numRead = 0;
	        while ((numRead = fin.read(buf)) >= 0) {
	            if (numRead == 1024) {
	                byte[] plainTemp = new byte[aesCipher.getUpdateOutputSize(numRead)];
	                int offset = aesCipher.processBytes(buf, 0, numRead, plainTemp, 0);
	                // int last = aesCipher.doFinal(plainTemp, offset);
	                final byte[] plain = new byte[offset];
	                System.arraycopy(plainTemp, 0, plain, 0, plain.length);
	                fout.write(plain, 0, plain.length);
	            } else {
	                byte[] plainTemp = new byte[aesCipher.getOutputSize(numRead)];
	                int offset = aesCipher.processBytes(buf, 0, numRead, plainTemp, 0);
	                int last = aesCipher.doFinal(plainTemp, offset);
	                final byte[] plain = new byte[offset + last];
	                System.arraycopy(plainTemp, 0, plain, 0, plain.length);
	                fout.write(plain, 0, plain.length);
	            }
	        }
	        fout.close();
	        fin.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}*/
}
