package com.ketangpai.security;

public interface LoginAttemptService {

    void checkAllowed(String username, String clientIp);

    void recordFailure(String username, String clientIp);

    void recordSuccess(String username, String clientIp);
}
