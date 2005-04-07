/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&


/**
 * Given a file name, open the file and read it byte by byte.
 * Helps to debug binary trace files.
 *
 * @author Peter F. Sweeney
 * @date 3/28/2003
 */

import java.io.*;
import java.util.*;

class readBinaryFile
{
  static private int debug = 3;
  
  // number of records to generate
  static private int length = 0;

  /*
   * Main routine
   *
   * Process command line options. Open trace file.  Write contents.
   */
  static public void main(String args[]) 
  {
    String filename = null;
    if (args.length < 1) {
      usage();
    }

    for (int i=0; i<args.length; i++) {
      String arg = args[i];
      if (arg.charAt(0) != '-') {
        if(debug>=2)System.out.println("main() filenames = \""+arg+"\"");
        filename = arg;
      } else if (arg.compareTo("-length") == 0) {
        String Length = args[++i];
        try {
          length = Integer.parseInt(Length);
        } catch (NumberFormatException e) {
          System.out.println("***main() -length "+Length+" throws NumberFormatException!***");
        }
      } else {
        System.err.println("***main() unrecognized command line argument \""+arg+"\"!***");
        usage();
      }
    }

    if (filename == null) {
      System.out.println("Must specify filename!");
      usage();
    }
    DataInputStream input_file = Utilities.openDataInputStream(filename);

    int index = 0;
    int QUANTUM = 16;
    byte[] quadword = new byte[QUANTUM];
    while (true) {
      try {
        byte atom = input_file.readByte();
        if ((index % QUANTUM) == 0) System.out.print(index+": ");
        System.out.print(atom+" ");
        quadword[index%QUANTUM] = atom;
        index++;
        if ((index % QUANTUM) == 0) {
          System.out.print("  \"");
          for (int i=0; i<QUANTUM; i++ ) {
            System.out.print((char)quadword[i]);
          }
          System.out.println("\"");
          if (index > length) break;
        }

      } catch (EOFException e) {
        if(debug>=2)System.out.println("    EOF in "+filename+"!");
        break;
      } catch (IOException e) {
        System.out.println("*** IO Exception in "+filename+"!***");
        System.exit(1);
      }
    }
  }
  /**
   * How to use this program!
   */
  static public void usage() {
    System.out.println("\nreadTraceFile usage\n"+
                       " Value options: option\n"+
                       "  trace_filename       String name of trace file.  If more than one trace file name is\n"+
                       "                       specified, combine all the records.\n"+
                       "  -length              number of bytes to read.\n"+
                       "\n");
    System.exit(1);
  }

}
