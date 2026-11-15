package com.example.pleosstarter.notes;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** 위치 JSON과 기기 키 저장소 사이의 암호화 계약이다. */
public interface LocationNoteCipher {
    /** 매번 새 초기화 벡터를 사용해 텍스트를 인증 암호문으로 바꾼다. */
    String encrypt(String plaintext) throws GeneralSecurityException, IOException;

    /** 원래 기기의 키로 인증된 암호문만 복원한다. */
    String decrypt(String encrypted) throws GeneralSecurityException, IOException;
}
