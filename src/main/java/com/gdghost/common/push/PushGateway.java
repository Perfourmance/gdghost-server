package com.gdghost.common.push;

/** 브라우저 푸시 서비스로 한 건 보낸다 */
public interface PushGateway {

    /**
     * @param payload 암호화 전 JSON. 서비스 워커가 받는 내용
     * @return 구독이 사라졌으면(404 · 410) {@link PushResult#GONE}
     * @throws PushSendException 그 밖의 실패. 재시도하지 않는다
     */
    PushResult send(PushTarget target, String payload);

}
