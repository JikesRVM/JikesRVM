/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * Read one line from the console (standard input) and 
 * return selected words found therein.
 * @author Ton Ngo (borrowed from Derek Lieber)
 */
import java.io.IOException;
import java.util.Vector;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;

class CommandLine implements JDPCommandInterface
{
  /**
   * The string to be used as the prompt
   */
  private String prompt = "jdp>";
  
  /**
   * Flag to choose raw or cooked TTY mode for keyboard input
   * (raw mode allows command retrieval/editing/completion)
   */
  private boolean rawTTY = false;

  /**
   * Buffer for the text command being edited from the keyboard
   */
  private byte[]  buffer = new byte[0];       
  private byte[]  keystrokes; // to receive keystroke from tty
  private int     kIndex;     // index into the keystrokes byte array
  private int     kCount;     // number of keystrokes to be processed in the array
  private byte[]  blanks;     // for padding blanks to the right of the cursor

  /**
   * Current editing position in the text buffer
   */
  private int     bufferCursor;  

  /**
   * Command history for retrieval
   */
  private Vector history;        

  /**
   * Current recall position in the command history
   */
  private int    historyCursor;  

  /**
   * Current cursor coordinate
   */
  private int cursorX;
  private int cursorY;

  /**
   * tty input and output, to attach to the screen in raw tty mode
   */
  private FileInputStream  in;
  private FileOutputStream out;

  /**
   * Some escape sequence for controlling the screen:  beep, cursor placement
   * And some control characters
   */
  private final byte    beep  = (byte)'\007';
  private final byte    blank = (byte)' ';
  private final byte[]  home  = { (byte)'\033', (byte)'[', (byte)'H' };
  private static final byte K_NOP   = (byte)0x80;
  private static final byte K_UP    = (byte)0x81;
  private static final byte K_DN    = (byte)0x82;
  private static final byte K_LT    = (byte)0x83;
  private static final byte K_RT    = (byte)0x84;
  private static final byte K_ESC   = (byte)0x85;
  private static final byte K_TAB   = (byte)0x86;
  private static final byte K_BSP   = (byte)0x87;
  private static final byte K_DEL   = (byte)0x88;
  private static final byte K_ENTER = (byte)0x89;
  private static final byte K_HOME  = (byte)0x8a;
  private static final byte K_END   = (byte)0x8b;
  private static final byte K_PLUS  = (byte)'+';
  private static final byte K_MINUS = (byte)'-';
  private static final byte K_SPACE = (byte)' ';

  /** 
   * For debugging
   */
  static PrintStream         log = System.err;

  /**
   * To get access to process-specific info
   * This value may change if the process is killed, restarted, or reattached
   */
  private OsProcess user;

  /**
   * The command parsed from the console
   */
  private String cmd    = "?";

  /**
   * The argument list parsed from the console
   */
  private String args[] = new String[0];

  /**
   * Create an interface to the console
   * @param newprompt  A string to be used as the prompt for the command line
   * @param rawMode true if the terminal is in raw TTY mode, false if in cooked mode
   * (the external shell script needs to be configured correctly for the raw mode to work)
   * @return
   * @see
   */
  public CommandLine(String newprompt, boolean rawMode) {
    if (rawMode) {
      initScreen();
      history = new Vector();
    }
    prompt = newprompt;
    rawTTY = rawMode;
  }

  /**
   * Set a new command prompt
   * @param newprompt a string to be used as the command prompt
   * @return
   * @see
   */
  public void setPrompt(String newprompt) {
    prompt = newprompt;
  }


  /**
   * Selecting from the command history
   */
  private void up() {
    // log.println("TANGO: up key");
    if (--historyCursor < 0 || history.size() == 0)
      historyCursor = 0;
    else
      load((String)history.elementAt(historyCursor));
  }
      
