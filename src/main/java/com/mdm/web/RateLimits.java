package com.mdm.web;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared SQL counters; raw usernames and IP addresses are never stored here. */
@Service
public class RateLimits {
    private final JdbcTemplate jdbc;
    private final String secret;
    public RateLimits(JdbcTemplate jdbc,@Value("${mdm.security.rate-secret:}") String secret){
        this.jdbc=jdbc;this.secret=secret.isBlank()?java.util.UUID.randomUUID().toString():secret;
    }
    public boolean allow(String scope,String identity,int limit,int seconds){
        String bucket=key(scope+":"+identity);long window=System.currentTimeMillis()/1000/seconds*seconds;
        jdbc.update("INSERT INTO auth_rate_limits(bucket,window_start,attempts) VALUES (?,?,1) ON DUPLICATE KEY UPDATE attempts=IF(window_start=?,attempts+1,1),window_start=?",bucket,window,window,window);
        Long attempts=jdbc.queryForObject("SELECT attempts FROM auth_rate_limits WHERE bucket=?",Long.class,bucket);
        if(attempts==null)throw new IllegalStateException("Rate counter unavailable");
        return attempts<=limit;
    }
    String key(String value){try{
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}}
}
