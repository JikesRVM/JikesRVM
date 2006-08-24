/*
 * (C) Copyright IBM 2002
 */
// $Id$

/**
 * Test of <code>Process.destroy()</code>.
 *
 * @author David Hovemeyer
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
          }
          catch (InterruptedException e) {
          }
        }
      }.start();

      // Wait for the process to exit
      int exitCode = proc.waitFor();
      System.out.println("Process exited with code " + exitCode);
      System.out.println("TestProcessDestroy SUCCESS");
    }
    catch (Exception e) {
      e.printStackTrace();
      System.out.println("TestProcessDestroy FAILURE");
    }
  }
}
