/*
 * (C) Copyright IBM Corp. 2001
 */


class testpm {

   static int array[] = new int[10000];

   public static void main(String args[]) {
      VM_BootRecord bootRecord = VM_BootRecord.the_boot_record;
      VM_Magic.sysCall0(bootRecord.sysPMstartIP,bootRecord.sysPMstartTOC);
      int s = 0;
      for (int i=0; i < 10000; i++)
          for (int j= 0; j < 10000; j++)
              s = s + array[j];
      VM_Magic.sysCall0(bootRecord.sysPMstopIP,bootRecord.sysPMstopTOC);

      for (int i=0; i < 10000; i++)
          for (int j= 0; j < 10000; j++)
              s = s + array[j];

      VM_Magic.sysCall0(bootRecord.sysPMstopIP,bootRecord.sysPMstopTOC);
   }
}
