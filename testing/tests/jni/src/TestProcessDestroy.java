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
public class TestProcessDestroy {
  public static void main(String[] argv) {
    try {
      final Process proc = Runtime.getRuntime().exec(new String[]{"cat"});

      // Process killer thread
      new Thread() {
        public void run() {
          try {
            Thread.sleep(3000); // give it a chance to start
            proc.destroy();
          } catch (InterruptedException e) {
          }
        }
      }.start();

      // Wait for the process to exit
      int exitCode = proc.waitFor();
      System.out.println("Process exited with code " + exitCode);
      System.out.println("TestProcessDestroy SUCCESS");
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("TestProcessDestroy FAILURE");
    }
  }
}
