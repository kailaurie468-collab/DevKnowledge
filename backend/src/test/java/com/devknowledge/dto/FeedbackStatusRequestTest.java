package com.devknowledge.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("反馈状态请求校验")
class FeedbackStatusRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private FeedbackStatusRequest request(String status) {
        FeedbackStatusRequest req = new FeedbackStatusRequest();
        req.setStatus(status);
        return req;
    }

    @Test
    @DisplayName("三个合法状态值通过校验")
    void acceptsValidStatuses() {
        for (String status : new String[]{"NEW", "IN_PROGRESS", "RESOLVED"}) {
            assertThat(validator.validate(request(status))).isEmpty();
        }
    }

    @Test
    @DisplayName("非法状态值被拒绝")
    void rejectsInvalidStatus() {
        assertThat(validator.validate(request("DELETED"))).isNotEmpty();
        assertThat(validator.validate(request("new"))).isNotEmpty();
        assertThat(validator.validate(request(""))).isNotEmpty();
    }

    @Test
    @DisplayName("null 状态被拒绝")
    void rejectsNullStatus() {
        assertThat(validator.validate(request(null))).isNotEmpty();
    }
}
