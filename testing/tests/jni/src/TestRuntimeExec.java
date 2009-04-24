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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Test whether the Runtime.exec API works
 */
class TestRuntimeExec extends Thread {

    public static void main(String[] argv) {
        try {
            int num = 1;
            if (argv.length > 0) {
                num = Integer.parseInt(argv[0]);
            }

            Thread[] threadList = new Thread[num];

            for (int i = 0; i < num; ++i) {
                threadList[i] = new TestRuntimeExec(i);
                threadList[i].start();
            }

            for (int i = 0; i < num; ++i)
                threadList[i].join();

            System.out.println("All test threads finished");
        } catch (Exception e) {
            System.out.println("TestRuntimeExec: FAILED");
            System.exit(1);
        }
    }

    private static String[] testData = {
        "This is a two line test file to see if we can call the Unix\n",
        "`tee' command with Runtime.exec and get the expected result\n"
    };

    private int myNumber;

    private int charsWritten = 0;
    private int charsRead = 0;
    private int charsExpected;

    // Set this property to true on the command line
    // to test interrupting a thread while it's blocked in
    // Process.waitFor().
    public static boolean interruptWait = Boolean.getBoolean("interruptWait");

    public static final String PROGRAM = "tee";

    public TestRuntimeExec(int myNum) {
        this.myNumber = myNum;
    }

    public void run() {
        try {
            charsExpected = 10000*(testData[0].length()+testData[1].length());

            String fileName ="/tmp/out" + myNumber;

            final Process tac =
                Runtime.getRuntime().exec(new String[]{PROGRAM, fileName}, null, new File("/tmp"));

            Thread writer = new Thread() {
                public void run() {
                    DataOutputStream stdin =
                        new DataOutputStream(tac.getOutputStream());

                    try {
                        for(int x = 0; x < 10000; x++) {
                            for(int i = 0; i < testData.length; i++) {
                                charsWritten += testData[i].length();
                                stdin.writeUTF(testData[i]);
                            }
                        }

                        stdin.flush();
                        stdin.close();

                    } catch (IOException e) {
                        throw new Error("TestRuntimeExec FAILED");
                    }
                }
            };

            Thread reader = new Thread() {
                public void run() {
                    DataInputStream stdout =
                        new DataInputStream(tac.getInputStream());
                    try {

                        for(int x = 0; x < 10000; x++) {
                            for(int i = 0; i < testData.length; i++) {
                                String in = stdout.readUTF();
                                charsRead += in.length();
                                if (! in.equals(testData[i]))
                                    throw new Error("TestRuntimeExec FAILED: bad input " +  in);
                            }
                        }

                        int exitCode = tac.waitFor();

                        if (exitCode == 0 &&
                            charsRead==charsExpected &&
                            charsWritten==charsExpected
                           )
                            System.err.println("TestRuntimeExec SUCCESS");
                        else
                            System.err.println("TestRuntimeExec FAILED");

                        //System.exit(exitCode);

                    } catch (Throwable e) {
                        e.printStackTrace();
                        throw new Error("TestRuntimeExec FAILED");
                    }
                }
            };

            writer.start();
            reader.start();

            // Use Process.waitFor() to wait for the process to complete
            final Thread waiter = new Thread() {
                public void run() {
                    try {
                        int exitCode = tac.waitFor();
                        System.out.println("waitFor(): Process exited with code " + exitCode);
                    } catch (InterruptedException e) {
                        if (!interruptWait) {
                            System.out.println("Waiting thread uninterrupted unexpectedly!!!");
                            System.out.println("TestRuntimeExec FAILED");
                            System.exit(1);
                        }
                        System.out.println("Waiting thread interrupted! (THIS IS GOOD)");
                        e.printStackTrace();
                    }
                }
            };
            waiter.start();

            if (interruptWait) {
                // See if waitFor() can be interrupted.
                // This should not affect the behavoir of the IO threads
                // or the process.
                new Thread() {
                    public void run() {
                        try { Thread.sleep(2000); } catch (Exception e) { }
                        waiter.interrupt();
                    }
                }.start();
            }

            // Use repeated polling with exitValue() to wait for the process
            // to complete
            Thread poller = new Thread() {
                public void run() {
                    int exitCode = -99;
                    boolean exited = false;
                    do {
                        try {
                            exitCode = tac.exitValue();
                            //System.out.println( "GOT IT");
                            exited = true;
                        } catch (IllegalThreadStateException e) {
                            System.out.println("still alive!");
                            try { Thread.sleep(1000); } catch (Exception ee) { }
                        }
                    }
                    while (!exited);

                    System.out.println("exitValue(): Process exited with code " + exitCode);
                }
            };
            poller.start();

            try {
                reader.join();
                writer.join();
                waiter.join();
                poller.join();
            } catch (InterruptedException eee) {
                eee.printStackTrace();
            }

        } catch (Throwable e) {
            System.err.println("TestRuntimeExec FAILED with");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
