/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class VM_ForNameCallBacks {
  static final private int MAX_CALL_BACKS = 10;
  static private VM_ForNameCallBack[] callBacks;
  static private int count;

  /**
   * put your documentation comment here
   * @param x
   * @exception VM_TooManyCallBacks
   */
  static void addCallBack (VM_ForNameCallBack x) throws VM_TooManyCallBacks {
    if (callBacks == null) {
      callBacks = new VM_ForNameCallBack[MAX_CALL_BACKS];
      count = 0;
    }
    if (count >= MAX_CALL_BACKS) {
      throw  new VM_TooManyCallBacks(count);
    }
    callBacks[count++] = x;
  }

  /**
   * put your documentation comment here
   * @param type
   */
  static void doCallBacks (VM_Type type) {
    if (VM.runningVM) {
      for (int i = 0; i < count; i++)
        callBacks[i].notifyOfForName(type);
    }
  }
}



