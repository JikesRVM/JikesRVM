/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestDispatch
   {
   public static void 
   main(String args[])
      throws Exception
      {
      System.out.println("TestDispatch");
      TestDispatchWorker a = new TestDispatchWorker("ping"); a.start();
      TestDispatchWorker b = new TestDispatchWorker("                   pong"); b.start();
      while (!a.isFinished && !b.isFinished)
         Thread.currentThread().yield();
      TestDispatchWorker.say("main", "bye");
      }
   }


