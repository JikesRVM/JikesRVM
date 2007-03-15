/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */
class PingPong extends Thread {
    String  word;
    int     delaytime;

    PingPong(String msg, int tm) {
        word = msg;
        delaytime = tm;
    }

    public void run() {
        try {
            for (int i=0; i<20; i++) {
                System.out.print(word + " ");
                sleep(delaytime);
            }
        }
        catch (InterruptedException e) {
            return;
        }
    }

    public static void main(String[] args) {
        new PingPong("-", 50).start();
        new PingPong("O", 100).start();
    }
}


