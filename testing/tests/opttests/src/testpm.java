/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
class testpm {

   static int[] array = new int[10000];

   public static void main(String[] args) {
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
