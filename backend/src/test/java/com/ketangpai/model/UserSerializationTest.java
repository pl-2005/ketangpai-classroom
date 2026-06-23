package com.ketangpai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ketangpai.model.entity.User;
import com.ketangpai.model.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordHashIsNeverSerialized() throws Exception {
        User user = User.builder()
                .username("zhangsan")
                .password("bcrypt-hash")
                .role(UserRole.STUDENT)
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("password", "bcrypt-hash");
    }
}
