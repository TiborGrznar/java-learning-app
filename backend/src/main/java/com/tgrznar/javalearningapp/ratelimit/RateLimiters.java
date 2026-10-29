package com.tgrznar.javalearningapp.ratelimit;

import org.springframework.stereotype.Component;

/** The limiters of the application, built from {@link RateLimitProperties}. */
@Component
public class RateLimiters {

    private final RateLimiter loginPerIp;
    private final RateLimiter registerPerIp;
    private final RateLimiter refreshPerIp;
    private final RateLimiter apiPerIp;
    private final RateLimiter loginFailuresPerEmail;
    private final RateLimiter quizLoadPerUser;

    public RateLimiters(RateLimitProperties properties) {
        int keys = properties.maxTrackedKeys();
        this.loginPerIp = build(properties.loginPerIp(), keys);
        this.registerPerIp = build(properties.registerPerIp(), keys);
        this.refreshPerIp = build(properties.refreshPerIp(), keys);
        this.apiPerIp = build(properties.apiPerIp(), keys);
        this.loginFailuresPerEmail = build(properties.loginFailuresPerEmail(), keys);
        this.quizLoadPerUser = build(properties.quizLoadPerUser(), keys);
    }

    private static RateLimiter build(RateLimitProperties.Rule rule, int maxKeys) {
        return new RateLimiter(rule.max(), rule.window(), maxKeys);
    }

    public RateLimiter loginPerIp() {
        return loginPerIp;
    }

    public RateLimiter registerPerIp() {
        return registerPerIp;
    }

    public RateLimiter refreshPerIp() {
        return refreshPerIp;
    }

    public RateLimiter apiPerIp() {
        return apiPerIp;
    }

    public RateLimiter loginFailuresPerEmail() {
        return loginFailuresPerEmail;
    }

    public RateLimiter quizLoadPerUser() {
        return quizLoadPerUser;
    }
}