package org.jikesrvm.energy;

import org.jikesrvm.VM;

public class AccmProfile {
	
	public static final int INIT_SIZE = 500;
	public static long[][] cumProfInfo;
	//Since we only record hot method, Using extra array records index
	//to prevent sparse array.
	public static int[] cumProfInfoIndex;
	public static int socket;
	public static int profileSize = 0;
	public static int entrySize = 10;
	
	public static void initProfInfo(int socketNum) {
		if (cumProfInfo == null) {
			socket = socketNum;
			cumProfInfo = new long[entrySize * socketNum][INIT_SIZE];
			cumProfInfoIndex = new int[INIT_SIZE];
			for(int i = 0; i < INIT_SIZE; i++) {
				cumProfInfoIndex[i] = -2;
			}
		}
	}
	
	public static void add(int row, int col, long data) {
		initProfInfo(EnergyCheckUtils.socketNum);
		profileSize++;
		//Check boundary
		if(cumProfInfo[row].length == profileSize) {
			int newSize = profileSize + (profileSize >> 1);
			long[][] copyProfInfo = new long[cumProfInfo.length][newSize];
			int[] copyProfInfoIndex = new int[newSize];
//			for(int i = 0; i < newSize; i++) {
//				cumProfInfoIndex[i] = -2;
//			}
			
			//Increase all entrySize entries boundaries
			for(int i = 0; i < entrySize * socket; i++) {
				if (VM.VerifyAssertions)
					VM._assert(profileSize == cumProfInfo[i].length, "AcmProfile error in line 41");
				
				System.arraycopy(cumProfInfo[i], 0, copyProfInfo[i], 0, profileSize);
				cumProfInfo[i] = copyProfInfo[i];
			}
			if (VM.VerifyAssertions)
				VM._assert(profileSize == cumProfInfoIndex.length, "AcmProfile error in line 47");
			System.arraycopy(cumProfInfoIndex, 0, copyProfInfoIndex, 0, profileSize);
			cumProfInfoIndex = copyProfInfoIndex;
		}
		cumProfInfo[row][profileSize] = data;
		cumProfInfoIndex[profileSize] = col;
		
	}
	
	public static long get(int sid, int index) {
		//TODO: Same method can be called multiple times, the final result should be accumulated in the end.
		for(int i = 0; i < cumProfInfoIndex.length; i++) {
			if(cumProfInfoIndex[i] == index) {
//				return cumProfInfo[sid][i];
				System.out.println("sid: " + sid + " index: " + index + "-----" + cumProfInfo[sid][i]);
			}
		}
		return -1;
	}
}
