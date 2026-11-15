package com.example.pleosstarter.notes;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** 내보낼 수 없는 기기별 AndroidKeyStore 키로 위치 기록을 AES/GCM 암호화한다. */
public final class AndroidKeyStoreLocationNoteCipher implements LocationNoteCipher {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "com.example.pleosstarter.location-notes.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String FORMAT = "v1";
    private static final byte[] ASSOCIATED_DATA = "pleos-location-notes-v1".getBytes(StandardCharsets.UTF_8);
    private static final int AUTHENTICATION_TAG_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    /** 새 랜덤 IV와 인증 태그를 포함한 버전별 암호문을 생성한다. */
    @Override
    public String encrypt(String plaintext) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(true));
        cipher.updateAAD(ASSOCIATED_DATA);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return FORMAT + "." + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + "." + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    /** 형식·IV 길이·인증 태그를 검사하며 잘못된 키나 변조된 저장본은 거부한다. */
    @Override
    public String decrypt(String encrypted) throws GeneralSecurityException, IOException {
        String[] parts = encrypted.split("\\.", -1);
        if (parts.length != 3 || !FORMAT.equals(parts[0])) {
            throw new GeneralSecurityException("지원하지 않는 암호문 형식입니다.");
        }
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != IV_LENGTH_BYTES || ciphertext.length < AUTHENTICATION_TAG_BITS / 8) {
            throw new GeneralSecurityException("위치 기록 암호문의 길이가 올바르지 않습니다.");
        }

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey(false), new GCMParameterSpec(AUTHENTICATION_TAG_BITS, iv));
        cipher.updateAAD(ASSOCIATED_DATA);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    /** 저장할 때만 키를 생성하고 복원 중 키가 없으면 기존 암호문을 보존한다. */
    private SecretKey getKey(boolean createIfMissing) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
            return secretKeyEntry.getSecretKey();
        }
        if (entry != null || !createIfMissing) {
            throw new GeneralSecurityException("위치 기록 암호화 키를 사용할 수 없습니다.");
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        KeyGenParameterSpec parameters = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(parameters);
        return generator.generateKey();
    }
}
