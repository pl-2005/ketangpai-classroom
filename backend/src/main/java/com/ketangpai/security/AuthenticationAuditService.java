package com.ketangpai.security;

import com.ketangpai.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAuditService {

    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void registrationSucceeded(User user) {
        audit.info("event=registration_success userId={} username={} role={}",
                user.getId(), safe(user.getUsername()), user.getRole());
    }

    public void loginSucceeded(User user, String clientIp) {
        audit.info("event=login_success userId={} username={} ip={}",
                user.getId(), safe(user.getUsername()), safe(clientIp));
    }

    public void loginFailed(String username, String clientIp, FailureReason reason) {
        audit.warn("event=login_failure username={} ip={} reason={}",
                safe(username), safe(clientIp), reason);
    }

    public void loginBlocked(String username, String clientIp) {
        audit.warn("event=login_blocked username={} ip={}", safe(username), safe(clientIp));
    }

    private String safe(String value) {
        if (value == null) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
    }

    public enum FailureReason {
        INVALID_CREDENTIALS,
        ACCOUNT_DISABLED
    }
}
