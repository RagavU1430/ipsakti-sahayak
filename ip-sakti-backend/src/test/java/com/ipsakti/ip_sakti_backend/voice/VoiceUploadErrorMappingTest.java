package com.ipsakti.ip_sakti_backend.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.ipsakti.ip_sakti_backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class VoiceUploadErrorMappingTest {
    @Test
    void oversizedMultipartRequestReturnsControlledPayloadTooLargeError() {
        var response = new GlobalExceptionHandler()
                .handleMaxUploadSize(new MaxUploadSizeExceededException(10 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AUDIO_TOO_LARGE");
    }
}
