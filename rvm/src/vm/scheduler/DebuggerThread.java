/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import java.lang.reflect.Method;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Vector;

import org.vmmagic.unboxed.*;

/**
 * An interactive debugger that runs inside the virtual machine.
 * This thread is normally dormant and only scheduled for execution
 * by VM_Thread.threadSwitch() following receipt of a debug request 
 * signal (SIGQUIT).
 *
 * @author Derek Lieber
 * @date 28 April 1999 
 */
class DebuggerThread extends VM_Thread {

  DebuggerThread() {
    super(null);
    makeDaemon(true);
    //-#if RVM_WITH_OSR
    super.isSystemThread = true;
    //-#endif
  }

  public String toString() {
    return "DebuggerThread";
  }
      
  public void run() {
    while(true) {
      try {
        VM.sysWrite("debug> ");
        String [] tokens = readTokens();
        eval(tokens);
      } catch (Exception e) { 
        VM.sysWrite("oops: " + e + "\n"); 
      }
    }
  }

  private static final char EOF = 0xFFFF;
  private static String[] previousTokens;

  // Evaluate an expression.
  //
  private static void eval(String[] tokens) throws Exception {
    char command = tokens        == null ? EOF  // end of file
      : tokens.length == 0    ? ' '  // empty line
      : tokens[0].charAt(0);         // first letter of first token

    VM.sysWriteln("COMMAND IS '"+command+"'");
    switch (command)      {
    case ' ': // repeat previous command once
      if (previousTokens != null)
        eval(previousTokens);
      return;
         
    case '*': // repeat previous command once per second, until SIGQUIT is received
        if (previousTokens != null)
          for (VM_Scheduler.debugRequested = false; VM_Scheduler.debugRequested == false; ) {
            VM.sysWrite("\033[H\033[2J");
            eval(previousTokens);
            VM_Wait.sleep(1000);
          }
        return;
    }
         
    previousTokens = tokens;
      
    VM.sysWriteln("COMMAND IS '"+command+"'");
    switch (command) {
    case 't': // display thread(s)
      if (tokens.length == 1) { //
        for (int i = 0, col = 0; i < VM_Scheduler.threads.length; ++i) {
          VM_Thread thread = VM_Scheduler.threads[i];
          if (thread == null) continue;
          VM.sysWrite(rightJustify(thread.getIndex() + " ", 4) + leftJustify(thread.toString(), 40) + getThreadState(thread) + "\n");
        }
      } else if (tokens.length == 2) { // display specified thread
        int threadIndex = Integer.valueOf(tokens[1]).intValue();
        // !!TODO: danger here - how do we know if thread's context registers are
        //         currently valid (ie. thread is not currently running and 
        //         won't start running while we're looking at it) ?
        VM_Thread thread = VM_Scheduler.threads[threadIndex];

        VM.sysWrite(thread.getIndex() + " " + thread + " " + getThreadState(thread) + "\n");
               
        Address fp = (thread == VM_Thread.getCurrentThread()) 
          ? VM_Magic.getFramePointer()
          : thread.contextRegisters.getInnermostFramePointer();
        
        VM_Processor.getCurrentProcessor().disableThreadSwitching();
        VM_Scheduler.dumpStack(fp);
        VM_Processor.getCurrentProcessor().enableThreadSwitching();
      } else {
        VM.sysWrite("please specify a thread id\n");
      }
      return;
         
    case 'p': // print object 
      if (tokens.length == 2) {
        //-#if RVM_FOR_64_ADDR
        Address addr = Address.fromLong(Long.parseLong(tokens[1], 16));
        //-#else
        Address addr = Address.fromIntZeroExtend(Integer.parseInt(tokens[1], 16));
        //-#endif
        VM.sysWrite("Object at addr 0x");
        VM.sysWriteHex(addr);
        VM.sysWrite(": ");
        VM_ObjectModel.describeObject(addr.toObjectReference());
        VM.sysWriteln();
      } else {
        VM.sysWriteln("Please specify an address\n");
      }
      return;

    case 'd': // dump virtual machine state
      VM_Scheduler.dumpVirtualMachine();
      return;

    case 'c': // continue execution of virtual machine (make debugger dormant until next debug request)
      VM_Scheduler.debugRequested = false;
      VM_Scheduler.debuggerMutex.lock();
      yield(VM_Scheduler.debuggerQueue, VM_Scheduler.debuggerMutex);
      return;

    case 'q': // terminate execution of virtual machine
      VM.sysWrite("terminating execution\n");
      VM.sysExit(VM.EXIT_STATUS_MISC_TROUBLE);
      return;

    default:
      if (tokens.length == 1) { // offer help
        VM.sysWrite("Try one of:\n");
        VM.sysWrite("   t                - display all threads\n");
        VM.sysWrite("   t <threadIndex>  - display specified thread\n");
        VM.sysWrite("   p <hex addr>     - print (describe) object at given address\n");
        VM.sysWrite("   d                - dump virtual machine state\n");
        VM.sysWrite("   c                - continue execution\n");
        VM.sysWrite("   q                - terminate execution\n");
        VM.sysWrite("   <class>.<method> - call a method\n");
        VM.sysWrite("Or:\n");
        VM.sysWrite("   <enter>          - repeat previous command once\n");
        VM.sysWrite("   *                - repeat previous command once per second until SIGQUIT is received\n");
        return;
      }

      // call a method
         
      // For the moment we only evaluate calls to static methods that take no parameters
      // !!TODO: more elaborate parsing to accept arbitrary number of classname-qualifiers,
      // parameter lists, static variables, arrays, etc.
      if (tokens.length != 3 || !tokens[1].equals(".")) {
        VM.sysWrite("please specify <class>.<method>\n");
      } else {
        Class    cls = Class.forName(tokens[0]);
        Class[]  signature = new Class[0];
        Method   method = cls.getMethod(tokens[2], signature);
        Object[] args = new Object[0];
        method.invoke(null, args);
      }
      return;
    }
  }

