/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2002, 2004
 *
 * $Id$
 */
package java.lang;

import com.ibm.JikesRVM.VM;     // for VM.sysWrite()
import com.ibm.JikesRVM.VM_Lock;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_UnimplementedError;
import com.ibm.JikesRVM.VM_Wait;

/**
 * Jikes RVM implementation of a Java thread.
 *
 * @author Julian Dolby
 * @modified Steven Augart -- so it doesn't subclass VM_Thread any more.
 */
public class Thread implements Runnable {

  public final static int MIN_PRIORITY = 1;
  public final static int MAX_PRIORITY = 10;
  public final static int NORM_PRIORITY = 5;

  private static int createCount = 0;
    
  protected VM_Thread vmdata;             // need to be accessible to
                                          // MainThread.

  private volatile boolean started = false;
    
  private String name = null;
    
  private ThreadGroup group = null;
    
  private Runnable runnable = null;
    
  private ClassLoader contextClassLoader = null;
    
  private volatile boolean isInterrupted;
    
  // Special constructor to create thread that has no parent.
  // Only for use by MainThread() constructor.
  // ugh. protected, should probably be default. fix this.
  //
  protected Thread(String argv[]){
    vmdata = new VM_Thread(this);
    
    //-#if RVM_WITH_OSR
    vmdata.isSystemThread = false;
    //-#endif
    vmdata.priority = NORM_PRIORITY;
    name = "main";
    group = ThreadGroup.root;
    group.addThread(this);
    // Is this necessary?  I've added it because it seems wrong to have a null
    // context class loader; maybe it's OK though?
    contextClassLoader = ClassLoader.getSystemClassLoader();
  }
    
  /** 
   * This constructor is only used to create the system threads.
   *
   * @deprecated This constructor should go away in favor of the two-argument
   * form. 
   */
  Thread(VM_Thread vmdata) {
    /* Initialize the name to NULL, then set it after the initializer is run.
     * This is awkward, but Java won't let us invoke the toString()
     * method on this object until the initializer it calls has been run.   */
    this(vmdata, null);
    this.name = "Jikes_RVM_Miscellaneous_System_Thread: " + toString();
  }
  

  /** This is only used to create the system threads.
   *
   * This is only used by 
   * java.lang.JikesRVMSupport.createThread(VM_Thread, String). 
   *
   * And THAT function is ONLY used by the VM_Thread() constructor, when
   * called with a NULL VM_Thread argument.  In turn, the constructor is only
   * called with that argument when we create the "boot thread".
   */

  Thread(VM_Thread vmdata, String myName) {
    final boolean dbg = false;
    
    if (dbg) VM.sysWriteln("Invoked Thread(VM_Thread, String)");
    this.vmdata = vmdata;
    if (dbg) VM.sysWriteln("  Thread(VM_Thread, String) wrote vmdata");
    // isSystemThread defaults to "true"
    vmdata.priority = NORM_PRIORITY;
    if (dbg) 
      VM.sysWriteln("  Thread(VM_Thread, String) wrote vmdata.priority");
    this.name = myName;
    if (dbg) VM.sysWriteln("  Thread(VM_Thread, String) wrote vmdata.name");
    group = ThreadGroup.root;
    if (dbg) VM.sysWriteln("  Thread(VM_Thread, String) wrote vmdata.group");
    // // We might still be in the process of booting the VM.  If so, leave us
    // // out of a threadGroup.
    // if ( group != null )
    group.addThread(this);
    if (dbg) 
      VM.sysWriteln("  Thread(VM_Thread, String) called group.addThread");
    // Is this necessary?  I've added it because it seems wrong to have a null
    // context class loader; maybe it's OK though?
    // contextClassLoader = ClassLoader.getSystemClassLoader();
    if (dbg) 
      VM.sysWriteln("  Thread(VM_Thread, String) set contextClassLoader");
  }

  public Thread() {
    this(null, null, newName());
  }
    
  public Thread(Runnable runnable) {
    this(null, runnable, newName());
  }

