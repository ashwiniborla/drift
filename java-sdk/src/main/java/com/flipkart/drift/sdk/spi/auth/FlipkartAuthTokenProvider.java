package com.flipkart.drift.sdk.spi.auth;

import com.flipkart.kloud.authn.AuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlipkartAuthTokenProvider implements TokenProvider{
    private static final Logger log = LoggerFactory.getLogger(FlipkartAuthTokenProvider.class);
    private AuthTokenService tokenService;
    private boolean initialised = false;
    @Override
    public void init() {
        AuthTokenService.init("https://service.authn-prod.fkcloud.in/", "imsv2_varadhi_client1", "RJ8URYfBT0v+T3WO+uh8eLAc8VMuNKJ1ibqhjnNqWsfc2sGo");
        tokenService = AuthTokenService.getInstance();
        initialised = true;
    }

    @Override
    public String getAuthToken(String targetClientId) {
        String token = tokenService.fetchToken(targetClientId).toAuthorizationHeader();
        log.info("Target Client Id {} | token generated {}", targetClientId, token);
        return token;
    }

    @Override
    public boolean isInitialized() {
        return this.initialised;
    }
}
