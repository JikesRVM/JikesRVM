/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Emulate Jalapeno:  wait for socket connection
 *
 *
 */


class emuJalapeno {

  public static void main(String[] args) {
    String stringFromClient;
    tServer myServer = new tServer(1024);
    myServer.acceptConnection();
        
    System.out.println("connected, reading from client ...");
    // try receiving some text
    while (true) {
      stringFromClient = myServer.readCommand();
      if (stringFromClient!=null) {
	System.out.println("Jalapeno daemon receives: " + stringFromClient);
	break;
      }
      // else
      // System.out.println("Jalapeno daemon: no read");
    }

    // try sending some text
    myServer.writeResult("How are things");
    
  }

}