  public Thread(Runnable runnable, String threadName) {
    this(null, runnable, threadName);
  }
    
  public Thread(String threadName) {
    this(null, null, threadName);
  }

  public Thread(ThreadGroup group, Runnable runnable) {
    this(group, runnable, newName());
  }

  public Thread(ThreadGroup group, String threadName) {
    this(group, null, threadName);
  }

  public Thread(ThreadGroup group, Runnable runnable, String threadName) {
    vmdata = new VM_Thread(this);

    //-#if RVM_WITH_OSR
    vmdata.isSystemThread = false;
    //-#endif
    if (threadName==null) throw new NullPointerException();
    this.name = threadName;
    this.runnable = runnable;
    vmdata.priority = NORM_PRIORITY;
    Thread currentThread  = currentThread();

    if (currentThread.isDaemon())
      vmdata.makeDaemon(true);

    if (group == null) {
      SecurityManager currentManager = System.getSecurityManager();
      // if there is a security manager...
      if (currentManager != null) {
        // Ask SecurityManager for ThreadGroup...
        group = currentManager.getThreadGroup();
                
        // ...but use the creator's group otherwise
        if (group == null) {
          group = currentThread.getThreadGroup();
        }
      } else {
        // Same group as Thread that created us
        group = currentThread.getThreadGroup();
      }
    }
    
    group.checkAccess();
    group.addThread(this);
    this.group = group;
        
    if (currentThread != null) { // Non-main thread
      contextClassLoader = currentThread.contextClassLoader;
    } else { 
      // no parent: main thread, or one attached through JNI-C
      // Just set the context class loader
      contextClassLoader = ClassLoader.getSystemClassLoader();
    }
  }

  public static int activeCount(){
    return currentThread().getThreadGroup().activeCount();
  }

  public final void checkAccess() {
    SecurityManager currentManager = System.getSecurityManager();
    if (currentManager != null) currentManager.checkAccess(this);
  }

  public void exit() {
    group.removeThread(this);
  }

  public int countStackFrames() {
    return 0;
  }

  public static Thread currentThread () { 
    Thread t = VM_Thread.getCurrentThread().getJavaLangThread();
    final boolean dbg2 = false;
    if ( dbg2 )
      VM.sysWriteln("Thread.currentThread(): About to return " + t);
    return t;
  }

  /** The JDK docs say "This method is not implemented".  We won't implement
      it either, nor will we even have it throw an exception. */
  public void destroy() {

  }

  public static void dumpStack() {
    new Throwable().printStackTrace();
  }

  public static int enumerate(Thread[] threads) {
    return currentThread().getThreadGroup().enumerate(threads, true);
  }

  public ClassLoader getContextClassLoader() {
    return contextClassLoader;
  }

  public final String getName() {
    return String.valueOf(name);
  }

  public final int getPriority() {
    return vmdata.priority;
  }

  public final ThreadGroup getThreadGroup() {
    return group;
  }

  public void interrupt() {
    synchronized (vmdata) {
      checkAccess();
      isInterrupted = true;
      vmdata.kill(new InterruptedException("operation interrupted"), false);
    }
  }
  
  public static boolean interrupted () {
    Thread current = currentThread();
    if (current.isInterrupted) {
      current.isInterrupted = false;
      return true;
    }
    return false;
  }
    
    
  public final boolean isAlive() {
    synchronized (vmdata) {
      return vmdata.isAlive();
    }
  }
    
  private boolean isDead() {
    // Has already started, is not alive anymore, and has been removed from the ThreadGroup
    synchronized (vmdata) {
      return started && !isAlive();
    }
  }

  public final boolean isDaemon() {
    return vmdata.isDaemonThread();
  }

  public boolean isInterrupted() {
    return isInterrupted;
  }

  public final void join() throws InterruptedException {
    synchronized (vmdata) {
      if (started)
        while (!isDead())
          vmdata.wait(0);
    }
  }

