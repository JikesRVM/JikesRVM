/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
        } catch (InterruptedException e) {
            return;
        }
    }

    public static void main(String[] args) {
        new PingPong("-", 50).start();
        new PingPong("O", 100).start();
    }
}


