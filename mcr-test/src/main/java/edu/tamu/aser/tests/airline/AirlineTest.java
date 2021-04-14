package edu.tamu.aser.tests.airline;

import edu.tamu.aser.reex.JUnit4MCRRunner;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;



@RunWith(JUnit4MCRRunner.class)
public class AirlineTest {

    public static void main(String args[]) throws Exception {
        AirlineTest airlineTest = new AirlineTest();
        airlineTest.test5ThreadsNotTooMany();

    }

    public void test5ThreadsNotTooMany() throws Exception {
        makeBookings(5);
        testNotTooManyTicketsSold();
    }

    private Airline airline;

    public void makeBookings(int numTickets) throws Exception {
        airline = new Airline(numTickets);
        airline.makeBookings();
    }

    @SuppressWarnings("deprecation")
	public void testNotTooManyTicketsSold() {
        if (airline.numberOfSeatsSold > airline.maximumCapacity) {
            Assert.fail("Too many were sold! Number of tickets sold: " + airline.numberOfSeatsSold + " out of max: " + airline.maximumCapacity);
        }
    }

    @Test
	public void test() throws InterruptedException {
		try {
			AirlineTest.main(null);
		} catch (Exception e) {
			System.out.println("here");
			fail();
		}
	}

}
