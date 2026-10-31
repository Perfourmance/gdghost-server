package com.gdghost.embedding;

import java.util.List;

public interface EmbeddingClient {

    /** 키가 없으면(로컬) false — 부르지 않는다 */
    boolean enabled();

    /** 입력 순서대로 1024차원. 실패하면 {@link EmbeddingException} */
    List<float[]> embed(List<String> texts);

    String modelId();

}
