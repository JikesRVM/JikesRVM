/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * @author unascribed
 */
class NativeThreadsWorker extends Thread
   {
///static VM_ProcessorLock mutex = new VM_ProcessorLock();

   String     name;
   boolean    isFinished;
   
   NativeThreadsWorker(String name)
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

     //- overrides Thread
     //      
     public void
       run()   {
       int tid = VM_Magic.getThreadIdRegister() >>  VM_ObjectLayoutConstants.OBJECT_THREAD_ID_SHIFT;
       int retval = 0;
       int loopcntr = 75;
       float fp  = (float)17.8;
       if ( name == "pong") loopcntr = 75;
       for (int i = 0; i < loopcntr; ++i)
         {
           if ( (name == "ping") ||( name == "ping2") ) {
             //  call a native function
             say(name, "calling nativeFoo");
             retval = tNativeThreads.nativeFoo(VM_Processor.getCurrentProcessor().id);
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
             } 
             catch (InterruptedException e) {}
             say(name, "after sleep -calling javaFoo");
             retval = tNativeThreads.javaFoo(10);
             say(name, "return from javaFoo");
           }

           if ( name == "pong") {
             // someone calls gc
             say(name, "about to call gc");
             //      say(name, "first dump VM");
             //      VM_Scheduler.dumpVirtualMachine();
             //      say(name, "skipping gc");
                     System.gc();
             say(name, "gc completed");
           }
         }
       //test complete
       ///     say(name, "dump before -bye");
       ///       VM_Scheduler.dumpVirtualMachine();
       say(name, "complete -bye");
       isFinished = true;
     }


   synchronized static void
   say(String who, String what)
      {
      int pid = VM_Processor.getCurrentProcessorId();
      //      System.out.println("pid- " + pid + " " + who + ": " + what);
      VM_Scheduler.trace(who,what);
      }
   }

