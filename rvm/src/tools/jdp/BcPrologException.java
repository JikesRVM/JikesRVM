/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * Exception for BootMap:  thrown for addresses that fall in the prolog of a method
 * @see jdpConstants
 * @author Ton Ngo
 */
class BcPrologException extends Exception
{
  public BcPrologException()  {
    super("address is in method prolog");
  }
}

