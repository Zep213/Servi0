package br.com.zep.servio;

import br.com.zep.servio.seguranca.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ServioApplicationTests {

	@Test
	void contextLoads() {
	}

}
