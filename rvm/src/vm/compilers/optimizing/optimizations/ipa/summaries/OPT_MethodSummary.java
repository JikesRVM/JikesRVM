/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Hold semantic information about a method that is not defined in
 * VM_Method.
 *
 * @author Stephen Fink
 */
public class OPT_MethodSummary {

  /**
   * @param m VM_Method representing this method.
   */
  OPT_MethodSummary (VM_Method m) {
    vmMethod = m;
  }

  /**
   * Record that a parameter may or may not escape from a thread.
   *
   * @param p the number of the parameter
   * @param b may it escape?
   */
  public void setParameterMayEscapeThread (int p, boolean b) {
    _parameterDoesNotEscape[p] = !b;
  }

  /**
   * Query whether a parameter may escape from a thread.
   * @param p the number of the parameter
   * @return false iff the parameter <em> must not </em> escape from the
   * thread. true otherwise.
   */
  public boolean parameterMayEscapeThread (int p) {
    return  !_parameterDoesNotEscape[p];
  }

  /**
   * Record that a result of this method may or may not escape from a thread.
   *
   * @param b may it escape?
   */
  public void setResultMayEscapeThread (boolean b) {
    _resultMayEscape = b;
  }

  /**
   * Query whether the result of this method may escape from a thread.
   * @return false iff the parameter <em> must not </em> escape from the
   * thread. true otherwise.
   */
  public boolean resultMayEscapeThread () {
    return  _resultMayEscape;
  }

  /**
   * Is analysis of this method in progress?
   */
  public boolean inProgress () {
    return  inProgress;
  }

  /**
   * Mark that analysis of this method is or is not in progress.
   * @param b
   */
  public void setInProgress (boolean b) {
    inProgress = b;
  }

  /**
   * the method summarized
   */
  VM_Method vmMethod;
  /**
   * maximum number of parameters to the method supported.
   */
  private final static int MAX_PARAMETERS = 50;
  /**
   * Is this method currently being analyzed?  Used for recursive
   * invocations of the optimizing compiler.
   */
  private static boolean inProgress = false;
  /**
   * For each parameter, have we determined the actual parameter
   * <em> must not </em> escape?
   */
  boolean[] _parameterDoesNotEscape = new boolean[MAX_PARAMETERS];
  /**
   * Have we determined that the method result <em> may </em> escape?
   */
  boolean _resultMayEscape = true;
}



