/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Keep a log of interesting virtual machine activity.
 * Instrument your code using this style: 
 * "if (VM.BuildForEventLogging && VM.EventLoggingEnabled) 
 *    VM_EventLogger.logXXXEvent();"
 * 
 * @author Derek Lieber
 * @date 03 Feb 1999
 * 
 */
import java.io.FileOutputStream;
import java.io.IOException;

public class VM_EventLogger implements VM_Uninterruptible {
   //---------------//
   // instance data //
   //---------------//
   
   private double[] timeStamp;   // time at which event happened
   private int[]    processorId; // id of VM_Processor in which event happened
   private int[]    threadId;    // id of VM_Thread in which event happened
   
   private int[]    eventId;     // event type
   private int[]    eventData1;  // event-specific information
   private int[]    eventData2;  // event-specific information

   private int      eventIndex;  // next available slot in above arrays
   
   // elapsed times spent in network i/o operations
   //
   private int      netSelectContentions;
   private int      netSelectStartOperations;
   private int      netSelectStopOperations;
   private double   netSelectStartTime;
   private double   netSelectElapsedTime;

   private int      netReadStartOperations;
   private int      netReadRestartOperations;
   private int      netReadStopOperations;
   private double   netReadStartTime;
   private double   netReadElapsedTime;

   private int      netWriteStartOperations;
   private int      netWriteRestartOperations;
   private int      netWriteStopOperations;
   private double   netWriteStartTime;
   private double   netWriteElapsedTime;
   
   //-------------//
   // static data //
   //-------------//

   private static VM_EventLogger[] eventLoggers;          // one per virtual processor

   // Values for "eventId".
   //
   private static final int NETWORK_SELECT             =  0;
   private static final int NETWORK_WAIT               =  1;
   
   private static final int NETWORK_CONNECT_BEGIN      =  2;
   private static final int NETWORK_CONNECT_RETRY      =  3;
   private static final int NETWORK_CONNECT_END        =  4;
   
   private static final int NETWORK_ACCEPT_BEGIN       =  5;
   private static final int NETWORK_ACCEPT_RETRY       =  6;
   private static final int NETWORK_ACCEPT_END         =  7;
   
   private static final int NETWORK_READ_BEGIN         =  8;
   private static final int NETWORK_READ_RETRY         =  9;
   private static final int NETWORK_READ_END           = 10;
   
   private static final int NETWORK_WRITE_BEGIN        = 11;
   private static final int NETWORK_WRITE_RETRY        = 12;
   private static final int NETWORK_WRITE_END          = 13;
   
   private static final int SCHEDULER_WAIT_BEGIN       = 14;
   private static final int SCHEDULER_WAIT_END         = 15;
   private static final int SCHEDULER_NOTIFY_BEGIN     = 16;
   private static final int SCHEDULER_NOTIFY_END       = 17;
   private static final int SCHEDULER_NOTIFY_ALL_BEGIN = 18;
   private static final int SCHEDULER_NOTIFY_ALL_END   = 19;
   
   private static final int SCHEDULER_ENQUEUE          = 20;
   private static final int SCHEDULER_DEQUEUE          = 21;
   
   private static String[] eventIdNames = {
                                          "NETWORK_SELECT            ",
                                          "NETWORK_WAIT              ",
                                          
                                          "NETWORK_CONNECT_BEGIN     ",
                                          "NETWORK_CONNECT_RETRY     ",
                                          "NETWORK_CONNECT_END       ",
                                          
                                          "NETWORK_ACCEPT_BEGIN      ",
                                          "NETWORK_ACCEPT_RETRY      ",
                                          "NETWORK_ACCEPT_END        ",
                                          
                                          "NETWORK_READ_BEGIN        ",
                                          "NETWORK_READ_RETRY        ",
                                          "NETWORK_READ_END          ",
                                          
                                          "NETWORK_WRITE_BEGIN       ",
                                          "NETWORK_WRITE_RETRY       ",
                                          "NETWORK_WRITE_END         ",
                                          
                                          "SCHEDULER_WAIT_BEGIN      ",
                                          "SCHEDULER_WAIT_END        ",
                                          "SCHEDULER_NOTIFY_BEGIN    ",
                                          "SCHEDULER_NOTIFY_END      ",
                                          "SCHEDULER_NOTIFY_ALL_BEGIN",
                                          "SCHEDULER_NOTIFY_ALL_END  ",
                                          
                                          "SCHEDULER_ENQUEUE         ",
                                          "SCHEDULER_DEQUEUE         ",
                                          };
   
