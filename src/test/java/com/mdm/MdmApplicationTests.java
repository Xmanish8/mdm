package com.mdm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="MDM_DB_TESTS", matches="true")
@SpringBootTest
class MdmApplicationTests {

	@Test
	void contextLoads() {
	}

}
