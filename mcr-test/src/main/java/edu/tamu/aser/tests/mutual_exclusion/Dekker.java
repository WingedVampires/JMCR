package edu.tamu.aser.tests.mutual_exclusion;

import edu.tamu.aser.reex.JUnit4MCRRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.fail;

@RunWith(JUnit4MCRRunner.class)
public class Dekker {
    final static int N1 = 1;
    final static int N2 = 1;
    public static int flag1;
    public static int flag2;
    public static int turn;
    public static int x;

    @Test
    public void test() throws InterruptedException {
        try {
            flag1 = 0;
            flag2 = 0;
            turn = 1;
            x = 0;
            Thread t1 = new Thread(new Runnable() {

                @Override
                public void run() {
                    int n1 = 0, n2 = 0;
                    flag1 = 1;
                    while (flag2 == 1) {
                        if (n2++ > N2) break;
                        if (turn != 1) {
                            flag1 = 0;
                            while (turn != 1)
                                if (n1++ > N1) break;
                            flag1 = 1;
                        }
                    }

                    x = 1;
                    if (x != 1) System.out.println("error");
                    turn = 2;
                    flag1 = 0;
                }
            });

            Thread t2 = new Thread(new Runnable() {

                @Override
                public void run() {
                    int n1 = 0, n2 = 0;
                    flag2 = 1;
                    while (flag1 == 1) {
                        if (n2++ > N2) break;
                        if (turn != 2) {
                            flag2 = 0;
                            while (turn != 2)
                                if (n1++ > N1) break;
                            flag2 = 1;
                        }
                    }

                    x = 2;
                    if (x != 2) System.out.println("error");
                    turn = 1;
                    flag2 = 0;
                }
            });

            t1.start();
            t2.start();

            try {
                t1.join();
                t2.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("here");
            fail();
        }
    }
}