  private void down()  {
    // log.println("TANGO: down key");
    if (++historyCursor >= history.size()) {
      historyCursor = history.size();
      load("");
    }
    else
      load((String)history.elementAt(historyCursor));
  }

  /**
   * Editing the command line 
   */
  private void left() {
    if (bufferCursor > 0) --bufferCursor;
  }
            
  private void right() {
    if (bufferCursor < buffer.length) ++bufferCursor;
  }
            
  private void home() {
    bufferCursor = 0;
  }

  private void end() {
    bufferCursor = buffer.length;
  }

  private void insert(byte ch) {
    byte[] tmp = new byte[buffer.length + 1];
    
    // copy everything upto cursor
    System.arraycopy(buffer, 0, tmp, 0, bufferCursor);
    
    // insert the character
    tmp[bufferCursor] = ch;
    
    // copy everything following cursor
    System.arraycopy(buffer, bufferCursor, tmp, bufferCursor + 1, 
		     buffer.length - bufferCursor);
    
    buffer = tmp;
  }

   private void delete() {
     if (bufferCursor == buffer.length)
       return;
     
     byte[] tmp = new byte[buffer.length - 1];
     
     // copy everything upto cursor
     System.arraycopy(buffer, 0, tmp, 0, bufferCursor);
     
     // copy everything following cursor
     System.arraycopy(buffer, bufferCursor + 1, tmp, bufferCursor, 
		      buffer.length - bufferCursor - 1);
     
     buffer = tmp;
   }

