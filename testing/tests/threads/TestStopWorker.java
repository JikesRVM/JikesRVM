/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
