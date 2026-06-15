package com.alexeisoki.vibeboot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpSession;

class ManualAuthInterceptorTest {

    private final ManualAuthInterceptor interceptor = new ManualAuthInterceptor();

    @Test
    void preHandle_rejectsRequestWhenSessionDoesNotExist() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean shouldContinue = interceptor.preHandle(request, response, new Object());

        assertThat(shouldContinue).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void preHandle_rejectsRequestWhenSessionHasNoUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean shouldContinue = interceptor.preHandle(request, response, new Object());

        assertThat(shouldContinue).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_allowsRequestWhenSessionHasUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession();
        session.setAttribute(AuthController.USER_ID_SESSION_ATTRIBUTE, UUID.randomUUID());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean shouldContinue = interceptor.preHandle(request, response, new Object());

        assertThat(shouldContinue).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
