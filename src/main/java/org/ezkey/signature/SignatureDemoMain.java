package org.ezkey.signature;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class SignatureDemoMain {
    public static void main(String[] args) {
        try {
            // Génération d'une paire de clés RSA
            //KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            //keyGen.initialize(2048);
            //KeyPair keyPair = keyGen.generateKeyPair();

            String base64PrivateKey = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDtPOoUU3lYL+ImOyQCbN4pxhNmEYAe0D0jO1mbmcPrcJ2YNXGslVhI7ivWRv2I9tQbTmo8uczW9MnjqaWyCwP73udsXzV6hNylVGxPjDIcMuw64Pu32/Vl7ERiwV7fDI78bDJyXiNByhx0ufxBzmSZRAbB9S30nrKJnszWuj/b3iBBgYMXmCos0yxzHlbaD5ykdhh/IsiBXqK49m1Z3D6Y2CfkkDZf6fBzSIHW076oA8PBWEcJJ0mLR7Zo/PSrBk3nwIe9CrKxdr1DQiO8j9IdKiEKsGpsKNHChTcMzj+2ruCMipWx5E9kacOtXi0usLmQnb9aEMEpMjeKE+ZWU//JAgMBAAECggEAAl9VcmxWxm9dJjd5b9ZFnvqSe1bxtm0r7VgkuGIAY/B5fPoM19Za/oi2MsN9OdNJEfhV5uNPRXeHdNG/nNEQIkLLgumNU/sz5ynZ1u5OmnpWYyzYi5FFKhajpO6s25NpMyREWWEwXlIueGrbN0/AcQltfV9NrThwjeCDCSMt7A2Oy6BkzjGwPxFVKJGaqYWBMCSYapfYBra9tsJBwMHRVjFJw51gBGw7k4wl+YIPrWewTDCZrwJuoTJjhK3R28ZIlbzB8C/S4HvT0+KiVYvOkslPQ6s02dY+06Fkqw5Osv1BRsN4FgLouOUzyAtax1JijwkbM5CAzt2Fqi3rM7/gUQKBgQDtnmuWhF034AZSUElz+KdngnAP5xzVSwhMOz9xl8T49QgBQIUEUx/8VlOJMv/jhqIUNbdQXms/muN3QOOHHbxY90oeWB9G0gu0RrP7W0KOi8tYlMjSQYl1S/zczNyf6jkZKSqa7bKMTlLJlMuUQLOI3YgpXRiU/G7dj7ilN5LmuQKBgQD/lvOTk/cNJwasLfWPF9U0icSVC1lASZ3+zD1yyv+m5jfHXlAvcYTdGpMEAR84pbTOyxhMHsBH/yFRibgNX+wFC0gBnBbmv3RJy2bEKl16BbjM3Qnpfs+70mNSmpOIV+2Lnd8WEX1NyFZ7DKeQguV1SKA1+fW7b5NwwBK+ZLFZkQKBgHBSOWMt8G6QHAze3MnQGTnJo7UwtyVv0V1PiF7msfpIwV7uI7J2pKEAIX6yN9lSc9z/w0ZfS20Gh02Aw8zD1ptrUYLkfuxYL6Yo4b0IV37QP/AAbKlOx25F3CJ3SDbjXdgx6GzRvVurTmDxUAYlS3h/13ROCGesp69d15dpnKwpAoGBAIdG3w7KLuVQNZDmFmUxKRBQprJ4OjnzfONCOHfMh7lPelBUU754p+jayttAuMSjt+oHxolrcTvqBjmA2eCCV4pn6Smo8toYlTUFqhPDlIwkASa1Cy0BiHORrC4pUFLGrxzJZyzn/tkvYs5n6Txse4Qy91D6Rpx5NqFjl8gbxV5xAoGBAOwhuIYbyAqtaGhl6DZBtghP3lm3goHC2dEJCtimuWkjBJjbDuubZa/ejNLoBBafjvkV7JGpyxlszslfptU5feP0w6WoYIMr91Lf2O05ObARoioON4lQ4a58GhGmZiqBbyZuan6ESIWbSBCcUJvQkhoKvYWqwgDhNeAZQ2LJ1gSW"; 
            //Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String base64PublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7TzqFFN5WC/iJjskAmzeKcYTZhGAHtA9IztZm5nD63CdmDVxrJVYSO4r1kb9iPbUG05qPLnM1vTJ46mlsgsD+97nbF81eoTcpVRsT4wyHDLsOuD7t9v1ZexEYsFe3wyO/Gwycl4jQcocdLn8Qc5kmUQGwfUt9J6yiZ7M1ro/294gQYGDF5gqLNMscx5W2g+cpHYYfyLIgV6iuPZtWdw+mNgn5JA2X+nwc0iB1tO+qAPDwVhHCSdJi0e2aPz0qwZN58CHvQqysXa9Q0IjvI/SHSohCrBqbCjRwoU3DM4/tq7gjIqVseRPZGnDrV4tLrC5kJ2/WhDBKTI3ihPmVlP/yQIDAQAB";
            //Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

            String data = "test";

            SignatureService signatureService = new SignatureService();
            String signature = signatureService.generateSignature(data, base64PrivateKey);
            System.out.println("Signature générée : " + signature);

            boolean isValid = signatureService.validateSignature(data, signature, base64PublicKey);
            System.out.println("Signature valide ? " + isValid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
