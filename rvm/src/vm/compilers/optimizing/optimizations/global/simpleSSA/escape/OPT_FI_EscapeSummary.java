/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This class holds the results of a flow-insensitive escape analysis
 * for a method.
 *
 * @author Stephen Fink
 */
class OPT_FI_EscapeSummary {

  /**
   * Returns true iff ANY object pointed to by symbolic register r
   * MUST be thread local
   */
  boolean isThreadLocal (OPT_Register r) {
    Object result = hash.get(r);
    if (result == null)
      return  false;
    if (result == THREAD_LOCAL)
      return  true;
    return  false;
  }

  /**
   * Returns true iff ANY object pointed to by symbolic register r
   * MUST be method local
   */
  boolean isMethodLocal (OPT_Register r) {
    Object result = hash2.get(r);
    if (result == null)
      return  false;
    if (result == METHOD_LOCAL)
      return  true;
    return  false;
  }

  /** 
   * record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this thread
   */
  void setThreadLocal (OPT_Register r, boolean b) {
    if (b == true) {
      hash.put(r, THREAD_LOCAL);
    } 
    else {
      hash.put(r, MAY_ESCAPE_THREAD);
    }
  }

  /** 
   * Record the fact that ALL object pointed to by symbolic register r
   * MUST (or may) escape this method
   */
  void setMethodLocal (OPT_Register r, boolean b) {
    if (b == true) {
      hash2.put(r, METHOD_LOCAL);
    } 
    else {
      hash2.put(r, MAY_ESCAPE_METHOD);
    }
  }

  /** Implementation */
  /**
   * A mapping that holds the analysis result for thread-locality for each
   * OPT_Register.
   */
  private JDK2_HashMap hash = new JDK2_HashMap();   
  /**
   * A mapping that holds the analysis result for method-locality for each
   * OPT_Register.
   */
  private JDK2_HashMap hash2 = new JDK2_HashMap();  

  /**
   * Static object used to represent analysis result
   */
  static Object THREAD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static Object MAY_ESCAPE_THREAD = new Object();
  /**
   * Static object used to represent analysis result
   */
  static Object METHOD_LOCAL = new Object();
  /**
   * Static object used to represent analysis result
   */
  static Object MAY_ESCAPE_METHOD = new Object();
}



