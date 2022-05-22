/*
 * Decompiled with CFR 0.152.
 */
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class StinkhornEncrypter {
    public static String PWD = "We couldn't leak the password...";
    public static int COUNT = 11542142;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage : java -jar stinkhorn_encrypter.jar <id> '<transaction_to_encrypt>'");
            return;
        }
        Object id = args[0];
        Object transaction = args[1];
        id = (String)id + "padddddd";
        id = ((String)id).substring(0, 8);
        transaction = "----- BEGIN TRANSACTION -----\n" + (String)transaction + "\n----- END TRANSACTION -----\n";
        String encrypted = StinkhornEncrypter.getCalculatedID((String)id) + "\t\t" + StinkhornEncrypter.encrypt((String)transaction, PWD, (String)id, COUNT);
        System.out.println("Calculated ID\t\tEncrypted transaction");
        System.out.println(encrypted);
    }

    public static String encrypt(String data, String password, String salt, int noIterations) {
        try {
            String method = "PBEWithMD5AndDES";
            SecretKeyFactory kf = SecretKeyFactory.getInstance(method);
            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray());
            SecretKey key = kf.generateSecret(keySpec);
            Cipher cipher = Cipher.getInstance(method);
            PBEParameterSpec params = new PBEParameterSpec(salt.getBytes(), noIterations);
            cipher.init(1, (Key)key, params);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        }
        catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String getCalculatedID(String id) {
        String tmpId = PWD + id;
        try {
            byte[] calculatedID = tmpId.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (int i = 0; i < COUNT; ++i) {
                calculatedID = md.digest(calculatedID);
            }
            return StinkhornEncrypter.hex(calculatedID).substring(0, 7);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte aByte : bytes) {
            int decimal = aByte & 0xFF;
            Object hex = Integer.toHexString(decimal);
            if (((String)hex).length() % 2 == 1) {
                hex = "0" + (String)hex;
            }
            result.append((String)hex);
        }
        return result.toString();
    }
}

