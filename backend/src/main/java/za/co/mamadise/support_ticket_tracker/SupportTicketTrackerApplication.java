package za.co.mamadise.support_ticket_tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // SLA monitor (sla.SlaMonitor)
public class SupportTicketTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SupportTicketTrackerApplication.class, args);
	}

}