  /**
   * Make noise.
   */
  private void beep() {
    try {
      out.write(beep);
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  private void write(String s) { 
    try {
      for (int i = 0, n = s.length(); i < n; ++i)
	out.write(s.charAt(i));
    } catch (IOException e) {
      System.out.println("write fails: " + e.getMessage());
    }
  }
                  
  private int read() {
    try {
      int ch = in.read();
      if (ch == -1) System.exit(1); // eof
      return ch;
    } catch (IOException e) {
      System.out.println("read fails: " + e.getMessage());
      return 0;
    }
  }

  /**
   * Copy some text into the command buffer for editing
   * and adjust the cursor
   *
   */
  private void load(String text) {
    buffer = text.getBytes();
    bufferCursor = buffer.length;
  }

  /**
   * Screen update:  write the buffer to the current line
   * and place the cursor at the insertion point
   */
  void sync() {

    // log.println("sync: " + (new String(buffer)) + ", length " + buffer.length +
    // 	     ", cursor at " + cursorY + " : " + (prompt.length() + bufferCursor));

    int promptLength = prompt.length();
    try {
      // go to the beginning of the line, print the promt and blank out the old line
      write("\033[" + (cursorY) + ";" + 0 + "H");
      out.write(prompt.getBytes(), 0, promptLength);   
      out.write(blanks, 0, blanks.length-promptLength-1);

      // go to the beginning of the prompt and 
      // print the text in the edit buffer
      write("\033[" + (cursorY) + ";" + (promptLength+1) + "H");
      out.write(buffer, 0, buffer.length); 
    }
    catch (IOException e) {
      // Sometimes a write of more than 4096 chars to /dev/tty fails on AIX.
      // However, instead of reporting the failure, let's just silently keep going.
      // The problem seems to clear after subsequent writes and doesn't seem to disrupt
      // the screen display very much.
    }

    // then place the cursor at the desired location
    write("\033[" + (cursorY) + ";" + (prompt.length() + bufferCursor + 1) + "H");
  }

  /**
   * Initialize external screen.
   */
   private void initScreen() {
     try {
       in  = new FileInputStream("/dev/tty");
       out = new FileOutputStream("/dev/tty");
     } catch (IOException e) {
       System.out.println("initScreen fails: " + e.getMessage());
     }     

     locateCursor();

     // measure dimensions of output area
     // 1. move cursor to lower right corner: \033[999;999H
     // 2. ask where it landed:               \033[6n
     // 3. read back response:                \033[#;#R
     //
     write("\033[999;999H\033[6n");
     
     read();
     read();
     
     int height = 0;    // we don't use the height info
     for (;;) {
       int ch = read();
       if (ch == ';')
	 break;
       height = height * 10 + ch - '0';
     }
     
     int width = 0;
     for (;;) {
       int ch = read();
       if (ch == 'R')
	 break;
       width = width * 10 + ch - '0';
     }
     
     // the width tells how many blanks to pad to the right of the cursor
     blanks = new byte[width];
     for (int i = 0, n = blanks.length; i < n; ++i)
       blanks[i] = blank;

     // also use the width to expect the number of characters that could come
     // at once (from cut and paste)
     keystrokes = new byte[width];
     kIndex = 0;
     kCount = 0;
     
     // enable line wrapping
     write("\033[?7h");

     // put cursor back at the beginning of the line
     write("\033[" + (cursorY) + ";" + 0 + "H");
   }


  /**
   * Query the screen to find the coordinate of the cursor
   */
  private void locateCursor() {
    // Escape sequence for querying the screen
    // 1. ask where the cursor is:           \033[6n
    write("\033[6n");
    
    // 2. read back response:                \033[#;#R
    read();  // discard the first two characters
    read();
    
    cursorY = 0;
    for (;;) {
      int ch = read();
      if (ch == ';')
	break;
      cursorY = cursorY * 10 + ch - '0';
    }
    
    cursorX = 0;
    for (;;) {
      int ch = read();
      if (ch == 'R')
	break;
      cursorX = cursorX * 10 + ch - '0';
    }
    
  }

  /**
   * Read raw keytroke (unechoed) from the keyboard and map control keys.
   * (mapping is normally done by stty but it was disabled so that we can
   * capture the escape key sequence of the up/down keys to do command 
   * retrieval)
   * @return a properly mapped character 
   */
   private byte readKeystroke() {

     if (kIndex<kCount) {
       // if we have leftover character in the keystroke buffer from the last read
       return keystrokes[kIndex++];
     } 

     // reset the keystroke buffer and refresh the screen
     kIndex = 0;
     kCount = 0;
     sync();

     for (;;) {
       int cnt = 0;
       
       try { 
	 cnt = in.read(keystrokes); 
       } catch (IOException e) { 
	 // log.println("readKeystroke: " + e.getMessage());
	 return (byte)'?';
       }
       
       // if (cnt>1)
       // log.println("readKeystroke: read " + cnt + " char");
       
       if (cnt < 1) System.exit(1); // eof
       
       int ch = keystrokes[0];
       
       switch (ch) 
	 {
	   // Typing keys (normal text)
	   //
	 default:
	   if (ch < ' ') break;
	   if (cnt>1) {
	     // we get multiple text characters at once, probably from cut & paste
	     kCount = cnt;
	     kIndex = 0;
	     return (byte) keystrokes[kIndex++];
	   } else {
	     return (byte)ch;
	   }

	   // Control keys
	   //
	 case '\r': return K_ENTER;
	 case '\t': return K_TAB;
	 case '\b': return K_BSP;
	   
	   //
	   // Emacs-style control keys
	   //
	 case 0x10: return K_UP;   // ctl-p
	 case 0x0e: return K_DN;   // ctl-n
	 case 0x06: return K_RT;   // ctl-f
	 case 0x02: return K_LT;   // ctl-b
	 case 0x01: return K_HOME; // ctl-a
	 case 0x05: return K_END;  // ctl-e
	   
	   //
	   // ANSI- and AIX- escape sequences
	   // The 3 character sequence could be delivered once all together
	   // or in two sequences:  first the escape character, then the 
	   // remaining two characters
	   //
	 case '\033':
	   if (cnt == 1) {
	     try {
	       int cnt2 = in.available(); 
	       // see if this is really an escape sequence of 3 that is broken up
	       if (cnt2==2) {
		 cnt2 = in.read(keystrokes); 
		 if (cnt2==2) {
		   switch ((int)keystrokes[1])  // esc[#
		     {
		     case 'A': return K_UP;
		     case 'B': return K_DN;
		     case 'C': return K_RT;
		     case 'D': return K_LT;
		     case 'H': return K_HOME;
		     case 'P': return K_DEL;
		     default:  break;
		     }		    
		 }
	       } 
	       // no other pending characters, so it must be just the escape key hit
	       else {
		 return K_ESC;             // esc
	       }
	     } catch (IOException e) { 
	       // log.println("readKeystroke: " + e.getMessage()); 
	     }	     
	   }
	   
	   if (cnt == 3)
	     switch ((int)keystrokes[2])  // esc[#
	       {
	       case 'A': return K_UP;
	       case 'B': return K_DN;
	       case 'C': return K_RT;
	       case 'D': return K_LT;
	       case 'H': return K_HOME;
	       case 'P': return K_DEL;
	       default:  break;
	       }
	   
	   if (cnt == 6)              // esc[###q
	     {
               int n = ((int)keystrokes[2] - '0') * 100 + 
		 ((int)keystrokes[3] - '0') * 10 + 
		 ((int)keystrokes[4] - '0') * 1;
	       
               switch (n)
		 {
		 case 146: return K_END;
		 }
	     }
	   break; 
	 }
       
       // unrecognized control character or escape sequence
       //
       beep();
     }
   }

  /**
   * Handle command line editing after any key mapping from 
   * the raw keyboard input
   * @see readKeyStroke
   * @return a command line
   */
  private String acceptKeystroke() {
    // prepare to edit a new line
    //
    historyCursor = history.size();
    load("");
    
    byte keystroke = readKeystroke();
    // log.println("acceptKeystroke entry: " + keystroke);
    
    for (;;) {
      // log.println("acceptKeystroke loop: " + keystroke);
      
      switch (keystroke)	
	{
	case K_NOP:
	case K_TAB:
          break;
          
	case K_BSP:
          if (bufferCursor > 0)
	    {
	      --bufferCursor;
	      delete();
	    }
          else
	    beep();
          break;
	  
	case K_ESC: 	  {                        
	  // auto-completion based on jdp command, using the text up to the bufferCursor
	  String toComplete = new String(buffer, 0, bufferCursor);
	  String completed = CommandCompletion.complete(toComplete, user);
	  if (completed==null) {
	    log.println("completion not found for: " + toComplete);
	    beep();
	  }
	  else
	    load(completed); // need to load the command too 
	  break;
	}

	case K_SPACE: 	  {                        
	  // auto-completion based on jdp command, using the text up to the bufferCursor
	  // for a space, see if we can complete the command
	  String toComplete = new String(buffer, 0, bufferCursor);
	  String completed = CommandCompletion.complete(toComplete, user);
	  if (completed==null) {	    
	    // can't complete, if this command expects any more space
	    // quietly treat it as a normal space key
	    if (CommandCompletion.moreSpaceKeyExpected(toComplete)) {
	      insert(keystroke);
	      ++bufferCursor;
	    } else {
	      beep();
	    }
	  } else
	    load(completed); 
	  break;
	}
          
	case K_ENTER:  {
	  // add command to history
	  //
          String string = new String(buffer);
          if (string.length() != 0) { 
	    history.removeElement(string); 
	    history.addElement(string);
	  }
	  
          // prepare to edit a new line
          //
          historyCursor = history.size();
          load("");
          
          // return the command
          //
          return string;
	}
          
        case K_UP:   up();     break;
        case K_DN:   down();   break;
        case K_RT:   right();  break;
        case K_LT:   left();   break;
        case K_HOME: home();   break;
        case K_END:  end();    break;
        case K_DEL:  delete(); break;
          
        default:
        insert(keystroke);
        ++bufferCursor;
        }
    
      keystroke = readKeystroke();
    }
  }


  /**
   * Handle selection from command history buffer
   * after any command line editing
   * @see acceptKeyStroke
   * @return a command line
   */
  private String acceptCommand() {
    // feed a new line to start a prompt
    write("\n");

    // find the cursor coordinate to echo keystroke later
    locateCursor();

    // get the command from the user
    return acceptKeystroke();
  }

  /**
   * Read one line of input from the console.
   * The input is expected to be in cooked TTY mode.
   * Use this method if readFromRawTTY() doesn't work
   * due to terminal configuration.
   * The command input is parsed and saved to be queried later
   * @see readFromRawTTY, cmd, args
   */
  private String readFromCookedTTY() {
    StringBuffer buf = new StringBuffer();
    System.out.print(prompt); System.out.flush();
    try {
      for (;;) {
        int ch = System.in.read();
        if (ch == -1)
          break;
        if (ch == '\r')
          continue;
        if (ch == '\n')
          break;
        buf.append((char)ch);
      }
    } catch (IOException e) {
      buf.append("?");
    }

    return buf.toString();
  }

  /**
   * Read one line from the console
   * The input is expected to be in raw TTY mode so the shell script
   * needs to be configured correctly.
   * Use this method to get command retrieval/editing/completion
   * @see readFromCookedTTY, cmd, args
   */
  private String readFromRawTTY()
  {
    // find the cursor coordinate to echo keystroke later
    locateCursor();

    // get the command from the user
    String buf = acceptKeystroke();
    // feed a new line for the next screen output
    write("\n");

    return buf;
  }

  /**
   * Read a command from the console, parse and save it to query later
   * @see cmd, args
   */
  public void readCommand(OsProcess proc) {
    String str;
    user = proc;              // this access is only needed for command completion

    if (rawTTY) 
      str = readFromRawTTY();
    else 
      str = readFromCookedTTY();

    // parse the line into words
    String [] words = toArgs(str);

    // if nothing entered, keep the last command
    if (words!=null) {
      if (words.length != 0)  {
	cmd  = words[0];
	args = new String[words.length - 1];
	for (int i = 0; i < args.length; ++i)
	  args[i] = words[i+1];
      }
    }
  }

  /**
   * Return the first string from the console input
   * @param
   * @return cmd the command string 
   * @see
   */
  public String cmd()
  {
    return cmd;
  }

  /**
   *Return the remaining strings in the console input after the first string
   * @param
   * @return args  the argument lists
   * @see
   */
  public String[] args()
  {
    return args;
  }


  /**
   * parse a line into an array of words, useful for arguments parsing
   * @param line      a string (command and arguments)
   * @return          an array of words making up the string
   * @see
   */
  public static String[] toArgs(String line) {
    String lineargs[];
    String str = new String(line);
    Vector vec = new Vector();

    for (;;)  {
      while (str.startsWith(" "))
	str = str.substring(1);          // skip blanks
      if (str.length() == 0)
	break;                           // end of string
      int end = str.indexOf(" ");
      if (end == -1) {
	vec.addElement(str);
	str = "";
      }  else  {
	vec.addElement(str.substring(0, end));
	str = str.substring(end);
      }
    }

    // copy the vector into an array
    if (vec.size() != 0)  {
      lineargs = new String[vec.size()];
      for (int i = 0; i < lineargs.length; ++i)
	lineargs[i] = (String)vec.elementAt(i);
      return lineargs;
    }

    return null;
  }


  /**
   * Parse the class name from a string of the form class.variable[1][2]
   * (the class name is delimited by /)
   * Some combinations:      class.variable, class.variable.variable
   * @param name a string
   * @return the class name, null if not found
   * @see BootMap
   */

  public static String varParseClass(String name) {
    int dot = name.indexOf('.');
    if (dot!=-1)
      return name.substring(0,dot);
    else
      return name;
  }

  /**
   * Parse the field name(s) from a string of the form class.variable[1][2]
   * Some combinations:      class.variable, class.variable.variable
   * @param name a string
   * @return the field names array, null if not found
   * @see BootMap
   */
  public static String[] varParseField(String name) {
    String remain;
    String thisfield = name;
    int dot1, dot2, bracket;
      
    int numfield = varCountField(name);
    String fields[] = new String[numfield];
    remain = name;
    if (numfield==0) {
      return null;
    }

    for (int i=0; i<numfield; i++) {
      dot1 = remain.indexOf('.');
      dot2 = remain.indexOf('.', dot1+1);
      if (dot1!=-1 && dot2!=-1) {                        // in the middle
	thisfield = remain.substring(dot1+1,dot2);
	remain = remain.substring(dot1+1);
      } else if (dot1!=-1 && dot2==-1) {                 // last field
	thisfield = remain.substring(dot1+1);
      } else {                                           // shouldn't be here
	System.out.println("ERROR: parsing " + name);
      }

      bracket = thisfield.indexOf('[');
      if (bracket!=-1) {
	fields[i] = thisfield.substring(0,bracket);
      } else {
	fields[i] = thisfield;
      }
    } 

    return fields;

  }

  /**
   * Parse a string of the form class.variable[1][2], count the number of fields
   * @param name a string
   * @return the field count
   * @see 
   */
  private static int varCountField(String name) {
    String shortname;
    int dot, numfield;

    // count the number of fields
    shortname = name;
    numfield = 0;
    while (true) {
      dot = shortname.indexOf('.');
      if (dot!=-1) {
	numfield++;
	shortname = shortname.substring(dot+1);
      } else {
	break;
      }
    }
    return numfield;
  }


  /**
   * Parse the array dimension string from a string of the form class.variable[1][2]
   * @param name a string
   * @return an array of String, one for each field containing the array dimension 
   *         in the form [xx][xx],  empty String if no array dimension for this field
   * @see BootMap
   */
  public static String[] varParseFieldDimension(String name) {
    String remain;
    String thisfield = name;
    int dot1, dot2, bracket;

    int numfield = varCountField(name);
    String fields[] = new String[numfield];
    remain = name;
    if (numfield==0) {
      return null;
    }
      
    for (int i=0; i<numfield; i++) {
      dot1 = remain.indexOf('.');
      dot2 = remain.indexOf('.', dot1+1);
      fields[i] = "";

      if (dot1!=-1 && dot2!=-1) {                        // in the middle
	thisfield = remain.substring(dot1+1,dot2);
	remain = remain.substring(dot1+1);
      } else if (dot1!=-1 && dot2==-1) {                 // last field
	thisfield = remain.substring(dot1+1);
      } else {                                           // shouldn't be here
	System.out.println("ERROR: parsing " + name);
      }

      bracket = thisfield.indexOf('[');
      if (bracket!=-1) {
	fields[i] = thisfield.substring(bracket);
      } 	  


    } 

    return fields;

  }


  /**
   * Parse the array dimension from a string of the form class.variable[1][2]
   * @param name a string
   * @return an integer array of dimension, the length is the number of dimensions,
   *         or null if not found
   * @see BootMap
   */
  public static int[] varParseArrayDimension(String name) throws NumberFormatException {
    int bracketLeft, bracketRight;
    boolean gettingDimension = true;
    String dimString = name;
    int numDim = 0;
    int index = 0;
    int result[];

    // first count the dimensions to allocate an int array
    while (gettingDimension) {
      bracketRight = dimString.indexOf(']');
      if (bracketRight==-1) {
	gettingDimension = false;
      } else {
	numDim++;
	// System.out.println("parsing: " + dimString.length() + ", " + bracketRight);
	if (bracketRight==(dimString.length()-1))   // are we at the end yet?
	  gettingDimension = false;
	else
	  dimString = dimString.substring(bracketRight+1);
      }
    }
      
    // now go over the name again to get the element index for each dimension
    result = new int[numDim];
    index = 0;
    dimString = name;
    gettingDimension = true;
    while (gettingDimension) {
      bracketRight = dimString.indexOf(']');   // should always go in pair
      bracketLeft = dimString.indexOf('[');
      if (bracketRight==-1 || bracketLeft==-1) {
	gettingDimension = false;
      } else {
	result[index] = Integer.valueOf(dimString.substring(bracketLeft+1,bracketRight)).intValue();
	index++;	
	if (bracketRight==(dimString.length()-1))   // are we at the end yet?
	  gettingDimension = false;
	else
	  dimString = dimString.substring(bracketRight+1);
      }
    }

    return result;

  }


  /**
   * Parse the class name from a string of the form class.method:line
   * @param name a string
   * @return the class name, or empty String if there is no class name
   * @see BootMap.findAddress()
   */
  public static String breakpointParseClass(String name) {
    int dot = name.lastIndexOf('.');
    int colon = name.indexOf(':');
    if (dot!=-1) {                                 //  class.method:line or class.method
      return name.substring(0,dot);
    } else if (dot==-1 && colon!=-1) {             //  class:line
      return name.substring(0,colon);
    }  else {
      return "";
    }  
  }

  /**
   * Parse the method name from a string of the form class.method:line
   * @param name a string
   * @return the method name, or empty String if there is no method name
   * @see BootMap.findAddress()
   */
  public static String breakpointParseMethod(String name) {
    int dot = name.lastIndexOf('.');
    int colon = name.indexOf(':');
    if (dot!=-1 && colon!=-1) {                    //  class.method:line
      return name.substring(dot+1,colon);
    } else if (dot==-1 && colon!=-1) {             //  class:line
      return "";
    } else if (dot!=-1 && colon==-1) {             //  class.method
      return name.substring(dot+1);
    } else {
      return  name;
    }
  }

  /**
   * Parse the line number from a string of the form class.method:line
   * @param name a string
   * @return the line number, or -1 if there is no line number
   * @see BootMap.findAddress()
   */
  public static int breakpointParseLine(String name) throws BmapNotFoundException{
    int colon = name.indexOf(':');
    if (colon!=-1) {                    //  class.method:line
      try { 
	return Integer.parseInt(name.substring(colon+1)); 
      }
      catch (NumberFormatException e) { 
	if (colon==(name.length()-1))              // colon but no line number
	  return -1;
	else
	  throw new BmapNotFoundException("no such line number");
      }
    } else {
      return -1;
    }
  }

  /**
   * Parse the stack frame number from a string of the form frame:local.field...
   * @param expr the expression
   * @return the stack frame number, or -1 if there is none
   */
  public static int localParseFrame(String expr) {
    try {
      int colon = expr.indexOf(':');
      if (colon==-1) 
	return Integer.parseInt(expr);
      else 
	return Integer.parseInt(expr.substring(0,colon));
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Parse the local variable name from a string of the form frame:local.field...
   * @param expr the expression
   * @return the local variable name, or null if there is none
   */
  public static String localParseName(String expr) {
    int colon = expr.indexOf(':');
    if (colon==-1) {
      try {
        int i = Integer.parseInt(expr);
        return null;   // if it's a number, then no variable name was given 
      } catch (NumberFormatException e) {
        return expr;   /// it's not a number, so the expression is a variable name
      }
    } else {
      return expr.substring(colon+1);
    }
  }

  // to implement JDPCommandInterface

  /**
   * write the output to the screen if it is a
   * String, and fail otherwise
   * @param output the output Object
   */
  public void writeOutput(Object output)
  {
    if (output instanceof String)
    {
      System.out.println(output);
    }
    else
    {
      System.err.println("Command line interface only handles strings!");
      System.exit(1);
    }
  }

}

