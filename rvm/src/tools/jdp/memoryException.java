/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Exception for memory:  thrown when protected memory is accessed
 * @author Ton Ngo
 */
class memoryException extends Exception
{
  public memoryException(String msg)  {
    super(msg);
  }
}
