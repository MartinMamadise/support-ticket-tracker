package za.co.mamadise.support_ticket_tracker;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the application clock (UTC) so time-dependent logic can be tested with a fixed clock. */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
