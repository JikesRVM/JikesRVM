/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestStop
   {
   public static void 
   main(String args[])
      throws Exception
      {
      System.out.println("TestStop");
      
      System.out.println(Thread.currentThread().getName() + ": creating");
      TestStopWorker w = new TestStopWorker();
      
      System.out.println(Thread.currentThread().getName() + ": starting");
      w.start();
      while (TestStopWorker.ready == false)
         try { Thread.currentThread().sleep(1000); } catch (InterruptedException e) {}

      System.out.println(Thread.currentThread().getName() + ": sending interrupt to " + w.getName());
      w.stop(new ClassNotFoundException());

      System.out.println(Thread.currentThread().getName() + ": waiting for TestStopWorker to die");
      while (w.isAlive())
         try { Thread.currentThread().sleep(1000); } catch (InterruptedException e) {}

      System.out.println("main: bye");
      }
   }
