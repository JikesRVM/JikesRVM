package kenan;

import org.dacapo.harness.Callback;      
import org.dacapo.harness.CommandLineArgs;   
import java.io.*;
import java.lang.reflect.*;

public class IterationCallBack extends Callback {


public static  int MAX_ITERATIONS = 100;
public static  int CURRENT_ITERATION = 1;

public static  long[] START_ITER_TS = new long[MAX_ITERATIONS];
public static  long[] STOP_ITER_TS = new long[MAX_ITERATIONS];

public static  double[] START_ITER_EN  = new double[MAX_ITERATIONS];
public static  double[] STOP_ITER_EN  =  new double[MAX_ITERATIONS];

private static final int FIRE_AFTER = 5;

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

		System.out.println(ev);
		return ev;
}


public void stop(boolean w) {
	System.out.println("stop");


	super.stop(w);
	STOP_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();
	STOP_ITER_EN[CURRENT_ITERATION-1] = read_jikesrvm_energy();

	CURRENT_ITERATION++;

	//if(CURRENT_ITERATION==5) {
		try {
			Class cls = Class.forName("org.jikesrvm.VM");
			System.out.println("Calling end iteration");
			Method m = cls.getDeclaredMethod("end_iteration", null);
			Object o = m.invoke(null, null);
		} catch(Exception exc) {
			 exc.printStackTrace();
		}
	//}
	
}


public void start(String benchmark) {

	super.start(benchmark);

	START_ITER_TS[CURRENT_ITERATION-1] = System.currentTimeMillis();


	START_ITER_EN[CURRENT_ITERATION-1] = read_jikesrvm_energy();

	
}

public IterationCallBack(CommandLineArgs args) {
	super(args);
}

public void complete(String benchmark, boolean valid) {


	long execution_time=0;
	double total_energy=0;
	try {
		FileWriter  fileWriter  = new FileWriter("iteration_times");
		PrintWriter printWriter = new PrintWriter(fileWriter);
		FileWriter  enFileWriter  = new FileWriter("iteration_energy");
		PrintWriter enPrinter = new PrintWriter(enFileWriter);
		FileWriter  execWriter  = new FileWriter("execution_time");
		PrintWriter execPrinter = new PrintWriter(execWriter);
		FileWriter  kenanWriter  = new FileWriter("kenan_energy");
		PrintWriter kenanPrinter = new PrintWriter(kenanWriter);
		for(int i=1; i<= CURRENT_ITERATION-1;i++) {
			printWriter.printf("%d,%d \n",START_ITER_TS[i-1],STOP_ITER_TS[i-1]);
			enPrinter.printf("%f,%f \n",START_ITER_EN[i-1],STOP_ITER_EN[i-1]);
			long iter_time = STOP_ITER_TS[i-1] - START_ITER_TS[i-1];
			double iter_en = STOP_ITER_EN[i-1] - START_ITER_EN[i-1];
			if(i>=6) {	
				execution_time+= iter_time;
				total_energy+=iter_en;
			}
		}
		printWriter.close();
		enPrinter.close();
		execPrinter.printf("%d",execution_time);
		kenanPrinter.printf("%f",total_energy);
		execWriter.close();
		kenanWriter.close();

	} catch(Exception exception) {
		System.out.println(exception.getMessage());
	}
}

}

