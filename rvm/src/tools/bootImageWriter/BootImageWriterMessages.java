/*
 * (C) Copyright IBM Corp. 2001
 */
//BootImageWriterMessages.java
//$Id$

/**
 * Functionality to write messages during image generation.
 *
 * @author Derek Lieber
 * @version 03 Jan 2000
 */
public class BootImageWriterMessages {
  protected static void say(String message) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.println();
  }

  protected static void say(String message, String message1) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.println();
  }

  protected static void say(String message, String message1, String message2) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.print(message2);
    System.out.println();
  }

  protected static void say(String message, String message1, String message2,
                            String message3) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.print(message2);
    System.out.print(message3);
    System.out.println();
  }

  protected static void say(String message, String message1, String message2,
                            String message3, String message4) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.print(message2);
    System.out.print(message3);
    System.out.print(message4);
    System.out.println();
  }

  protected static void say(String message, String message1, String message2,
                            String message3, String message4, String message5) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.print(message2);
    System.out.print(message3);
    System.out.print(message4);
    System.out.print(message5);
    System.out.println();
  }

  protected static void say(String message, String message1, String message2,
                            String message3, String message4, String message5,
                            String message6) {
    System.out.print("BootImageWriter: ");
    System.out.print(message);
    System.out.print(message1);
    System.out.print(message2);
    System.out.print(message3);
    System.out.print(message4);
    System.out.print(message5);
    System.out.print(message6);
    System.out.println();
  }

  protected static void fail(String message) throws Error {
    throw new Error("\nBootImageWriter: " + message);
  }
}

