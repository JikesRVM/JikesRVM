/*
 * (C) Copyright IBM Corp. 2001
 */
/*
 * @author Ton Ngo
 */
import java.util.*;
import java.io.*;

class OsProcessException extends Exception {
  int status;

  public OsProcessException (int pstatus) {
    status = pstatus;
  }

  public OsProcessException (String msg) {
    super(msg);
  }

}
