package com.paytm.seatreservation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class SeatReservationApplicationTests {

	@Test
	void contextLoads() {
		// This test will fail if the application context cannot start,
		// which includes verifying that Flyway migrations succeed
		// and the database is accessible.
	}

}
