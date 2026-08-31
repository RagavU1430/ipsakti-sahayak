package com.ipsakti.ip_sakti_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"rag.base-url=http://localhost:8000",
		"app.security.mode=dev"
})
class IpSaktiBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
