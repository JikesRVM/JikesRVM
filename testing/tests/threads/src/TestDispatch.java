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
class TestDispatch {

   static public int threadCount = 2;
   static public TestDispatchWorker [] workers = new TestDispatchWorker[threadCount];

   public static void main(String args[]) throws Exception {
       
     System.out.println("TestDispatch");
     for (int i=0; i<threadCount; i++) {
         String name = "    worker " + i;
         for (int j=0; j<i; j++)
             name = "                    " + name;   
         workers[i] = new TestDispatchWorker(name);
     }
     for (int i=0; i<threadCount; i++) 
         workers[i].start();
     boolean done = false;
     while (!done) {
         Thread.yield();
         done = true;
         for (int i=0; i<threadCount; i++) 
             if (!workers[i].isFinished) 
                 done = false;
     }
     TestDispatchWorker.say("main", "bye");
   }

}


