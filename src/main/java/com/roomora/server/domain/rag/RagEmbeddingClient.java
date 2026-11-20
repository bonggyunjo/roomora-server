package com.roomora.server.domain.rag;

public interface RagEmbeddingClient {

    int VECTOR_DIMENSION = 1536;

    float[] embed(String text);
}
