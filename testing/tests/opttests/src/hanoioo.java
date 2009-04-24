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
class Globals {
        public static int NumDisks;
        public static int MaxDisks = 64; // this will do!
}

public class hanoioo {

    static Peg peg1 = new Peg(1),
               peg2 = new Peg(2),
               peg3 = new Peg(3);

    public static void main(String[] args) {

       Globals.NumDisks = 24;
       if (args.length > 0) Globals.NumDisks = Integer.parseInt(args[0]);

       System.out.println("moving " + Globals.NumDisks + " disks...");

       if (Globals.NumDisks > Globals.MaxDisks)
           Globals.NumDisks = Globals.MaxDisks;

       for (int i = Globals.NumDisks; i > 0; i--)
           peg1.addDisk(i);

        long start = System.currentTimeMillis();

        moveDisks(Globals.NumDisks, peg1, peg3, peg2);

        long stop = System.currentTimeMillis();

        long t = (stop - start) / 100;

        System.out.println("finished in " + (t / 10) + "." + (t % 10) + " seconds");
    }

    public static void moveDisks(int numDisks, Peg fromPeg, Peg toPeg, Peg usingPeg) {
        if (numDisks == 1) {
            int disk;
            toPeg.addDisk(disk = fromPeg.removeDisk());
        } else {
            moveDisks(numDisks - 1, fromPeg, usingPeg, toPeg);
            moveDisks(1, fromPeg, toPeg, usingPeg);
            moveDisks(numDisks - 1, usingPeg, toPeg, fromPeg);
        }
    }
}

class Peg {

    int pegNum;
    int[] disks = new int[64];
    int nDisks;

    public Peg(int n) {
        pegNum = n;
        for (int i = 0; i < Globals.NumDisks; i++)
            disks[i] = 0;
        nDisks = 0;
    }

    public int pegNum() {
        return pegNum;
    }

    public void addDisk(int diskNum) {
        disks[nDisks++] = diskNum;
    }

    public int removeDisk() {
        return disks[--nDisks];
    }

}

