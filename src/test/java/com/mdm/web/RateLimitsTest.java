package com.mdm.web;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RateLimitsTest {
    @Test void enforcesBoundaryWithoutPersistingRawIdentity(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);RateLimits limits=new RateLimits(jdbc,"test-only-key-for-unit-tests");
        when(jdbc.queryForObject(anyString(),eq(Long.class),anyString())).thenReturn(8L,9L);
        assertTrue(limits.allow("login","person@example.com:127.0.0.1",8,900));assertFalse(limits.allow("login","person@example.com:127.0.0.1",8,900));
        ArgumentCaptor<String> bucket=ArgumentCaptor.forClass(String.class);
        verify(jdbc,times(2)).queryForObject(anyString(),eq(Long.class),bucket.capture());
        for(String key:bucket.getAllValues())assertTrue(key.matches("[0-9a-f]{64}"));assertEquals(bucket.getAllValues().get(0),bucket.getAllValues().get(1));
    }
}
