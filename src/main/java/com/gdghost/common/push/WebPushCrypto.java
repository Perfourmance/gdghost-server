package com.gdghost.common.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** RFC 8291(본문 암호화 aes128gcm) · RFC 8292(VAPID 서명). P-256 공개키는 65바이트 비압축 형식 */
final class WebPushCrypto {

    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final int COORDINATE_LENGTH = 32;
    private static final int RECORD_SIZE = 4096;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    static byte[] encrypt(byte[] plaintext, byte[] receiverPublicKey, byte[] authSecret)
            throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, receiverPublicKey, authSecret, generator.generateKeyPair(), salt);
    }

    /** 메시지마다 새 키 쌍 · salt를 쓴다. 인자로 받는 것은 RFC 테스트 벡터 검증용 */
    static byte[] encrypt(byte[] plaintext, byte[] receiverPublicKey, byte[] authSecret, KeyPair sender, byte[] salt)
            throws GeneralSecurityException {
        byte[] senderPublicKey = rawPublicKey((ECPublicKey) sender.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(receiverPublicKey), true);

        byte[] keyInfo = concat(ascii("WebPush: info\0"), receiverPublicKey, senderPublicKey);
        byte[] ikm = hkdfExpand(hmac(authSecret, agreement.generateSecret()), keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] contentKey = hkdfExpand(prk, ascii("Content-Encoding: aes128gcm\0"), 16);
        byte[] nonce = hkdfExpand(prk, ascii("Content-Encoding: nonce\0"), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
        // 0x02 = 마지막 레코드 표시
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

        ByteBuffer header = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + PUBLIC_KEY_LENGTH);
        header.put(salt).putInt(RECORD_SIZE).put((byte) PUBLIC_KEY_LENGTH).put(senderPublicKey);
        return concat(header.array(), ciphertext);
    }

    /** JWT ES256 서명은 DER가 아니라 r || s 그대로 */
    static String vapidToken(ECPrivateKey privateKey, String audience, String subject, long expiresAtEpochSecond)
            throws GeneralSecurityException {
        String header = base64Url(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"));
        String claims = base64Url(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtEpochSecond + ",\"sub\":\""
                + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(ascii(unsigned));
        return unsigned + "." + base64Url(signature.sign());
    }

    static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != PUBLIC_KEY_LENGTH || raw[0] != 4) {
            throw new GeneralSecurityException("P-256 비압축 공개키가 아닙니다");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, curve()));
    }

    static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), curve()));
    }

    static byte[] decodeBase64Url(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] rawPublicKey(ECPublicKey key) {
        return ByteBuffer.allocate(PUBLIC_KEY_LENGTH).put((byte) 4)
                .put(fixedLength(key.getW().getAffineX())).put(fixedLength(key.getW().getAffineY())).array();
    }

    /** BigInteger는 앞의 0을 빼거나 부호 바이트를 붙이므로 32바이트로 맞춘다 */
    private static byte[] fixedLength(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] fixed = new byte[COORDINATE_LENGTH];
        int length = Math.min(bytes.length, COORDINATE_LENGTH);
        System.arraycopy(bytes, bytes.length - length, fixed, COORDINATE_LENGTH - length, length);
        return fixed;
    }

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] hmac(byte[] key, byte[] value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    /** 32바이트 이하만 쓰므로 한 블록이면 된다 */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(buffer::put);
        return buffer.array();
    }

}