   // Values for "eventData1".
   //
   // Queue ids for SCHEDULER_ENQUEUE & SCHEDULER_DEQUEUE events.
   //
   public static final int ENTERING_QUEUE  =  0; // per-object queue for contention resolution
   public static final int WAITING_QUEUE   =  1; // per-object queue for wait/notify
   public static final int TRANSFER_QUEUE  =  2; // per-processor queue for incoming work
   public static final int READY_QUEUE     =  3; // per-processor queue for threads ready to run
   public static final int IO_QUEUE        =  4; // per-processor queue for threads waiting for i/o
   public static final int IDLE_QUEUE      =  5; // per-processor queue for idle thread
   public static final int WAKEUP_QUEUE    =  6; // global queue for sleeping threads
   public static final int GC_WAIT_QUEUE   =  7; // global queue for concurrent gc thread (RCGC)
   public static final int COLLECTOR_QUEUE =  8; // global queue for collector threads
   public static final int DEAD_QUEUE      =  9; // global queue for dead threads
   public static final int DEBUGGER_QUEUE  = 10; // global queue for debugger thread
   public static final int ATTACHTHREAD_QUEUE  = 11; // global queue for debugger thread
   public static final int NATIVE_COLLECTOR_QUEUE = 12; // global queue for native collector threads that
                                                        // run on native "purple" processors 
   public static final int DEAD_VP_QUEUE   =  13; // global queue for dead VPs i.e. waiting for new callToNatives
   public static final int FINALIZER_QUEUE =  14; // global queue for FinalizerThread when idle

   private static String[] eventDataNames = {
                                            "ENTERING_QUEUE    ",
                                            "WAITING_QUEUE     ",
                                            "TRANSFER_QUEUE    ",
                                            "READY_QUEUE       ",
                                            "IO_QUEUE          ",
                                            "IDLE_QUEUE        ",
                                            "WAKEUP_QUEUE      ",
                                            "GC_WAIT_QUEUE     ",
                                            "COLLECTOR_QUEUE   ",
                                            "DEAD_QUEUE        ",
                                            "DEBUGGER_QUEUE    ",
                                            "ATTACHTHREAD_QUEUE",
					    "NATIVE_COLLECTOR_QUEUE",
					    "DEAD_VP_QUEUE",
                                            };
   //-----------//
   // interface //
   //-----------//
   
   // network events
   
