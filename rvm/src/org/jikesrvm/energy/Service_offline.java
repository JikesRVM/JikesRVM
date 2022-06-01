

package org.jikesrvm.energy;
import java.io.*;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.MiscHeader;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

public class Service extends ProfilingTypes implements Runnable {
	public static final long THREASHOLD = 2000;
	
	public static final boolean changed = false;
	public static boolean isJraplInit = false;
	public static boolean isSamplingInit = false;
	public native static int scale(int freq);
	public native static int[] freqAvailable();
	public static final int INIT_SIZE = 500;

	public static String[] clsNameList = new String[INIT_SIZE];
	public static String[] methodNameList = new String[INIT_SIZE];
	public static long[] methodCount = new long[INIT_SIZE];
	
	public static int mthInvokCount = 0;
	
	public static DataOutputStream dump;


	public static char [] info = {'i','o', '\n'};

	/*public static long [] timerStack = new long[INIT_SIZE];
	public static long timerPointer;

	public static long [] counterStack = new long[INIT_SIZE];
	public static long counterPointer;*/

	public static int currentPos = 0;

	public static void accmMethodCount() {
		mthInvokCount++;
	}
	public static int addMethodEntry(String cls, String name){
//		if(!isSamplingInit) {
//			ProfileStack.InitStack(EnergyCheckUtils.socketNum);
//			AccmProfile.initProfInfo(EnergyCheckUtils.socketNum);
//			isSamplingInit = true;
//		}
//		if(!isJraplInit) {
//			EnergyCheckUtils.initJrapl();
//			isJraplInit = true;
//		}
//		System.out.println("socketNum: " + EnergyCheckUtils.socketNum + "waparound: " + EnergyCheckUtils.wraparoundValue);
		
		//Check boundaries of arrays
		if (methodCount.length - 1 == currentPos){
			int len = methodCount.length;
			String[] newClsNameList = new String[len * 2];
			String[] newMethodNameList = new String[len * 2];
			long[] newMethodCount = new long[len * 2];
			if (VM.VerifyAssertions)
				VM._assert(clsNameList.length - 1 == currentPos && methodNameList.length - 1== currentPos && methodCount.length - 1== currentPos, "Service error");
			
			System.arraycopy(clsNameList,0,newClsNameList,0,len);
			System.arraycopy(methodNameList,0,newMethodNameList,0,len);
			System.arraycopy(methodCount,0,newMethodCount,0,len);
			clsNameList = newClsNameList;
			methodNameList = newMethodNameList;
			methodCount = newMethodCount;
//			VM.sysWriteln("-------------methodNameList length: " + methodNameList.length);
		}
//		VM.sysWriteln("TimeProfiling MethodNameMap: " + cls + "." + name + " " + currentPos);
		methodNameList[currentPos] = name;
		clsNameList[currentPos] = cls;
		methodCount[currentPos] = 0;
		currentPos++;
		return currentPos - 1;
	}

	public static void doPush(int index) {
		int round = 0;
		long count = ++methodCount[index];
		long[] energy = EnergyCheckUtils.getEnergyStats();
//		long[] time = TimeCheckUtils.getCurrentThreadTimeInfo();
		long threadId = Thread.currentThread().getId();
		
		if(index == 0) 
			VM.sysWriteln("main thread id: " + threadId);
		
//		VM.sysWriteln("TIME: " + TimeCheckUtils.getCurrentThreadTimeInfo());
		ProfileStack.Push(COUNT, count); 
		ProfileStack.Push(WALLCLOCKTIME, System.currentTimeMillis());
		ProfileStack.Push(INDEX, index);
//		ProfileStack.Push(USERTIME, time[0]);
//		ProfileStack.Push(CPUTIME, time[1]);
		ProfileStack.Push(THREADID, threadId);
		
		//push energy information for each socket
		for (int i = 0; i < EnergyCheckUtils.socketNum; i++) {
			round = i * 3;
			ProfileStack.Push(DRAMENERGY + round, energy[0 + round]);
			ProfileStack.Push(CPUENERGY + round, energy[1 + round]);
			ProfileStack.Push(PKGENERGY + round, energy[2 + round]);
		}
	}

