/*
 * (C) Copyright IBM Corp. 2001
 */
// Measure time spent in various vm activities.
//
class VM_Timer
   {
   //-----------//
   // interface //
   //-----------//

   // subsystems
   //
   static final int CLASS_LOAD              =  0;
   static final int CLASS_RESOLVE           =  1;
   static final int METHOD_COMPILE          =  2;
   static final int REFERENCEMAP_GEN        =  3;
   static final int EXCEPTION_HANDLING      =  4;
   static final int GC                      =  5;

   // GC phases
   //
   static final int GC_STACK                =  6;
   static final int GC_STATICS              =  7;
   static final int GC_WORKQ                =  8;

   // runtime entrypoints
   //
   static final int RUNTIME_INVOKESTATIC    =  9;
   static final int RUNTIME_INVOKEVIRTUAL   = 10;
   static final int RUNTIME_INVOKESPECIAL   = 11;
   static final int RUNTIME_INVOKEINTERFACE = 12;
   static final int RUNTIME_GETSTATIC       = 13;
   static final int RUNTIME_PUTSTATIC       = 14;
   static final int RUNTIME_GETFIELD        = 15;
   static final int RUNTIME_PUTFIELD        = 16;
   static final int RUNTIME_INSTANCEOF      = 17;
   static final int RUNTIME_CHECKCAST       = 18;
   static final int RUNTIME_CHECKSTORE      = 19;
   static final int RUNTIME_NEWSCALAR       = 20;
   static final int RUNTIME_QUICKNEWSCALAR  = 21;
   static final int RUNTIME_QUICKNEWARRAY   = 22;
   static final int RUNTIME_NEWARRAYARRAY   = 23;

   static final int TYPECHECK_COMPUTEMATRIX = 24;

   static final int NUM_TIMERS              = 25;

   static void
   reset()
      {
      for (int i = 0; i < NUM_TIMERS; ++i)
         elapsedTimes[i] = 0;
      stackTop = -1;
      overallStartTime = VM_Time.now();
      }

   static void
   start(int timerIndex)
      {
      double now = VM_Time.now();

      // snapshot current timer
      //
      if (stackTop >= 0)
         elapsedTimes[activeTimers[stackTop]] += (now - activeStartTime);

      // start new timer
      //
      activeTimers[++stackTop] = timerIndex;
      activeStartTime = VM_Time.now();
      }

   static void
   stop(int timerIndex)
      {
      double now = VM_Time.now();

      // snapshot current timer
      //
      if (VM.VerifyAssertions) VM.assert(timerIndex == activeTimers[stackTop]);
      elapsedTimes[activeTimers[stackTop]] += (now - activeStartTime);

      // restart previous timer
      //
      --stackTop;
      activeStartTime = VM_Time.now();
      }

   static void
   report()
      {
      double now = VM_Time.now();

      if (VM.VerifyAssertions) VM.assert(description.length == NUM_TIMERS);

      double total    = now - overallStartTime;
      double subtotal = 0;
      for (int i = 0; i < NUM_TIMERS; ++i)
         {
         if (VM.TraceRuntimeTimes == false && description[i].startsWith("RUNTIME"))
            continue; // don't print timers that aren't enabled
         double time = elapsedTimes[i];
         VM.sysWrite(description[i] + format(time, 4, 6) + " sec\n");
         subtotal += time;
         }
      VM.sysWrite("ACCOUNTED FOR............" + format(subtotal, 4, 6) + " sec of " + format(total, 4, 6) + " sec\n");

      VM.sysWrite("running count of GC's completed = " + VM_Collector.collectionCount() + "\n");

      if (stackTop >= 0)
        VM.sysWrite("[ " + (stackTop + 1) + " timers still running ]\n");
      }

   //----------------//
   // implementation //
   //----------------//


   private static String description[] = { // order here must match constants above
                                            "CLASS_LOAD...............",
                                            "CLASS_RESOLVE............",
                                            "METHOD_COMPILE...........",
                                            "REFERENCEMAP_GEN.........",
                                            "EXCEPTION_HANDLING.......",
                                            "GC.......................",
                                            "GC_STACKS................",
                                            "GC_STATICS...............",
                                            "GC_WORKQUEUE.............",
                                            "RUNTIME_INVOKESTATIC.....",
                                            "RUNTIME_INVOKEVIRTUAL....",
                                            "RUNTIME_INVOKESPECIAL....",
                                            "RUNTIME_INVOKEINTERFACE..",
                                            "RUNTIME_GETSTATIC........",
                                            "RUNTIME_PUTSTATIC........",
                                            "RUNTIME_GETFIELD.........",
                                            "RUNTIME_PUTFIELD.........",
                                            "RUNTIME_INSTANCEOF.......",
                                            "RUNTIME_CHECKCAST........",
                                            "RUNTIME_CHECKSTORE.......",
                                            "RUNTIME_NEWSCALAR........",
                                            "RUNTIME_QUICKNEWSCALAR...",
                                            "RUNTIME_QUICKNEWARRAY....",
                                            "RUNTIME_NEWARRAYARRAY....",
                                            "TYPECHECK_COMPUTEMATRIX..",
                                            };

   private static double elapsedTimes[] = new double[NUM_TIMERS]; // accumulated sub-times
   private static int    activeTimers[] = new int[10];    // stack of up to 10 nested timer activations
   private static int    stackTop = -1;                   // stack index of most recently started subtimer
   private static double activeStartTime;                 // time at which that subtimer was started
   private static double overallStartTime;                // time at which all timers were reset

   static String
   format(double val, int beforeDecimal, int afterDecimal)
      {
      String s    = String.valueOf(val);
      int    dot  = s.indexOf('.');
      String expo = null;

      if ((dot == 1) || (dot == 2))
         {
         int e = s.indexOf('E');
         if (e != -1)
            expo = s.substring(e);
         }

      int numOfSpaces = beforeDecimal - dot;
      int n;
      StringBuffer ret;
      if (numOfSpaces < 0)
         {
         ret = new StringBuffer(s);
         n = afterDecimal + dot + 1;
         }
      else
         {
         ret = new StringBuffer("                                                                             ");
         ret.setLength(numOfSpaces);
         ret.append(s);
         n = beforeDecimal + afterDecimal + 1;
         }
      ret.setLength(n);

      int max = s.length() + beforeDecimal - dot;
      for (int i = max; i < n; ++i)
         ret.setCharAt(i, '0');

      if (expo != null)
         ret.append(expo);

      return ret.toString();
      }
   }
