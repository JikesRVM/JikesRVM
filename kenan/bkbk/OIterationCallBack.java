package kenan;
import java.io.*;
import java.lang.reflect.*;
import dacapo.Callback;

public class OIterationCallBack extends Callback {
  
	
	
public OIterationCallBack() {
    try {
      System.out.println("OIteration Callback started");
      Class cls = Class.forName("org.jikesrvm.VM");
      Method m = cls.getDeclaredMethod("get_startup_ts", null);
      Object o =  m.invoke(null, null);
      jvm_start = (Long) o;
      System.out.println("Invovation Successfull");
      System.out.println(o);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
  }


public static double read_jikesrvm_energy() {
		double ev=0.0;
		try {
			Class cls = Class.forName("org.jikesrvm.VM");
			System.out.println("Reading Energy Value At Iteration Delimiter");
			Method m = cls.getDeclaredMethod("read_energy", null);
			ev = (Double) m.invoke(null, null);
		} catch(Exception exc) {
			 exc.printStackTrace();
		}
		return ev;
}

  public static  int MAX_ITERATIONS = 100;
  public static  int CURRENT_ITERATION = 1;
  public static  long[] START_ITER_TS = new long[MAX_ITERATIONS];
  public static  long[] STOP_ITER_TS = new long[MAX_ITERATIONS];
  public static  double[] START_ITER_EN  =  new double[MAX_ITERATIONS];
  public static  double[] STOP_ITER_EN  =  new double[MAX_ITERATIONS];
  private static final int FIRE_AFTER = 5;
  public static long jvm_start = 0;
  
  /* Start the timer and announce the begining of an iteration */
  public void start(String benchmark) {
   	super.start(benchmark);
        System.out.println("Benchmark Last Iteration is starting ...");
	startWarmup(benchmark);
  };

  public void startWarmup(String benchmark) {
          super.startWarmup(benchmark);
	  START_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();
	  START_ITER_EN[CURRENT_ITERATION-1] = read_jikesrvm_energy();
  };

  /* Stop the timer */
  public void stop() {
    super.stop();
  }
  public void stopWarmup() {
    super.stopWarmup();
  }
  /* Announce completion of the benchmark (pass or fail) */
  public void complete(String benchmark, boolean valid) { 
    super.complete(benchmark,valid);
    completeWarmup(benchmark, valid);
    try {
      FileWriter fileWriter = new FileWriter("iteration_times");
      PrintWriter printWriter = new PrintWriter(fileWriter);
      long execution_time=0;

      FileWriter  enFileWriter  = new FileWriter("iteration_energy");
      PrintWriter enPrinter = new PrintWriter(enFileWriter);
      FileWriter  execWriter  = new FileWriter("execution_time");
      PrintWriter execPrinter = new PrintWriter(execWriter);
      for(int i=1; i<= CURRENT_ITERATION-1;i++) {
        printWriter.printf("%d,%d \n",START_ITER_TS[i-1] -jvm_start,STOP_ITER_TS[i-1] - jvm_start);

	enPrinter.printf("%f,%f \n",START_ITER_EN[i-1],STOP_ITER_EN[i-1]);

	long iter_time = STOP_ITER_TS[i-1] - START_ITER_TS[i-1];
	execution_time+= iter_time;
      }

      printWriter.close();	
      execPrinter.printf("%d",execution_time);
      execWriter.close();

    } catch(Exception exception) {
      System.out.println(exception.getMessage());
    }
  };


  public void completeWarmup(String benchmark, boolean valid) {
    super.completeWarmup(benchmark, valid);
    STOP_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();
    STOP_ITER_EN[CURRENT_ITERATION-1] = read_jikesrvm_energy();
    CURRENT_ITERATION++;
    System.out.println("Iteration Stopping");
    try {
      Class cls = Class.forName("org.jikesrvm.VM");
      System.out.println("Calling end iteration");
      Method m = cls.getDeclaredMethod("end_iteration", null);
      Object o = m.invoke(null, null);
    } catch(Exception exc) {
      exc.printStackTrace();
    }
  };
}
