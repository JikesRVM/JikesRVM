/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestStopWorker extends Thread
   {
   static boolean ready;

   TestStopWorker()
      {
      setName("worker");
      }

   public void
   run() //- overrides Thread
      {
      try {
          System.out.println(Thread.currentThread().getName() + ": running");
          ready = true;
          for (;;) { 
              Thread.yield();
          }
      }
      catch (Exception e) {
          System.out.println(Thread.currentThread().getName() + ": received interrupt " + e);
      }
      }
}
