package energy;
import java.io.*;
import java.lang.reflect.Field;
class Scaler {
	public native static int scale(int freq);
	public native static int[] freqAvailable();
	public native static void SetGovernor(String gov);
	static {
		
				System.setProperty("java.library.path",
				System.getProperty("user.dir")+"/energy/64");
				System.out.println("Printing java.library.path below ...");
				System.out.println(System.getProperty("java.library.path"));
		try {
			Field fieldSysPath = ClassLoader.class
					.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (Exception e) {

		}
		System.loadLibrary("CPUScaler");
	}
	public static void main(String[] argv) {
		String option = null;
		String gov = null;
		option = argv[0];
		if(argv.length > 1) {
			gov = argv[1];
			if(!gov.equals("userspace")) {
				SetGovernor(gov);
				return;
			}
		}
		int[] a = freqAvailable();
		for (int i = 0; i < a.length; i++) {
			System.out.println(a[i]);
		}
		
		int opt = Integer.parseInt(option);
		if(opt < 1 || opt > 12) {
			System.out.println("Invalid Option");
			System.exit(0);
		}
		
		scale(a[opt-1]);
		/*
		if(Integer.parseInt(option) == 1) {
			scale(a[0]);
		} else if(Integer.parseInt(option) == 2) {
			scale(a[3]);
		} else if(Integer.parseInt(option) == 3) {
			scale(a[5]);
		} else if(Integer.parseInt(option) == 4) {
			scale(a[7]);
		} else if(Integer.parseInt(option) == 5) {
			scale(a[9]);
		} else if(Integer.parseInt(option) == 6) {
		       scale(a[11]);
		}*/	       
			
	}

}
