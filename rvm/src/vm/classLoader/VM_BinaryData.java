/*
 * (C) Copyright IBM Corp. 2001
 */

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Contents of a binary data file.
 *  The access methods provided here are similar to those of 
 * java.io.DataInputStream, but faster.
 */
public class VM_BinaryData {
  private byte data[];
  private int  index;

  // Data provided by caller
  //
  public VM_BinaryData(byte[] data) {
    this.data  = data;
    this.index = -1;
  }

  // Data from a zip or jar archive.
  //
  VM_BinaryData(InputStream input, int length) throws IOException {
    byte[] data = new byte[length];
    for (int index = 0; length > 0; ) {
      int n = input.read(data, index, length);
      if (n < 0)
	throw new IOException();
      index  += n;
      length -= n;
    }
    this.data  = data;
    this.index = -1;
  }

  // Data from a filesystem directory.
  //
  VM_BinaryData(String fileName) throws FileNotFoundException, IOException {
    FileInputStream input = new FileInputStream(fileName);
    int             len   = (int)(new File(fileName)).length();
    byte[] data = new byte[len];
    if (input.read(data, 0, len) != len)
      throw new IOException(fileName);
    input.close();
    this.data  = data;
    this.index = -1;
  }

  final public long readLong() {
    return ((long)(data[++index] & 0x000000ff) << 56)
          | ((long)(data[++index] & 0x000000ff) << 48)
          | ((long)(data[++index] & 0x000000ff) << 40)
          | ((long)(data[++index] & 0x000000ff) << 32)
          | ((long)(data[++index] & 0x000000ff) << 24)
          | ((long)(data[++index] & 0x000000ff) << 16)
          | ((long)(data[++index] & 0x000000ff) <<  8)
          | ((long)(data[++index] & 0x000000ff)      );
  }

  final public int readInt() {
      return  ((data[++index] & 0x000000ff) << 24)
            | ((data[++index] & 0x000000ff) << 16)
            | ((data[++index] & 0x000000ff) <<  8)
            | ((data[++index] & 0x000000ff)      );
   }

  final public int readUnsignedShort() {
    return  ((data[++index] & 0x000000ff) <<  8)
	  | ((data[++index] & 0x000000ff)      );
   }

  final public int readUnsignedByte() {
    return  (data[++index] & 0x000000ff);
  }
  
  final public void readBytes(byte[] dst) {
    for (int i = 0, n = dst.length; i < n; ++i)
      dst[i] = data[++index];
  }

  final public void skipBytes(int cnt) {
    index += cnt;
  }

  final public byte[] getBytes() {
    return data;
  }
   
  public final InputStream asInputStream() {
    return new ByteArrayInputStream( data );
  }
}