   public  static void  logNetSelectContention()                   { eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID].netSelectContentions += 1; }
   public  static void  logNetSelectBegin()                        { startNetSelectTimer(); logSelect(VM_Thread.getCurrentThread().getIndex(),     -1); }
   public  static void  logNetSelectEnd(int nready)                { stopNetSelectTimer();  logSelect(VM_Thread.getCurrentThread().getIndex(), nready); }
   
   public  static void  logNetWait(int socket)                     {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_WAIT,          socket); }

   public  static void  logNetConnectBegin(int socket)             {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_CONNECT_BEGIN, socket); }
   public  static void  logNetConnectRetry(int socket)             {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_CONNECT_RETRY, socket); }
   public  static void  logNetConnectEnd(int socket)               {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_CONNECT_END,   socket); }

   public  static void  logNetAcceptBegin(int socket)              {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_ACCEPT_BEGIN,  socket); }
   public  static void  logNetAcceptRetry(int socket)              {                        log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_ACCEPT_RETRY,  socket); }
   public  static void  logNetAcceptEnd(int socket, int newsocket) {                        log2(VM_Thread.getCurrentThread().getIndex(), NETWORK_ACCEPT_END,    socket, newsocket); }

   public  static void  logNetReadBegin(int socket)                { startNetReadTimer();   log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_READ_BEGIN,    socket); }
   public  static void  logNetReadRetry(int socket)                { restartNetReadTimer(); log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_READ_RETRY,    socket); }
   public  static void  logNetReadEnd(int socket)                  { stopNetReadTimer();    log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_READ_END,      socket); }

   public  static void  logNetWriteBegin(int socket)               { startNetWriteTimer();  log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_WRITE_BEGIN,   socket); }
   public  static void  logNetWriteRetry(int socket)               { restartNetWriteTimer();log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_WRITE_RETRY,   socket); }
   public  static void  logNetWriteEnd(int socket)                 { stopNetWriteTimer();   log1(VM_Thread.getCurrentThread().getIndex(), NETWORK_WRITE_END,     socket); }

   // thread scheduler events
   
   public  static void  logWaitBegin()              { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_WAIT_BEGIN);       }
   public  static void  logWaitEnd()                { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_WAIT_END);         }
   public  static void  logNotifyBegin()            { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_NOTIFY_BEGIN);     }
   public  static void  logNotifyEnd()              { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_NOTIFY_END);       }
   public  static void  logNotifyAllBegin()         { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_NOTIFY_ALL_BEGIN); }
   public  static void  logNotifyAllEnd()           { log0(VM_Thread.getCurrentThread().getIndex(), SCHEDULER_NOTIFY_ALL_END);   }
   
   public  static void  logEnqueue(VM_Thread t, int queueId) { if (queueId == IDLE_QUEUE || queueId == READY_QUEUE) /* don't flood event log */ return; log1(t.getIndex(), SCHEDULER_ENQUEUE, queueId); }
   public  static void  logDequeue(VM_Thread t, int queueId) { if (queueId == IDLE_QUEUE || queueId == READY_QUEUE) /* don't flood event log */ return; log1(t.getIndex(), SCHEDULER_DEQUEUE, queueId); }
   
   // other events !!TODO: incorporate into log
   
   public  static void  logRuntimeLockContentionEvent() {}
   public  static void  logOtherLockContentionEvent() {}
   public  static void  logDispatchEvent() {}
   public  static void  logIdleEvent() {}
   public  static void  logCompilationEvent() {}
   public  static void  logObjectAllocationEvent() {}
   public  static void  logGarbageCollectionEvent() {}
   public  static void  logOptDLSiteExecutedEvent() {}
   
   static void
   boot() throws VM_PragmaInterruptible 
      {
      if (eventLoggers == null)
         {
         int n = VM_Scheduler.numProcessors;
         eventLoggers = new VM_EventLogger[n];
         for (int i = 0; i < n; ++i)
            eventLoggers[i] = new VM_EventLogger();
         }
      }
      
   //----------------//
   // implementation //
   //----------------//
   
   private static final int MAX_EVENTS = 10000;
   
   private VM_EventLogger()
      {
      timeStamp   = new double[MAX_EVENTS];
      processorId = new int[MAX_EVENTS];
      threadId    = new int[MAX_EVENTS];
      eventId     = new int[MAX_EVENTS];
      eventData1  = new int[MAX_EVENTS];
      eventData2  = new int[MAX_EVENTS];
      
      eventIndex  = 0;
      }
   
   // Log events that have 0 associated data items.
   //
   private static void
   log0(int threadId, int eventId)
      {
      int            processorId = VM_Processor.getCurrentProcessorId();
      VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      int            eventIndex  = eventLogger.eventIndex;
      
      if (eventIndex == MAX_EVENTS)
         return;

      eventLogger.timeStamp[eventIndex]   = VM_Time.now();
      eventLogger.processorId[eventIndex] = processorId;
      eventLogger.threadId[eventIndex]    = threadId;
      eventLogger.eventId[eventIndex]     = eventId;

      eventLogger.eventIndex += 1;
      }

   // Log events that have 1 associated data item.
   //
   private static void
   log1(int threadId, int eventId, int eventData1)
      {
      int            processorId = VM_Processor.getCurrentProcessorId();
      VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      int            eventIndex  = eventLogger.eventIndex;
      
      if (eventIndex == MAX_EVENTS)
         return;

      eventLogger.timeStamp[eventIndex]   = VM_Time.now();
      eventLogger.processorId[eventIndex] = processorId;
      eventLogger.threadId[eventIndex]    = threadId;
      eventLogger.eventId[eventIndex]     = eventId;
      eventLogger.eventData1[eventIndex]  = eventData1;

      eventLogger.eventIndex += 1;
      }

   // Log events that have 2 associated data items.
   //
   private static void
   log2(int threadId, int eventId, int eventData1, int eventData2)
      {
      int            processorId = VM_Processor.getCurrentProcessorId();
      VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      int            eventIndex  = eventLogger.eventIndex;
      
      if (eventIndex == MAX_EVENTS)
         return;

      eventLogger.timeStamp[eventIndex]   = VM_Time.now();
      eventLogger.processorId[eventIndex] = processorId;
      eventLogger.threadId[eventIndex]    = threadId;
      eventLogger.eventId[eventIndex]     = eventId;
      eventLogger.eventData1[eventIndex]  = eventData1;
      eventLogger.eventData2[eventIndex]  = eventData2;

      eventLogger.eventIndex += 1;
      }

   // Log NETWORK_SELECT events, merging adjacent repetitions so they don't flood our buffer.
   //
   private static void
   logSelect(int threadId, int nready)
      {
      int            processorId = VM_Processor.getCurrentProcessorId();
      VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      int            eventIndex  = eventLogger.eventIndex;
      
      if (eventIndex == MAX_EVENTS)
         return;

      if (nready < 0)
         { // start of event
         if (eventIndex > 0 && eventLogger.eventId[eventIndex - 1] == NETWORK_SELECT)
            { // merge with preceeding event
            eventIndex -= 1;
            if (eventLogger.threadId[eventIndex] != threadId)
               eventLogger.threadId[eventIndex] = -1; // merge thread ids
            }
         else
            { // new event
            eventLogger.timeStamp[eventIndex]   = VM_Time.now();
            eventLogger.processorId[eventIndex] = processorId;
            eventLogger.threadId[eventIndex]    = threadId;
            eventLogger.eventId[eventIndex]     = NETWORK_SELECT;
            eventLogger.eventData2[eventIndex]  = 0; // number of event repetitions
            eventLogger.eventIndex += 1;
            }
         }
      else
         { // end of event
         if (VM.VerifyAssertions) VM._assert(eventIndex > 0 && eventLogger.eventId[eventIndex - 1] == NETWORK_SELECT);
         eventIndex -= 1;
         eventLogger.eventData1[eventIndex] = nready; // number of sockets ready
         eventLogger.eventData2[eventIndex] += 1;     // number of event repetitions
         }
      }

   private static void
   startNetSelectTimer() 
      { 
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netSelectStartOperations += 1;
      eventLogger.netSelectStartTime = VM_Time.now();
      }
      
   private static void
   stopNetSelectTimer()
      {
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netSelectStopOperations += 1;
      eventLogger.netSelectElapsedTime = VM_Time.now() - eventLogger.netSelectStartTime;
      }

   private static void
   startNetReadTimer()
      { 
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netReadStartOperations += 1;
      eventLogger.netReadStartTime = VM_Time.now();
      }
      
   private static void
   restartNetReadTimer()
      { 
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netReadElapsedTime = VM_Time.now() - eventLogger.netReadStartTime;
      eventLogger.netReadRestartOperations += 1;
      eventLogger.netReadStartTime = VM_Time.now();
      }
      
   private static void
   stopNetReadTimer()
      {
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netReadStopOperations += 1;
      eventLogger.netReadElapsedTime = VM_Time.now() - eventLogger.netReadStartTime;
      }

   private static void
   startNetWriteTimer()
      { 
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netWriteStartOperations += 1;
      eventLogger.netWriteStartTime = VM_Time.now();
      }
      
   private static void
   restartNetWriteTimer()
      { 
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netWriteElapsedTime = VM_Time.now() - eventLogger.netWriteStartTime;
      eventLogger.netWriteRestartOperations += 1;
      eventLogger.netWriteStartTime = VM_Time.now();
      }
      
   private static void
   stopNetWriteTimer()
      {
      VM_EventLogger eventLogger = eventLoggers[VM_Processor.getCurrentProcessorId() - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
      eventLogger.netWriteStopOperations += 1;
      eventLogger.netWriteElapsedTime = VM_Time.now() - eventLogger.netWriteStartTime;
      }

   // Write out each processor's event log and reset event index.
   // Taken:    file to write to
   // Returned: nothing
   //
   public static void
     dump(FileOutputStream out) throws IOException, VM_PragmaInterruptible 
      {
      if (eventLoggers == null)
         {
         VM.sysWrite("VM_EventLogger.dump: not initialized\n");
         return;
         }

      boolean oldState = VM.EventLoggingEnabled;
      VM.EventLoggingEnabled = false;
      
      // find earliest event
      //
      double earliestTimeStamp = 0;
      for (int processorId = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID; processorId <= VM_Scheduler.numProcessors; ++processorId)
         {
         VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
         int            eventIndex  = eventLogger.eventIndex;
         if (eventIndex == 0)
            continue;
         if (earliestTimeStamp == 0 || eventLogger.timeStamp[0] < earliestTimeStamp)
            earliestTimeStamp = eventLogger.timeStamp[0];
         }
      
      // write out event logs, counters, and timers
      //
      for (int processorId = VM_Scheduler.PRIMORDIAL_PROCESSOR_ID; processorId <= VM_Scheduler.numProcessors; ++processorId)
         {
         VM_EventLogger eventLogger = eventLoggers[processorId - VM_Scheduler.PRIMORDIAL_PROCESSOR_ID];
         int            eventIndex  = eventLogger.eventIndex;

         // write out event log
         //
         for (int i = 0; i < eventIndex; ++i)
            {
            double elapsedTime = eventLogger.timeStamp[i] - earliestTimeStamp;
            
            String str = "at " + format10(Double.toString(elapsedTime))
                     +  " p: " + format2(Integer.toString(eventLogger.processorId[i]))
                     +  " t: " + format2(Integer.toString(eventLogger.threadId[i]))
                     +  " e: " + eventIdNames[eventLogger.eventId[i]];

            switch (eventLogger.eventId[i])
               {
               case SCHEDULER_ENQUEUE:
               case SCHEDULER_DEQUEUE:
               str += "d: " + eventDataNames[eventLogger.eventData1[i]];
               break;

               case NETWORK_SELECT:
               str += "d: " + eventLogger.eventData1[i] + " sockets ready";
               if (eventLogger.eventData2[i] != 1)
                  str += " (after " + eventLogger.eventData2[i] + " attempts)";
               break;

               case NETWORK_CONNECT_BEGIN:
               case NETWORK_CONNECT_RETRY:
               case NETWORK_CONNECT_END:
               case NETWORK_ACCEPT_BEGIN:
               case NETWORK_ACCEPT_RETRY:
            // case NETWORK_ACCEPT_END: // below
               case NETWORK_READ_BEGIN:
               case NETWORK_READ_RETRY:
               case NETWORK_READ_END:
               case NETWORK_WRITE_BEGIN:
               case NETWORK_WRITE_RETRY:
               case NETWORK_WRITE_END:
               str += "d: socket " + eventLogger.eventData1[i];
               break;
               
               case NETWORK_ACCEPT_END:
               str += "d: socket " + eventLogger.eventData1[i]
                   +  " --> "      + eventLogger.eventData2[i];
               break;
               }

            write(out, str + "\n");
            }

         
         // write out counters and timers
         //
         write(out, "i/o operations for processor " + processorId + ": "
                 + " netSelect: " + eventLogger.netSelectContentions     + "/" + eventLogger.netSelectStartOperations  + "/" + eventLogger.netSelectStopOperations
                 + " netRead: "   + eventLogger.netReadStartOperations   + "/" + eventLogger.netReadRestartOperations  + "/" + eventLogger.netReadStopOperations
                 + " netWrite: "  + eventLogger.netWriteStartOperations  + "/" + eventLogger.netWriteRestartOperations + "/" + eventLogger.netWriteStopOperations
                 + "\n" );
         
         write(out, "i/o times for processor " + processorId + ":      "
                 + " netSelect: " + eventLogger.netSelectElapsedTime
                 + " netRead: "   + eventLogger.netReadElapsedTime  
                 + " netWrite: "  + eventLogger.netWriteElapsedTime 
                 + "\n" );
         
         // reset event log, counters, and timers
         //
         eventLogger.eventIndex = 0;

         eventLogger.netSelectContentions     = eventLogger.netSelectStartOperations  = eventLogger.netSelectStopOperations = 0;
         eventLogger.netReadStartOperations   = eventLogger.netReadRestartOperations  = eventLogger.netReadStopOperations   = 0;
         eventLogger.netWriteStartOperations  = eventLogger.netWriteRestartOperations = eventLogger.netWriteStopOperations  = 0;

         eventLogger.netSelectElapsedTime = 0;
         eventLogger.netReadElapsedTime   = 0;
         eventLogger.netWriteElapsedTime  = 0;
         }
            
      VM.EventLoggingEnabled = oldState;
      }

   // Format string into 2 character fixed width field.
   // Taken:      string
   // Returned:   string, right justified and blank padded
   //
   private static String
   format2(String str) throws VM_PragmaInterruptible
      {
      int len = str.length();
      return len < 2 ? "  ".substring(0, 2 - len) + str : str;
      }

   // Format string into 10 character fixed width field.
   // Taken:      string
   // Returned:   string, right justified and blank padded
   //
   private static String
   format10(String str) throws VM_PragmaInterruptible
      {
      int len = str.length();
      return len < 10 ? "          ".substring(0, 10 - len) + str : str;
      }
                                                                    
   private static void write(FileOutputStream out, String str) 
     throws IOException, VM_PragmaInterruptible {
     int    len   = str.length();
     byte[] ascii = new byte[len];
     str.getBytes(0, len, ascii, 0);
     out.write(ascii, 0, len);
   }
}
