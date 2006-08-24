/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestDispatchWorker extends Thread
   {
///static VM_ProcessorLock mutex = new VM_ProcessorLock();

   String     name;
   boolean    isFinished;
   
   TestDispatchWorker(String name)
      {
      this.name = name;
      say(name, "creating");
      }
      
   public void
   start() //- overrides Thread
      {
      say(name, "starting");
      super.start();
      }
      
   public void
   run() //- overrides Thread
      {
      for (int i = 0; i < 4; ++i)
         {
         say(name, "sleeping");
         try { sleep(1000); } catch (InterruptedException e) {}
         say(name, "running");
         System.gc();
         say(name, "gc completed");
         }
      say(name, "bye");
      isFinished = true;
      }

/***
 * static void
 * say(String who, String what)
 *    {
 *    if (VM.runningVM)
 *       {
 *       VM_Scheduler.trace(who, what);
 *       }
 *    else
 *       { // jdk
 *       System.out.println(who + ": " + what);
 *       }
 *    }
 ***/

   synchronized static void
   say(String who, String what)
      {
      System.out.println(who + ": " + what);
      }
   }
