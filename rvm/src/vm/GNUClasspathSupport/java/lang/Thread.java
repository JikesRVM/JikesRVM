package java.lang;

/*
 * Licensed Materials - Property of IBM,
 * (c) Copyright IBM Corp. 1998, 2001  All Rights Reserved
 */

import com.ibm.JikesRVM.librarySupport.ClassLoaderSupport;
import com.ibm.JikesRVM.librarySupport.ThreadBase;
import com.ibm.JikesRVM.librarySupport.ThreadSupport;
import com.ibm.JikesRVM.librarySupport.UnimplementedError;

public class Thread extends ThreadBase implements Runnable {
	private final static RuntimePermission SET_CONTEXT_CLASS_LOADER = new RuntimePermission("setContextClassLoader");

	public final static int MAX_PRIORITY = 10;		// Maximum allowed priority for a thread
	public final static int MIN_PRIORITY = 1;			// Minimum allowed priority for a thread
	public final static int NORM_PRIORITY = 5;		// Normal priority for a thread
	private static int createCount = 0;					// Used internally to compute Thread names that comply with the Java specification
	private static final int NANOS_MAX = 999999;		// Max value for nanoseconds parameter to sleep and join
	private static final int INITIAL_LOCAL_STORAGE_CAPACITY = 5;	// Initial number of local storages when the Thread is created
	private static final int NO_REF = 0;				// Symbolic constant, no threadRef assigned or already cleaned up

	// Instance variables
	private int threadRef = NO_REF;					// Used by the VM
	private volatile boolean started = false;			// If !isAlive(), tells if Thread died already or hasn't even started
	private String name = null;						// The Thread's name
	private int priority = NORM_PRIORITY;			// The Thread's current priority

	private ThreadGroup group = null;			// A Thread belongs to exactly one ThreadGroup
	private Runnable runnable = null;				// Target (optional) runnable object
	private Throwable stopThrowable = null;			// Used by the VM
	private ClassLoader contextClassLoader = null;	// Used to find classes and resources in this Thread
	private java.security.AccessControlContext accessControlContext;

   private volatile boolean isInterrupted;
    
    // Special constructor to create thread that has no parent.
    // Only for use by MainThread() constructor.
    // ugh. protected, should probably be default. fix this.
    //
    protected Thread(String argv[]){
	name = "main";
	group = ThreadGroup.root;
	setPriority(NORM_PRIORITY);
	group.addThread(this);
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
	super();
	if (threadName==null) throw new NullPointerException();
	this.name = threadName;		// We avoid the public API 'setName', since it does redundant work (checkAccess)
	this.runnable = runnable;	// No API available here, so just direct access to inst. var.
	Thread currentThread  = currentThread();
	if (currentThread.isDaemon())
	    // this.isDaemon = true; // We avoid the public API 'setDaemon', since it does redundant work (checkAccess)
	    this.makeDaemon(true); // We avoid the public API 'setDaemon', since it does redundant work (checkAccess)
	if (group == null) {
	    SecurityManager currentManager = System.getSecurityManager();
	    // if there is a security manager...
	    if (currentManager != null)
		// Ask SecurityManager for ThreadGroup
		group = currentManager.getThreadGroup();
	    else
		// Same group as Thread that created us
		group = currentThread.getThreadGroup();
	}
	
	initialize(group, currentThread);
	
	setPriority(currentThread.getPriority());	// In this case we can call the public API according to the spec - 20.20.10
    }

    private void initialize(ThreadGroup group, Thread parentThread) {
	group.checkAccess();
	// Adjust ThreadGroup references
	group.addThread(this);			// This will throw IllegalThreadStateException if the ThreadGroup has been destroyed already
	this.group = group;
	
	if (parentThread != null) { // Non-main thread
	    // By default a Thread "inherits" the context ClassLoader from its creator
	    contextClassLoader = parentThread.contextClassLoader;
	} else { // no parent: main thread, or one attached through JNI-C
	    // No need to initialize local storage (we use lazy initialization, and there is nothing to inherit)
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
	return ThreadSupport.getCurrentThread(); 
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
	return started && !isAlive() && threadRef == NO_REF;
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
	if (timeoutInMilliseconds < 0 || nanos < 0 || nanos > NANOS_MAX)
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

    public final synchronized void setPriority(int priority){
	checkAccess();
	if (MIN_PRIORITY <= priority && priority <= MAX_PRIORITY) {
	    int finalPriority = priority;
	    int threadGroupMaxPriority = getThreadGroup().getMaxPriority();
	    if (threadGroupMaxPriority < priority) finalPriority = threadGroupMaxPriority;
	    this.priority = finalPriority;
	    super.priority = finalPriority;
	} else throw new IllegalArgumentException();
    }
    
    public static void sleep (long time) throws InterruptedException {
	ThreadSupport.sleep(time);
    }
    
    public static void sleep(long time, int nanos) throws InterruptedException {
	if (time >= 0 && nanos >= 0 && nanos <= NANOS_MAX)
	    sleep(time);	// No nanosecond precision for now, the native method should take an extra parameter (nanos)
	else throw new IllegalArgumentException();
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
	return "Thread[" + this.getName() + "," + this.getPriority() + "," +  this.getThreadGroup().getName() + "]" ;
    }
    
    public static void yield () {
	ThreadSupport.yield(); 
    }
}
