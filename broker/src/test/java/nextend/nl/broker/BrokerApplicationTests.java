package nextend.nl.broker;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mockStatic;

@SpringBootTest
class BrokerApplicationTests {

    @Test
    void contextLoads() {

        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            mocked.when(() -> { SpringApplication.run(BrokerApplication.class, new String[] { "Hello", "Broker" }); })
                    .thenReturn(Mockito.mock(ConfigurableApplicationContext.class));
            BrokerApplication.main(new String[] { "Hello", "Broker" });
            mocked.verify(() -> { SpringApplication.run(BrokerApplication.class, new String[] { "Hello", "Broker" }); });
        }

    }

}
