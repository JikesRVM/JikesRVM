package test.org.jikesrvm.basic.bugs;
public class RVM_708 {
  public static void main(String args[]) {
    createThread();
    System.out.println("Sleeping for 1 seconds");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException i) {
      System.err.println(i);
    }
    System.out.println("Going to call System.gc()");
    System.gc();
  }

  private static void infiniteLoop() {
    System.out.println("Waiting...");
    while (true);
  }

  private static void createThread() {
    Thread t1 = new Thread (new Runnable() {
      public void run() {
        infiniteLoop();
      }
    });
    t1.setDaemon(true);
    t1.start();
  }
}
