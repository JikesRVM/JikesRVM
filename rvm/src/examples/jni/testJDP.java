/*
 * (C) Copyright IBM Corp. 2001
 */
public class testJDP {

  public static int compute (int val) {
    // System.out.println(args[0].substring(0,8) + "-----" + args[0].substring(12));
    VM.debugBreakpoint();
    if (val == 1)
      return 123;
    else 
      return 456;
  }

  public static void main(String[] args) {
    System.out.println(System.getProperty("java.class.path"));
    System.out.println("   from Java: Hello World! ");
    System.out.println("   I am thread: " + Thread.currentThread().getName());
    for (int i=0; i<args.length; i++) {
    	System.out.println("   arg " + i + " : " + args[i]);
    }

    // first call, method may not be compiled yet
    compute(0);
    // second call, method should be compiled
    compute(1);
    
  }
}