   // Figure out what a thread is doing.
   //
   private static String
   getThreadState(VM_Thread t)
      {
      // scan per-processor queues
      //
      for (int i = 0; i < VM_Scheduler.processors.length; ++i)
         {
         VM_Processor p = VM_Scheduler.processors[i];
         if (p == null) continue;
         if (p.transferQueue.contains(t)) return "runnable (incoming) on processor " + i;
         if (p.readyQueue.contains(t))    return "runnable on processor " + i;
         if (p.ioQueue.contains(t))       return "waitingForIO (" + p.ioQueue.getWaitDescription(t) + ") on processor " + i;
         if (p.processWaitQueue.contains(t)) return "waitingForProcess (" + p.processWaitQueue.getWaitDescription(t) + ") on processor " + i;
         if (p.idleQueue.contains(t))     return "waitingForIdleWork on processor " + i;
         }
         
     // scan global queues
     //
     if (VM_Scheduler.wakeupQueue.contains(t))    return "sleeping";
     if (VM_Scheduler.debuggerQueue.contains(t))  return "waitingForDebuggerWork";
     if (VM_Scheduler.collectorQueue.contains(t)) return "waitingForCollectorWork";
      
     // scan lock queues
     //
     for (int i = 0; i < VM_Scheduler.locks.length; ++i) {
       VM_Lock l = VM_Scheduler.locks[i];
       if (l == null || !l.active) continue;
       if (l.entering.contains(t)) return ("waitingForLock" + i);
       if (l.waiting.contains(t))  return "waitingForNotification";
     }

     // not in any queue
     //
     for (int i = 0; i < VM_Scheduler.processors.length; ++i) {
       VM_Processor p = VM_Scheduler.processors[i];
       if (p == null) continue;
       if (p.activeThread == t)
         return "running on processor " + i;
     }
     return "unknown";
   }
       
   private static final int STDIN = 0;
   
  // Read and tokenize one line of input.
  // Taken:    nothing
  // Returned: null      -> no input (end of file encountered)
  //           String[0] -> no input (end of line encountered)
  //           String[N] -> N tokens of input
  //
  private static String[] readTokens() {
    String line = new String();
    int    bb = VM_FileSystem.readByte(STDIN);

    if (bb < 0)
      return null;
    
    for ( ; bb >= 0 && bb != '\n'; bb = VM_FileSystem.readByte(STDIN))
      line += (char)bb;

    Vector tokens = new Vector();
    for (int i = 0, n = line.length(); i < n; ++i) {
      char ch = line.charAt(i);
         
      if (isLetter(ch) || isDigit(ch)) {
        String alphaNumericToken = new String();
        while (isLetter(ch) || isDigit(ch)) {
          alphaNumericToken += ch;
          if (++i == n) break;
          ch = line.charAt(i);
        }
        --i;
        tokens.addElement(alphaNumericToken);
        continue;
      }

      if (ch != ' ' && ch != '\r' && ch != '\t') {
        String symbol = new String();
        symbol += ch;
        tokens.addElement(symbol);
        continue;
      }
    }
      
    String[] results = new String[tokens.size()];
    for (int i = 0, n = results.length; i < n; ++i)
      results[i] = (String)tokens.elementAt(i);
    return results;
  }

   private static boolean isDigit(char ch) {
     return '0' <= ch && ch <= '9';
   }

   private static boolean isLetter(char ch) {
     return ('a' <= ch && ch <= 'z') || 
       ('A' <= ch && ch <= 'Z');
   }
   
  private static String leftJustify(String s, int width) {
    while (s.length() < width)
      s = s + " ";
    return s;
  }
   
  private static String rightJustify(String s, int width) {
    while (s.length() < width)
      s = " " + s;
    return s;
  }
}
