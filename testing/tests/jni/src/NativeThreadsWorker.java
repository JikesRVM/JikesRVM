/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
import org.jikesrvm.runtime.Magic;

class NativeThreadsWorker extends Thread {

   String     name;
   boolean    isFinished;

   NativeThreadsWorker(String name) {
      this.name = name;
      say(name, "creating");
   }

   public void start() {
      say(name, "starting");
      super.start();
   }

     //- overrides Thread
     //
     public void
       run()   {
       int tid = Magic.getThreadIdRegister() >>  ObjectLayoutConstants.OBJECT_THREAD_ID_SHIFT;
       int retval = 0;
       int loopcntr = 75;
       float fp  = (float)17.8;
       if (name == "pong") loopcntr = 75;
       for (int i = 0; i < loopcntr; ++i) {
           if ((name == "ping") ||(name == "ping2")) {
             //  call a native function
             say(name, "calling nativeFoo");
             retval = tNativeThreads.nativeFoo(RVMThread.getCurrentThread().getIndex());
             //      retval = tNativeThreads.nativeFoo(tid);
             ////             System.out.println(1.0f);
             say(name, "return from nativeFoo");
             //     VM.debugBreakpoint();

             // try to print a string
             //      say(name, " before s created");
             //      String s = java.lang.Float.toString(fp);
             //      say(name, " after s created");
             //             String s2 = " return value = " + s;
             //             say(name, s2);

             //try {
             //  say(name, "sleeping");
             //  sleep(300);
             //}
             //catch (InterruptedException e) {}
           } else {// pong story
             // others sleep for a while and then call java  function
             say(name, "about to sleep for 100 msec");
             try {
               say(name, "sleeping");
               sleep(100);
             } catch (InterruptedException e) {}
             say(name, "after sleep -calling javaFoo");
             retval = tNativeThreads.javaFoo(10);
             say(name, "return from javaFoo");
           }

           if (name == "pong") {
             // someone calls gc
             say(name, "about to call gc");
             //      say(name, "first dump VM");
             //      RVMThread.dumpVirtualMachine();
             //      say(name, "skipping gc");
                     System.gc();
             say(name, "gc completed");
           }
         }
       //test complete
       ///     say(name, "dump before -bye");
       ///       RVMThread.dumpVirtualMachine();
       say(name, "complete -bye");
       isFinished = true;
     }


   static synchronized void say(String who, String what) {
      RVMThread.trace(who,what);
      }
   }

