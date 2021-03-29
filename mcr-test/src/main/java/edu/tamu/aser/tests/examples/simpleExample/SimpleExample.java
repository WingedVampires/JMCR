package edu.tamu.aser.tests.examples.simpleExample;

/**
 * Created by IntelliJ IDEA.
 * User: ksen
 * Date: Jun 2, 2007
 * Time: 12:23:57 PM
 * To change this template use File | Settings | File Templates.
 */

public class SimpleExample extends Thread {
    public static int a, b;
    Object x, y;

    public SimpleExample(Object x, Object y) {
        this.x = x;
        this.y = y;
    }

    public static void main(String[] args) {
        Object x = new Object();
        Object y = new Object();
        (new SimpleExample(x, y)).start();
        (new SimpleExample(y, x)).start();

        //add by
        if ((a == 1 && b == 2) || (a == 0 || b == 0)) {
            System.out.println("success");
        } else {
            throw new RuntimeException("error");
        }
    }

    public void run() {
        synchronized (x) {
            a = 1;
        }
        synchronized (y) {
            b = 2;
        }
    }


}
