package org.jikesrvm.tools.template;

/**
 * Created by IntelliJ IDEA.
 * User: peter
 * Date: Mar 3, 2007
 * Time: 11:09:16 PM
 * To change this template use File | Settings | File Templates.
 */
class UnterminatedStringException extends RuntimeException {
  private static final long serialVersionUID = 5639864127476661778L;
  public UnterminatedStringException() { super(); }
  public UnterminatedStringException(String msg) { super(msg); }
}
