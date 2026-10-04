package me.zhenxin.zmusic.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KuwoWebAuthTest {
    @Test void matchesOfficialWebsiteProtocolVectors() {
        assertEquals("4725e6cf680a1e42ad4cd4fe3997c54263aa9f00bc614e", KuwoWebAuth.secret("dummy-visitor-token", 12345678));
        assertEquals("4725e6cf680a1e42ad4cd4fe3997c54263aa9f05397fb1", KuwoWebAuth.secret("dummy-visitor-token", 87654321));
        assertEquals("4725e6cf680a1e42ad4cd4fe3997c54263aa9f05f5e0ff", KuwoWebAuth.secret("dummy-visitor-token", 99999999));
        assertThrows(IllegalArgumentException.class, () -> KuwoWebAuth.secret("visitor", 1));
    }
}
