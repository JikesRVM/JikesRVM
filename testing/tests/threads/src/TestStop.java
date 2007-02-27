/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
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
