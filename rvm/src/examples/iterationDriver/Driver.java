/**
 * Generic driver to run a program <N> times.
 * This driver notifies the Jikes RVM of each application run start.
 *
 * Usage: java Driver <N> <main class> <main routine> [program args .. .. ]
 */

import java.lang.reflect.*;

class Driver {
  public static void main(String args[]) {
    int N = Integer.parseInt(args[0]);
    System.out.println("Running " + N + " times");
    String klass = args[1];
    String methodName = args[2];
    System.out.println("Method: " + klass + "." + methodName);
    int appArgsLength = Math.max(args.length-3, 0);
    String[] appArgs = new String[appArgsLength];
    System.arraycopy(args,3,appArgs,0,appArgs.length);
    Object[] argv = new Object[1];
    argv[0] = appArgs;

    try {
      Class invokee = Class.forName(klass);
      Method[] methods = invokee.getMethods();
      Method m = null;
      for (int i=0; i<methods.length; i++) {
        if (methods[i].getName().equals(methodName)) {
          m = methods[i];
          break;
        }
      }

      for (int i=0 ; i<N; i++) {
        System.out.println ("Run " + i + " ... ");
        VM_Callbacks.notifyAppRunStart(i);
    	long elapsedTime = -System.currentTimeMillis();
        m.invoke(null,argv);
        elapsedTime += System.currentTimeMillis();
        System.out.println("ELAPSED TIME " + elapsedTime + " ms");
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
