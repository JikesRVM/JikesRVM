/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Exception for BootMap:  thrown for methods that do not contain line number map
 * because they were not compiled with -g 
 * @see VM_LineNumberMap
 * @author Ton Ngo
 */
class LnNotAvailException extends Exception
{
  public LnNotAvailException()  {
    super("line number not available, was class compiled with -g ?");
  }
}

