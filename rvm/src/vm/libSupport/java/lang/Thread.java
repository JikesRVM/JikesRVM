/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Wait;

/**
 * Jikes RVM implementation of a Java thread.
 *
 * @author Julian Dolby
 */
public class Thread extends VM_Thread implements Runnable {

  public final static int MIN_PRIORITY = 1;
  public final static int MAX_PRIORITY = 10;
  public final static int NORM_PRIORITY = 5;

  private static int createCount = 0;
    
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
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
    priority = NORM_PRIORITY;
    name = "main";
    group = ThreadGroup.root;
    group.addThread(this);
  }
    
  public Thread() {
    this(null, null, newName());
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }
    
  public Thread(Runnable runnable) {
    this(null, runnable, newName());
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }

  public Thread(Runnable runnable, String threadName) {
    this(null, runnable, threadName);
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }
    
  public Thread(String threadName) {
    this(null, null, threadName);
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }

  public Thread(ThreadGroup group, Runnable runnable) {
    this(group, runnable, newName());
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }

  public Thread(ThreadGroup group, String threadName) {
    this(group, null, threadName);
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
  }

  public Thread(ThreadGroup group, Runnable runnable, String threadName) {
    super();
    //-#if RVM_WITH_OSR
    isSystemThread = false;
    //-#endif
    if (threadName==null) throw new NullPointerException();
    this.name = threadName;
    this.runnable = runnable;
    this.priority = NORM_PRIORITY;
    Thread currentThread  = currentThread();

    if (currentThread.isDaemon())
      this.makeDaemon(true);

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
    return (Thread)VM_Thread.getCurrentThread();
  }

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
    return priority;
  }

  public final ThreadGroup getThreadGroup() {
    return group;
  }

  public synchronized void interrupt() {
    checkAccess();
    isInterrupted = true;
    super.kill(new InterruptedException("operation interrupted"), false);
  }
  
  public static boolean interrupted () {
    Thread current = currentThread();
    if (current.isInterrupted) {
      current.isInterrupted = false;
      return true;
    }
    return false;
  }
    
    
  public final synchronized boolean isAlive() {
    return super.isAlive;
  }
    
  private synchronized boolean isDead() {
    // Has already started, is not alive anymore, and has been removed from the ThreadGroup
    return started && !isAlive();
  }

  public final boolean isDaemon() {
    return super.isDaemon;
  }

  public boolean isInterrupted() {
    return isInterrupted;
  }

  public final synchronized void join() throws InterruptedException {
    if (started)
      while (!isDead())
        wait(0);
  }

  public final synchronized void join(long timeoutInMilliseconds) throws InterruptedException {
    join(timeoutInMilliseconds, 0);
  }
    
  public final synchronized void join(long timeoutInMilliseconds, int nanos) throws InterruptedException {
    if (timeoutInMilliseconds < 0 || nanos < 0)
      throw new IllegalArgumentException();
        
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
      wait(toWait);
      long waited = System.currentTimeMillis() - start;
      totalWaited+= waited;
      toWait -= waited;
      // Anyone could do a synchronized/notify on this thread, so if we wait
      // less than the timeout, we must check if the thread really died
      timedOut = (totalWaited >= timeoutInMilliseconds);
    }

  }
    
  private synchronized static String newName() {
    return "Thread-" + createCount++;
  }

  public final synchronized void resume() {
    checkAccess();
    super.resume();
  }

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
    if (!this.started) super.makeDaemon(isDaemon);
    else throw new IllegalThreadStateException();
  }

  public final void setName(String threadName) {
    checkAccess();
    if (threadName != null) this.name = threadName;
    else throw new NullPointerException();
  }

  public final synchronized void setPriority(int newPriority){
    checkAccess();
    if (newPriority < MIN_PRIORITY || newPriority > MAX_PRIORITY) {
      throw new IllegalArgumentException();
    }
    int tgmax = getThreadGroup().getMaxPriority();
    if (newPriority > tgmax) newPriority = tgmax;
    priority = newPriority;
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
    
  public synchronized void start()  {
    super.start();
    started = true;
  }
    
  public final void stop() {
    stop(new ThreadDeath());
  }
    
  public final synchronized void stop(Throwable throwable) {
    checkAccess();
    if (throwable != null) super.kill(throwable, true);
    else throw new NullPointerException();
  }

  public String toString() {
    return "Thread[" + this.getName() + "]";
  }
    
  public static void yield () {
    VM_Thread.yield();
  }
}
