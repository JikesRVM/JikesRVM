/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class VM_TooManyCallBacks extends RuntimeException {

  /**
   * put your documentation comment here
   * @param   String message
   */
  VM_TooManyCallBacks (String message) {
    super(message);
  }

  /**
   * put your documentation comment here
   * @param   int count
   */
  VM_TooManyCallBacks (int count) {
    this(new Integer(count).toString());
  }
}



