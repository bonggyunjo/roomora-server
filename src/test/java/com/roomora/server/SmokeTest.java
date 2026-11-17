package com.roomora.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest {

    @Test
    void ciPipelineSanityCheck() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
