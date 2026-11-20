package com.roomora.server.domain.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagChunker {

    private static final int MAX_CHUNK_CHARS = 600;

    public List<String> chunkWithPrefix(String prefix, String title, String rawContent) {
        String safePrefix = prefix != null ? prefix : "";
        String body = (title != null ? title + "\n" : "") + (rawContent != null ? rawContent : "");
        String[] sections = body.split("(?=^##\\s)", java.util.regex.Pattern.MULTILINE);

        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= MAX_CHUNK_CHARS) {
                chunks.add(safePrefix + " " + trimmed);
            } else {
                for (int start = 0; start < trimmed.length(); start += MAX_CHUNK_CHARS) {
                    int end = Math.min(start + MAX_CHUNK_CHARS, trimmed.length());
                    chunks.add(safePrefix + " " + trimmed.substring(start, end));
                }
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(safePrefix + " " + title);
        }
        return chunks;
    }
}
