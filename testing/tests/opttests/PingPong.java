/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
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


