/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * put your documentation comment here
 *
 * @author Walter Lee
 */
class VM_CircularBuffer {

  // Interface
  public VM_CircularBuffer (String fileName) {
    FileName = fileName;
    Fd = -1;              // Ugh: We can't properly initialize this 
                          // if VM_CircularBuffer is initialized at boot time.
    Counter = 0;
    Buffer = new byte[BufferSize];
  }

  /**
   * put your documentation comment here
   * @param fileName
   */
  public void setFileName (String fileName) {
    if (VM.VerifyAssertions)
      VM.assert(Fd == -1);
    FileName = fileName;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public String getFileName () {
    return  FileName;
  }

  /**
   * put your documentation comment here
   * @param word
   */
  public void writeInt (int word) {
    Fd = guardedOpen(Fd, FileName);
    Buffer[Counter++] = (byte)((word >>> 24) & 0xff);
    Buffer[Counter++] = (byte)((word >>> 16) & 0xff);
    Buffer[Counter++] = (byte)((word >>> 8) & 0xff);
    Buffer[Counter++] = (byte)((word) & 0xff);
    if (Counter == BufferSize)
      flushAndReset();
  }

  /**
   * put your documentation comment here
   */
  public void close () {
    flushAndReset();
    Fd = guardedClose(Fd);
  }

  /**
   * put your documentation comment here
   */
  public void flushAndReset () {
    VM_FileSystem.writeBytes(Fd, Buffer, 0, Counter);
    Counter = 0;
  }

  // Helpers
  private static int guardedOpen (int fd, String name) {
    if (fd == -1)
      fd = VM_FileSystem.open(name, VM_FileSystem.OPEN_WRITE);
    return  fd;
  }

  /**
   * put your documentation comment here
   * @param fd
   * @return 
   */
  private static int guardedClose (int fd) {
    if (fd != -1)
      fd = VM_FileSystem.close(fd);
    return  -1;
  }
  // Private fields
  static final int BufferSize = 400000;
  private String FileName;
  private int Fd;
  private int Counter;
  private byte[] Buffer;
}