  public final void join(long timeoutInMilliseconds) 
    throws InterruptedException 
  {
    join(timeoutInMilliseconds, 0);
  }
    
  public final void join(long timeoutInMilliseconds, int nanos) 
    throws InterruptedException 
  {
    if (timeoutInMilliseconds < 0 || nanos < 0)
      throw new IllegalArgumentException();
        
    synchronized (vmdata) {
      if (!started || isDead()) return;
        
      // No nanosecond precision for now, we would need something like 'currentTimenanos'
        
      long totalWaited = 0;
      long toWait = timeoutInMilliseconds;
      boolean timedOut = false;

      if (timeoutInMilliseconds == 0 & nanos > 0) {
        // We either round up (1 millisecond) or down (no need to wait, just return)
        if (nanos < 500000)
          timedOut = true;
        else
          toWait = 1;
      }
      while (!timedOut && isAlive()) {
        long start = System.currentTimeMillis();
        vmdata.wait(toWait);
        long waited = System.currentTimeMillis() - start;
        totalWaited+= waited;
        toWait -= waited;
        // Anyone could do a synchronized/notify on this thread, so if we wait
        // less than the timeout, we must check if the thread really died
        timedOut = (totalWaited >= timeoutInMilliseconds);
      }
    }
  }
    
  /** The JDK 1.4.2 API says:
   * « Automatically generated names are of the form "Thread-"+n, where n is an
   *   integer. »
   */
  private synchronized static String newName() {
    return "Thread-" + createCount++;
  }

  public final void suspend () {
    checkAccess();
    synchronized (vmdata) {
      vmdata.suspend();
    }
  }

  public final synchronized void resume() {
    checkAccess();
    synchronized (vmdata) {
      vmdata.resume();
    }
  }

  /** Either someone subclasses Thread and overrides the  runnable() method or
   * they call one of Thread's constructors that takes a Runnable.  */
  public void run() {
    if (runnable != null) {
      runnable.run();
    }
  }
    
  public void setContextClassLoader(ClassLoader cl) {
    contextClassLoader = cl;
  }

  public final void setDaemon(boolean isDaemon) {
    checkAccess();
    synchronized (vmdata) {
      if (!started) 
        vmdata.makeDaemon(isDaemon);
      else 
        throw new IllegalThreadStateException();
    }
  }

  public final void setName(String threadName) {
    checkAccess();
    if (threadName != null) this.name = threadName;
    else throw new NullPointerException();
  }

  public final void setPriority(int newPriority){
    checkAccess();
    if (newPriority < MIN_PRIORITY || newPriority > MAX_PRIORITY) {
      throw new IllegalArgumentException();
    }
    int tgmax = getThreadGroup().getMaxPriority();
    if (newPriority > tgmax) newPriority = tgmax;
    synchronized (vmdata) {
      vmdata.priority = newPriority;
    }
  }
    
  public static void sleep (long time) throws InterruptedException {
    VM_Wait.sleep(time);
  }
    
  public static void sleep(long time, int nanos) throws InterruptedException {
    if (time >= 0 && nanos >= 0)
      sleep(time);
    else
      throw new IllegalArgumentException();
  }
    
  public void start()  {
    synchronized (vmdata) {
      vmdata.start();
      started = true;
    }
  }
    
  public final void stop() {
    stop(new ThreadDeath());
  }
    
  public final void stop(Throwable throwable) {
    checkAccess();
    synchronized (vmdata) {
      if (throwable != null) vmdata.kill(throwable, true);
      else throw new NullPointerException();
    }
  }

  /** jdk 1.4.2 documents toString() as returning name, priority, and group. */
  public String toString() {
    return "Thread[ name = " + this.getName() + ", priority = " + getPriority()
      + ", group = " + getThreadGroup() + "]";
  }
    
  public static void yield () {
    VM_Thread.yield();
  }

  /** Does this Thread hold a lock on obj? */
  public boolean holdsLock(Object obj) {
    return VM_Lock.owns(obj, vmdata);
  }
  

}
