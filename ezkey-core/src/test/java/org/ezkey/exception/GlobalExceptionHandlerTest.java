package org.ezkey.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ezkey.dto.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNoPendingAuthAttempt_returns204() {
        WebRequest req = mock(WebRequest.class);
        ResponseEntity<Void> resp = handler.handleNoPendingAuthAttempt(new NoPendingAuthAttemptException("none"), req);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        assertThat(resp.getBody()).isNull();
    }

    @Test
    void handleResourceNotFound_returns404() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/test");
        ResponseEntity<ErrorResponseDto> resp = handler.handleResourceNotFound(new ResourceNotFoundException("Integration", 9), req);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        ErrorResponseDto body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("NOT_FOUND");
        assertThat(body.getMessage()).contains("Integration with id 9 not found");
        assertThat(body.getPath()).isEqualTo("uri=/api/test");
    }

    @Test
    void handleGeneric_returns500GenericMessage() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/x");
        ResponseEntity<ErrorResponseDto> resp = handler.handleGenericException(new RuntimeException("secret internal detail"), req);
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        ErrorResponseDto body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred");
        // ensure internal exception message not leaked
        assertThat(body.getMessage()).doesNotContain("secret internal detail");
    }
}
