/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
public class VM_OnExitCallBacks {
  private static final int MAX_EXIT_CALL_BACKS = 10;
  private static int numberOfCallBacks = 0;
  private static VM_OnExitCallBack[] callBacks = null;
  private static boolean callBacksStarted = false;
  private static Object lock = new Object();

  /**
   * put your documentation comment here
   * @param callBack
   */
  public static void addCallBack (VM_OnExitCallBack callBack) {
    synchronized (lock) {
      if (callBacks == null)
        callBacks = new VM_OnExitCallBack[MAX_EXIT_CALL_BACKS];
      if (numberOfCallBacks >= MAX_EXIT_CALL_BACKS)
        throw  new VM_TooManyCallBacks("too many onExit call backs");
      callBacks[numberOfCallBacks++] = callBack;
    }
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public static boolean started () {
    return  callBacksStarted || (callBacks == null);
  }
  private static Thread runner;

  /**
   * put your documentation comment here
   */
  public static void doCallBacks () {
    synchronized (lock) {
      if (callBacks != null && !callBacksStarted) {
        callBacksStarted = true;
        runner = new Thread() {

          /**
           * put your documentation comment here
           */
          public void run () {
            for (int i = 0; i < numberOfCallBacks; i++)
              callBacks[i].notifyOfExit();
          }
        };
        runner.start();
      }
    }
  }

  /**
   * put your documentation comment here
   */
  public static void await () {
    try {
      runner.join();
    } catch (Exception e) {
      VM.sysWrite("exit call backs failed");
    }
  }
}



