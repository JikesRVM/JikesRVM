package kenan;

import org.dacapo.harness.Callback;      
import org.dacapo.harness.CommandLineArgs;   
import java.io.*;
import java.lang.reflect.*;

public class NoJikesIterationCallBack extends Callback {


public static  int MAX_ITERATIONS = 100;
public static  int CURRENT_ITERATION = 1;
public static  long[] START_ITER_TS = new long[MAX_ITERATIONS];
public static  long[] STOP_ITER_TS = new long[MAX_ITERATIONS];
private static final int FIRE_AFTER = 5;

public void stop(boolean w) {
	super.stop(w);
	STOP_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();
	CURRENT_ITERATION++;
}


public void start(String benchmark) {
	super.start(benchmark);
	START_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();
	

}

public NoJikesIterationCallBack(CommandLineArgs args) {
	super(args);
}

public void complete(String benchmark, boolean valid) {

	long execution_time=0;
	try {
		FileWriter  fileWriter  = new FileWriter("iteration_times");
		PrintWriter printWriter = new PrintWriter(fileWriter);
		FileWriter  execWriter  = new FileWriter("execution_time");
		PrintWriter execPrinter = new PrintWriter(execWriter);
		for(int i=1; i<= CURRENT_ITERATION-1;i++) {
			printWriter.printf("%d,%d \n",START_ITER_TS[i-1],STOP_ITER_TS[i-1]);
			long iter_time = STOP_ITER_TS[i-1] - START_ITER_TS[i-1];
			execution_time+= iter_time;
		}
		printWriter.close();
		execPrinter.printf("%d",execution_time);
		execWriter.close();

	} catch(Exception exception) {
		System.out.println(exception.getMessage());
	}
}

}