	public static synchronized void startSampling(int index){
//		System.out.println("**************start sampling**************");
		ProfileStack.Push(SAMPLESIGN, 0);
		doPush(index);

	}

	public static synchronized void endSampling(int index){
//		System.out.println("**************end sampling**************");
		ProfileStack.Push(SAMPLESIGN, 1);
		accmMethodCount();
		doPush(index);
		
		if(index == 0 || mthInvokCount % 1000 == 0) {
			VM.sysWriteln("method invocation count: " + mthInvokCount);
			try {
				dump = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("EnergyStackDump.txt", true)));
			} catch (SecurityException e1) {
				e1.printStackTrace();
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
			}
			try {
				while (!ProfileStack.isEmpty()) {
//					System.out.println("count value get popped on stack: " + ProfileStack.Pop(COUNT));
//					VM.sysWriteln("count value get popped on stack: " + ProfileStack.Pop(COUNT));
					//Output order is important since considering the parsing efficiency.
					dump.writeBytes(Long.toString(ProfileStack.Pop(THREADID)) + ",");
					dump.writeBytes(Long.toString(ProfileStack.Pop(SAMPLESIGN)) + ",");
					dump.writeBytes(Long.toString(ProfileStack.Pop(INDEX)) + ",");
					dump.writeBytes(Long.toString(ProfileStack.Pop(COUNT)) + ",");
					dump.writeBytes(Long.toString(ProfileStack.Pop(WALLCLOCKTIME)));

					for (int i = 0; i < EnergyCheckUtils.socketNum; i++) {
						dump.writeBytes("," + Long.toString(ProfileStack.Pop(DRAMENERGY)));
						dump.writeBytes("," + Long.toString(ProfileStack.Pop(CPUENERGY)));
						dump.writeBytes("," + Long.toString(ProfileStack.Pop(PKGENERGY)));
					}

					
					dump.writeBytes(System.getProperty("line.separator"));

				}
				dump.flush();
				dump.close();
//				InputStream is = new FileInputStream("EnergyStackDump.txt");
//
//				DataInputStream dis = new DataInputStream(is);
//				int byteRead = 0;
//				StringBuilder sb = new StringBuilder();
//				while((byteRead = dis.read()) != -1) {
//
//						
//						sb.append(byteRead);
////						sb.append(System.getProperty("line.Seperator"));
//					
//				}
//				VM.sysWriteln(sb.toString());
//				System.out.println("********************");
//				System.out.println(sb.toString());

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
			
	}

	//TODO: how to deal with other array, like char[] array?
	//Set hander for this arr, next time barrier access the array would reset it.
	public static void ioArgSampling(Object obj){
		if (VM.isBooted && VM.dumpMemoryTrace){
			MiscHeader.setIOTag(obj);
			Address addr = ObjectReference.fromObject(obj).toAddress().plus((int)VM.ioMemAccOffset);
			if (VM.dumpMemoryTrace)
				VM.sysAppendMemoryTrace(addr);
		}
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
}

/*
 * ==========================================
 * Java File I/O: in RVM could do FILE IO : )
 * ==========================================
 *
 * PrintWriter writer = new PrintWriter("the-file-name.txt", "UTF-8");
		writer.println("The first line");
		writer.println("The second line");
		writer.close();
 * */



/*================================
 * Copy Array
 * ===============================
 *
String [] tmp1 = new String[2*len];
String [] tmp2 = new String[2*len];
long [] tmp3 = new long[2*len];
//System.arraycopy(arg0, arg1, arg2, arg3, arg4);
clsNameList = tmp1;
methodNameList = tmp2;
methodCount = tmp3;
 */


/*============================================================
 * These code, i.e load libary, unable to call by RVM, even RVM
 * running scaler could not success.
 *
System.load("/home/jianfeih/workspace/jacob/Jikes-Miss-Prediction/CPUScaler/libCPUScaler.so");
System.out.println("libary loaded");
if (!changed){
	int[] a = freqAvailable();
	scale(a[6]);
	changed f true;
}
 */
