package org.jikesrvm.energy;

import org.mmtk.utility.statistics.Stats;
import org.mmtk.vm.VM;

public class PerfEvent {
	public int index;
	public String name;
	
	  /** The previously read value of the counter (used to detect overflow) */
//	  public static long[] previousValue;
	  private static long previousValue;
	
	  /** {@code true} if the counter overflowed */
	  private static boolean overflowed;
	
	  /** A buffer passed to the native code when reading values, returns the tuple RAW_COUNT, TIME_ENABLED, TIME_RUNNING */
//	  public static long[] readBuffer;
	  
	  //private final long[] readBuffer = new long[3];
	  private static final int RAW_COUNT = 0;
	  private static final int TIME_ENABLED = 1;
	  private static final int TIME_RUNNING = 2;
	  
	  /** {@code true} if any data was scaled */
	  //public static boolean dataWasScaled = false;
	  
	  public PerfEvent(int index, String name) {
		    this.index = index;
		    this.name = name;
	  }
	  
	  public PerfEvent() {}
	  
//	public static long[] getCurrentValue() {
////		VM.statistics.perfEventRead(index, readBuffer);
//		VM.statistics.perfEventRead(readBuffer);
//		for(int i = 0; i < Scaler.perfCounters; i++) {
//			if (readBuffer[i] < previousValue[i]) {
//				// value should monotonically increase
//				overflowed = true;
//				org.jikesrvm.VM.sysWriteln("Event counter is overflowed! ID is: " + i);
//			}
//			previousValue[i] = readBuffer[i];
//			org.jikesrvm.VM.sysWriteln("event value: " + readBuffer[i]);
//		}
//		
//		return readBuffer;
//	}
	
	public static long getCurrentValue(int index) {
		long[] readBuffer = new long[3];
		boolean dataWasScaled = false;
		VM.statistics.perfEventRead(index, readBuffer);
//		VM.statistics.perfEventRead(readBuffer);
		if (readBuffer[RAW_COUNT] < 0 || readBuffer[TIME_ENABLED] < 0
				|| readBuffer[TIME_RUNNING] < 0) {
			// Negative implies they have exceeded 63 bits.
			overflowed = true;
		}
		if (readBuffer[TIME_ENABLED] == 0) {
			// Counter never run (assume contention)
//			contended = true;
			org.jikesrvm.VM.sysWriteln("Event counter contended!");
		}
		if(readBuffer[RAW_COUNT] == 0) {
			org.jikesrvm.VM.sysWriteln("Event counter is 0");
		} else {
//			org.jikesrvm.VM.sysWriteln("Event counter is:" + readBuffer[RAW_COUNT]);
		}
		// Was the counter scaled?
		if (readBuffer[TIME_ENABLED] != readBuffer[TIME_RUNNING]) {
//			scaled = true;
			dataWasScaled = true;
			double scaleFactor;
			if (readBuffer[TIME_RUNNING] == 0) {
				scaleFactor = 0;
			} else {
				scaleFactor = readBuffer[TIME_ENABLED]
						/ readBuffer[TIME_RUNNING];
			}
			readBuffer[RAW_COUNT] = (long) (readBuffer[RAW_COUNT] * scaleFactor);
		}
		if (readBuffer[RAW_COUNT] < previousValue) {
			// value should monotonically increase
			overflowed = true;
		}
		previousValue = readBuffer[RAW_COUNT];
		return readBuffer[RAW_COUNT];
	}
}
