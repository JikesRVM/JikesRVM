/* (C) Copyright IBM Corp. 2001, 2003

   $Id$

   Documentation for this program is in the variables short_help_msg and
   long_help_msg, immediately below.
*/

static const char short_help_msg[] = ""
"Usage: %s [--help]\n"
"	 [--trace]\n"
"	 [--[no-]undefined-constants-in-conditions\n"
"	 [--[no-]only-boolean-constants-in-conditions\n"
"        [ --disable-modification-exit-status ] \n"
"        [ -D<name>[ =1 | =0 | =<string-value> ] ]... \n"
"	 [ -- ] <output directory> [ <input file> ]...\n";

      
static const char long_help_msg[] = ""
"   Preprocess source files that are new or have changed.\n"
"\n"
"   The timestamp of each input file is compared with that\n"
"   of the corresponding file in the <output directory>.  If the\n"
"   output file doesn't exist, or is older than the input file,\n"
"   then the input file is copied to the <output directory>, \n"
"   with preprocessing.\n"
"\n"
"   Invocation parameters:\n"
"      - zero or more definitions of preprocessor constants\n"
"	 of the form \"-D<name>=1\",\n"
"        of the equivalent shorthand form \"-D<name>\", \n"
"	 of the form \"-D<name>=0\",\n"
"	 and/or of the form \"-D<name>=<string-value>\".\n"
"      - name of directory to receive output files\n"
"      - names of zero or more input files\n"
"      - other flags\n"
"\n"
"   Process exit status means:\n"
"           0 - no files changed\n"
"           1 - some files changed\n"
"       other - trouble\n"
"\n"
"   With --disable-modification-exit-status, the process will\n"
"   exit with status 0 even when some files changed.  Under\n"
"   --disable-modification-exit-status, non-zero exit status\n"
"   always means trouble.  This is handy inside Makefiles.\n"
"\n"
"\n"
"   --trace  The preprocessor prints a \n"
"         '.' for each file that did not need to be changed and a \n"
"	  '+' for each file that needed preprocessing."
"\n"
"   --verbose, -v  The preprocessor prints a message for each file\n"
"         examined, and prints a summary at the end \n"
"\n"
"   --help, -h  Show this long help message and exit with status 0.\n"
"\n"
"   -D<name>=0 is historically a no-op; equivalent to never defining <name>.\n"
"   The intent is that <name> be a constant usable \n"
"   in an //-#if <name> directive.\n"
"\n"
"   -D<name>=1 and -D<name> are equivalent.\n"
"\n"
"   -D<name>=<any-string-value-but-0-or-1> will define a constant that is\n"
"       usable in a //-#value directive.\n"
"\n"
"   The following preprocessor directives are recognized\n"
"   in source files.  They must be the first non-whitespace characters\n"
"   on a line of input.\n"
"\n"
"      //-#if    <name>\n"
"	    Historically, it is not an error for <name> have never been\n"
"	    defined; it's equivalent to -D<name>=0.  This is currently \n"
"	    experimentally promoted to an error with the flag\n"
"	    --noundefined-constants-in-conditions.  The historical\n"
"	    behavior is explicitly requested with "
"--undefined-constants-in-conditions\n"
"	    \n"
"	    Historically, //-#if only checks whether <name> is defined.\n"
"	    If you specify --only-boolean-constants-in-conditions, then\n"
"	    you get stricter behavior where <name> must've been defined with\n"
"	    -D<name>=1 or -D<name>=0.\n"
"\n"
"	    \"//-#if\" also supports the constructs '!' (invert the sense of \n"
"	    the next test), '&&', and '||'.  '!' binds more tightly \n"
"	    than '&&' and '||' do.   '&&' and '||' are at the same precedence,\n"
"	    and are short-circuit evaluated in left-to-right order.\n"
"\n"
"	    The preprocessor does not support parentheses in //-#if constructs.\n"
"	    If you don't mix '&&' and '||' in the same line, you'll be OK.\n"
"\n"
"      //-#elif  <name>\n"
"            Takes the same arguments that //-#if does. \n"
"\n"
"      //-#else  <optional-comment>\n"
"\n"
"      //-#endif <optional-comment>\n"
"\n"
"      //-#value <preprocessor-symbol>\n"
"	    <-preprocessor-symbol> is the name of a constant defined on the\n"
"	    command line with -D; it will be replaced with the defined value.\n"
"\n"
"	    It is an error for <preprocessor-symbol> not to be defined.\n"
"\n"
"	    It is an error for <preprocessor-symbol> to have been defined\n"
"	    with -D<name>=1 or with -D<name>\n"
"\n"
"	   (This is an odd restriction, but is the way the code was written\n"
"           when I found it.  You're free to rewrite it if you want it to act\n"
"           just like the C preprocessor does.)\n"
"\n"
"     There is no equivalent to the C preprocessor's \"#define\" construct;\n"
"     all constants are defined on the command line with \"-D\".\n";

static const char authors[] =
"   @author Derek Lieber\n"
"   @date 13 Oct 1999\n"
"   @modified Steven Augart\n"
"   @date June, 2003\n";

static const char license[] = 
"Copyright ©IBM Corp. 2001, 2003.\n"
"This software is redistributable under the terms of the Common Public License,\n"
"an OSI-certified open-source license.\n";


#define _GNU_SOURCE 1		// so we get vsnprintf() guaranteed on gnu libc.
//#define _XOPEN_SOURCE 500	// the most extended version.  GNU explicitly
// tests for this being equal to 500.  Fun, eh?
#include <stdio.h>
#include <errno.h>
#include <string.h>     /* strcmp */
#include <libgen.h>     /* basename */
#include <unistd.h>     /* unlink */
#include <stdlib.h>     /* exit */
#include <sys/stat.h>   /* stat */
#if (defined __linux__) 
#include <limits.h> /* xxx_MAX */
#else
#include <sys/limits.h> /* xxx_MAX */
#endif
#include <assert.h>		// assert()
#include <ctype.h>		// isspace()
#include <stdarg.h>		// va_list, for snprintf()
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>		// for sigaction() and signal().
#ifndef __cplusplus
#include <stdbool.h>
#endif

char *Me; // name to appear in error messages

//bool only_boolean_constants_in_conditions = true; /* strict behav. */
bool only_boolean_constants_in_conditions = false; /* historical behaviour */
bool undefined_constants_in_conditions = true; /* Historical behavior */
//bool undefined_constants_in_conditions = false; /* Strict behavior */
bool exit_with_status_1_if_files_modified = true; /* Historical behavior */




#ifndef DEBUG
#define DEBUG 0
#endif

#define SHOW_PROGRESS(STR) if (trace) { printf(STR); fflush(stdout); }

// Limits.
//
#define MAXLINE       4000 /* longest input line we can handle */
#define MAXNESTING    100  /* maximum #if nesting we can handle */
#define MAXCONSTANTS 100  /* maximum number of -D<name> constants we can handle */

// Preprocessor constants that have been set via "-D<name>".
//
struct def {
    const char *Name;
    const char *Value;		// this is the value for the "value"
				// construct.  "value" is only meaningful if
				// this is non-null..
    enum { UNINIT = 0, SET = 1, UNSET = -1} isset; // true or false; this is
    // only sensible for the
    // -D<Name>=0 and
    // -D<Name>=1.    Otherwise
    // it's UNINIT
} Constant[MAXCONSTANTS];

// This kludge gets around the fact that an enum tag is scoped in its defining
// context in C++, whereas in standard C, enum tags are global identifiers.
#ifdef __cplusplus
#define membof(o) o
#else
#define membof(o)
#endif

int   Constants;                    // number thereof

// Source file currently being processed.
//
char package[MAXLINE];       // package name if it exists
const char *SourceName;            // file name
int   SourceLine;            // current line number therein
int   Nesting;               // number of unclosed #if's
int   TrueSeen[MAXNESTING];  // has any block evaluated VV_TRUE at current nesting level?
int   Value[MAXNESTING];     // has current block at current nesting level evaluated VV_TRUE?
int   Unmatched[MAXNESTING]; // line number of currently active #if, #elif, or #else at each level
bool   PassLines;             // if true, pass lines through.   false --> don't

char *PutIntoPackage = NULL;	// points to memory that's part of argv.

// Forward references, and prototypes to shut up GCC's paranoid warning
// settings. 
int  preprocess(const char *srcFile, const char *destinationFile);
void reviseState(void);
#if DEBUG
void printState(FILE *fout, char *constant, char *line);
#endif
bool eval(char *p);
int  evalReplace(char *cursor);
char *getToken(char **c);
bool getBoolean(char **c);

// Types of tokens returned by scan().
//
enum scan_token {
    TT_TEXT         = 0,
    TT_IF           = 1,
    TT_ELIF         = 2,
    TT_ELSE         = 3,
    TT_ENDIF        = 4,
    TT_REPLACE      = 5,
    TT_UNRECOGNIZED = 6,
};

enum scan_token scan(const char *srcFile, char *line, int *valuep);

#define UNUSED_DECL_ARG __attribute__((__unused__))
#define UNUSED_DEF_ARG __attribute__((__unused__))
// The __signal__ attribute is only relevant on GCC on the AVR processor.
// We don't (yet) work on the AVR, so this code will probably never be
// executed.  
#ifdef __avr__
#define SIGNAL_ATTRIBUTE    __attribute__((__signal__))
#else
#define SIGNAL_ATTRIBUTE
#endif


// Values of tokens returned by scan() (in *valuep) for IF and ELIF.
// For some cases, an index into ConstantValue is returned.
#define VV_FALSE 0
#define VV_TRUE  1


/* snprintf(), but with our own built-in error checks. */
void xsnprintf(char *buf, size_t bufsize, const char *format, ...)
    __attribute__((__format__(__printf__, 3, 4)));
void xsystem(const char *command);
void inputErr(const char msg[], ...) 
    __attribute__((noreturn))
    __attribute__((__format__(__printf__, 1, 2)));

static void set_up_trouble_handlers();
bool  strneql(const char *s, const char *t, size_t n) ;
bool  streql(const char *s, const char *t);
static void shorthelp(FILE *out);

/** Delete this file on trouble.  This is the interface to
 * delete_on_trouble().  */
const char *DeleteOnTrouble = NULL;


int
main(int argc, char **argv) 
{
    Me = basename(*argv++); --argc;

    set_up_trouble_handlers();

    // gather arguments
    //
    bool trace = false;
    bool verbose = false;
    for (; **argv == '-'; ++argv, --argc) {
	char *arg   = *argv;
	
	if (streql(arg, "--")) {
	    ++argv;
	    break;
	}
	
	if (strneql(arg, "--", 2)) {
	    ++arg;       /* treat double-dash and single-dash the same way. */
	}
	  
	if (streql(arg, "-help") || streql(arg, "-h")) {
	    printf(short_help_msg, Me);
	    fputs(long_help_msg, stdout);
	    exit(0);
	}
      
	if (streql(arg, "-disable-modification-exit-status")) {
	    exit_with_status_1_if_files_modified = false;
	    continue;
	}
      
	if (streql(arg, "-trace")) {
	    trace = true;
	    continue;
	}
	
	if (streql(arg, "-verbose") || streql(arg, "-v")) {
	    verbose = true;
	    continue;
	}

	if (streql(arg, "-undefined-constants-in-conditions")) {
	    undefined_constants_in_conditions = true;
	    continue;
	}
	  
	if (streql(arg, "-no-undefined-constants-in-conditions")) {
	    undefined_constants_in_conditions = false;
	    continue;
	}
	  
	if (streql(arg, "-only-boolean-constants-in-conditions")) {
	    only_boolean_constants_in_conditions = true;
	    continue;
	}
	  
	if (streql(arg, "-no-only-boolean-constants-in-conditions")) {
	    only_boolean_constants_in_conditions = false;
	    continue;
	}
	  
	if (strneql(arg, "-D", 2)) {
	    struct def *dp = Constant + Constants;
	    arg += 2;
	    
	    dp->Name = arg;
	    for ( ; *arg && *arg != '='; ++arg)
		;
	    char *val;
	    if (*arg == '=') {
		*arg = '\0';	// null-terminate the name.
		val = arg + 1;
	    } else {
		assert(*arg == '\0');
		val = NULL;	// Special sentinel value.
	    }
	    
	    if (++Constants >= MAXCONSTANTS) {
		fprintf(stderr, "\
%s: Too many (%d) -D constants; recompile with a larger\n" 
			"value of MAXCONSTANTS.\n",
			Me, Constants);
		exit(3);
	    }

	    // We used to ignore "-D<name>=0"; now we explicitly define it as
	    // UNSET. 
	    // accept "-D<name>" as "-D<name>=1" == SET
	    // accept "-D<name>=<str>" for any other string

	    if (*(dp->Name) == '\0') {
		fprintf(stderr, "%s: The -D<name>[=<value>] flag needs\n"
			"at least a <name>!  None found in definition # %d.\n",
			Me , Constants);
		shorthelp(stderr);
		exit(2);
	    }

	    // -D<name> with no =<value> is a special case.  
	    // Treat it as if -D<name>=1.
	    // -D<name>=1 is also a special case.
	    if (!val || streql(val, "1")) {
		assert(dp->Value == NULL);
		dp->isset = membof(dp->) SET;
		continue;
	    } 

	    // -D<name>=0.  Special case.   Used to be equivalent to never
	    // setting. 
	    if (streql(val, "0")) {
		// should always be initialized to NULL, since Constants is
		// global (BSS) space.
		assert(dp->Value == NULL);
		dp->isset = membof(dp->) UNSET;
		continue;		// ignore it (!)
	    }
	   

	    // Must be -D<name>=<value>
	    dp->Value = val;
	    // should always be initialized to UNINIT, since Constants is
	    // global (BSS) space.
	    assert(dp->isset == membof(dp->) UNINIT);
	    continue;
	}

	/* We'll consider multiple -package declarations benign, and let
	 * the last one win.. */
	if (streql(arg, "-package")) {
	    if (! *++argv ) {
		fprintf(stderr, "%s: The -package flag requires an argument\n", Me);
		shorthelp(stderr);
		exit(2);
	    }
	    PutIntoPackage = *argv;
	}

	if (strneql(arg, "-package=", 9)) {
	    PutIntoPackage = arg + 9;
	    continue;
	}

	fprintf(stderr, "%s: unrecognized option: %s\n", Me, arg);
	shorthelp(stderr);
	exit(2);
    }
    
    char *outputDirectory = *argv++; --argc;

    // check timestamps and preprocess new/changed files
    //
    int examined     = 0;
    int preprocessed = 0;
    assert(argc >= 0);

    if (argc == 0 && trace) {
	fprintf(stderr, "%s: I won't preprocess any files, since you didn't\n"
		"    specify any on the command line.", Me);
    }
    

    while (argc != 0) {
	char *source = *argv++; --argc;
	char  destination[PATH_MAX + 1];
	xsnprintf(destination, sizeof destination, "%s/%s", outputDirectory, basename(source));

	struct stat info;
	time_t      sourceTime;
	time_t      destinationTime;
	
	if (stat(source, &info) < 0) {
	    fprintf(stderr, "%s: a source file (%s) doesn't exist.  Aborting.\n",
		    Me, source);
	    exit(2);
	}
	sourceTime = info.st_mtime;

	if (stat(destination, &info) < 0) {
	    destinationTime = 0;
	} else {
	    destinationTime = info.st_mtime;
	}
      
	if (sourceTime > destinationTime) {
	    if (verbose) {
		if (destinationTime == 0) {
		    fprintf(stdout, "%s: \"%s\" has never been processed: processing\n", Me, basename(source));
		} else {
		    fprintf(stdout, "%s: \"%s\" changed since last time: reprocessing\n", Me, basename(source));
		}
	    }
	  
	    // file is new or has changed
         
	    // // make (previously preprocessed) output file writable
	    // if (destinationTime != 0)
	    //    chmod(destination, S_IREAD | S_IWRITE);
	  
	    if (preprocess(source, DeleteOnTrouble = destination) < 0) {
		exit(2);             // treat as fatal error
	    }
	    DeleteOnTrouble = NULL;
	  

	    // Move file to the right subdirectory if it is part of a package
	    //
	    if (*package != 0) {
		// Should do error-checking of package
		char command[PATH_MAX + 100];
		char finalDir[PATH_MAX + 1];
		char finalDstFile[PATH_MAX + 1];
		// Convert "." in package to "/"
		char *cur = package;
		while ((cur = strchr(cur, '.')) != NULL) {
		    *cur = '/';
		}
		xsnprintf(finalDir, sizeof finalDir, "%s/%s", outputDirectory, package);
		xsnprintf(finalDstFile, sizeof finalDstFile, "%s/%s", finalDir, basename(source));
		xsnprintf(command, sizeof command, "mkdir -p %s", finalDir);
		//fprintf(stderr, "%s\n", command);
		xsystem(command);
		xsnprintf(command, sizeof command, "mv -f %s %s", destination, finalDstFile);
		//fprintf(stderr, "%s\n", command);
		xsystem(command);
	    }

	    // // make output file non-writable to discourage editing that will get clobbered by future preprocessing runs
	    // chmod(destination, S_IREAD);
         
	    ++preprocessed;
	    SHOW_PROGRESS("+");
	}
	else
	    SHOW_PROGRESS(".");
	++examined;
    }
   
    if (verbose)
	fprintf(stdout, "\n%s: %d of %d files required preprocessing\n", Me, preprocessed, examined);
    // SHOW_PROGRESS("\n");
    if (exit_with_status_1_if_files_modified && preprocessed)
	exit(1);
    exit(0);
}


// Preprocess a file.
// Takes:    name of file to be read
//           name of file to be written
// Returns: 0 --> success
//          -1 --> trouble (which leads to immediate aborting by the caller)
int
preprocess(const char *srcFile, const char *dstFile)
{
    const int Trouble = -1;
    const int OK = 0;
    
    *package = 0;
    FILE *fin = fopen(srcFile, "r");
    if (!fin) {
	fprintf(stderr, "%s: can't find `%s'\n", Me, srcFile);
	return Trouble;
    }

    FILE *fout = fopen(dstFile, "w");
    if (!fout) {
	fprintf(stderr, "%s: can't create `%s':", Me, dstFile);
	perror("");
	return Trouble;
    }

#if DEBUG
    for (int i = 0; i < Constants; ++i) {
	struct def *dp = Constants + i;
	fprintf(fout, "[%s=%s]\n", dp->Name, 
		Constant[i].Value ? : 
		( dp->isset == membof(dp->) SET ? "1 (*SET*)" : "0 (*UNSET*)"));
    }
	
#endif

    SourceName = srcFile;
    SourceLine = 0;
    Nesting = 0;
    reviseState();

    if (PutIntoPackage != NULL)
	fprintf(fout, "\npackage %s;\n\n", PutIntoPackage);

    for (;;) {
	char line[MAXLINE];
	size_t linelen;

	if (!fgets(line, sizeof line, fin)) { 
	    if (feof(fin)) {
		if (fclose(fin)) {
		    fputs("Trouble while closing the input file ", stderr);
		    perror(SourceName);
		    return Trouble; // take advantage of the fact that the
				    // caller will abort.
		}; 

		if (ferror(fout)) {
		    fputs("Trouble while writing the file ", stderr);
		    perror(dstFile);
		    return Trouble;
		}

		if (fclose(fout)) {
		    fputs("Trouble while closing the file ", stderr);
		    perror(dstFile);
		    return Trouble;
		}

		if (Nesting) {
		    fprintf(stderr, "%s: %s:%d: No matching #endif for this line\n", Me, SourceName, Unmatched[Nesting]);
		    return Trouble;
		}
		return OK;
	    } else if (ferror(fin)) {
		fprintf(stderr, "%s: Trouble while reading the file \"%s\": ",
			Me, SourceName);
		perror("");
		// take advantage of the fact that the caller will abort.; no
		// need to close files.
		return Trouble;
	    } else {
		fprintf(stderr, "%s: Internal error: fgets() returned NULL, but"
			" neither feof() nor ferror() are true!\n"
			"%s: Aborting execution.\n", Me, Me);
		exit(13);
	    }
	}
	linelen = strlen(line);
	assert(linelen > 0);	// otherwise we'd have gotten feof().
	if (line[ linelen - 1 ] != '\n') {
	    inputErr("Line too long (over %lu characters).\n", 
		     (unsigned long) linelen);
	}

	++SourceLine;
	

	int value;
	enum scan_token token_type;
	
	switch (token_type = scan(srcFile, line, &value)) {
	case TT_TEXT:
#if DEBUG
	    printState(fout, "TEXT ", line);
#endif
	    fputs(PassLines ? line : "\n", fout);
	    continue;

	case TT_REPLACE:
#if DEBUG
	    printState(fout, "REPLACE ", line);
#endif
	    fputs(PassLines ? Constant[value].Value : "\n", fout);
	    continue;

	case TT_IF:
	    TrueSeen[Nesting] = Value[Nesting] = value;
	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "IF   ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ELIF:
	    if (Nesting == 0) {
		inputErr("#elif with no corresponding #if");
	    }
	    --Nesting;
	    
	    if (TrueSeen[Nesting]) Value[Nesting] = VV_FALSE;
	    else                   TrueSeen[Nesting] = Value[Nesting] = value;
	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    
	    reviseState();
#if DEBUG
	    printState(fout, "ELIF ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ELSE:
	    if (Nesting == 0) {
		inputErr("#else with no corresponding #if");
	    }
	    --Nesting;
	    if (TrueSeen[Nesting]) 
		Value[Nesting] = VV_FALSE;
	    else
		TrueSeen[Nesting] = Value[Nesting] = VV_TRUE;

	    Unmatched[Nesting] = SourceLine;
	    ++Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "ELSE ", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_ENDIF:
	    if (Nesting == 0) {
		inputErr("#endif with no corresponding #if");
	    }
	    --Nesting;
	    reviseState();
#if DEBUG
	    printState(fout, "ENDIF", line);
#endif
	    fputs("\n", fout);
	    continue;

	case TT_UNRECOGNIZED:
	    inputErr("unrecognized preprocessor constant: %s", line);
	    return Trouble; 

	default:
	    fprintf(stderr, "%s: %s:%d: Internal error: scan() should never return token type %d\n", Me, SourceName, SourceLine, token_type);
	    return Trouble;

	}
    }
    /* NOTREACHED */
}

// Compute new preprocessor state after scanning a constant.
// Taken:    Value[]
//           Nesting
// Returned: PassLines
//
void reviseState(void) 
{

    PassLines = true;
    for (int i = 0; i < Nesting; ++i)
	PassLines = PassLines && Value[i];
}

#if DEBUG
// Print preprocessor state (for debugging).
// Taken:    output file
//           current constant
//           current input line
// Returned: nothing
//
void
printState(FILE *fout, char *constant, char *line)
{
    fprintf(fout, "[stack=");

    int i;
    for (i = 0; i < Nesting; ++i) 
	fprintf(fout, "%d ", Value[i]);
   
    for (; i < 5; ++i)
	fprintf(fout, "..");
   
    fprintf(fout, "%s] %s %s", PassLines ? "pass" : "hide", constant, line);
}
#endif

// Scan for a preprocessor directive.  Also handles "package" declarations.
//
// Taken:    line to be scanned
//           place to put value of #if or #elif directive, if found
// Returned: TT_TEXT         --> found no                directive
//           TT_IF           --> found '//-#if <name>'   directive
//           TT_ELIF         --> found `//-#elif <name>' directive
//           TT_ELSE         --> found `//-#else'        directive
//           TT_ENDIF        --> found `//-#endif'       directive
//           TT_REPLACE      --> found `//-#value <name>' directive
//           TT_UNRECOGNIZED --> found unrecognized      directive
// In some cases, value is modified.

enum scan_token
scan(const char *srcFile, char *line, int *valuep) 
{
    // skip whitespace
    //
    char *p;
    for (p = line; *p && isspace(*p); ++p)
	;

    // look for "package [c.]*;"
    //
    if (strneql(p, "package ", 8)) {
	if (PutIntoPackage)
	    fprintf(stderr, "WARNING: package declaration co-existing with specified package via -package");
	if (*package != 0)
	    fprintf(stderr, "WARNING: multiple package declaration in file %s", srcFile);
	p += 8;
	char *tmp = package;
	while (*p != ';') {
	    if (*p == '\n') fprintf(stderr, "%s: Ill-formed package declaration: %s", srcFile, line);
	    *(tmp++) = *(p++);
	}
	*tmp = 0;
	return TT_TEXT;
    }

    // look for "//-#"
    //
    if (*p++ != '/') return TT_TEXT;
    if (*p++ != '/') return TT_TEXT;
    if (*p++ != '-') return TT_TEXT;
    if (*p++ != '#') return TT_TEXT;

    // look for "value "
    //
    if (strneql(p, "value", 5) && isspace(p[5])) {
	p +=6;
	while (isspace(*p))
	    ++p;
       
	*valuep = evalReplace(p);
	return TT_REPLACE;
    }

    // look for "if "
    //
    if (strneql(p, "if", 2) && isspace(p[2])) {
	p +=3;
	while (isspace(*p))
	    ++p;
	*valuep = eval(p);
	return TT_IF;
    }
     
    // look for "elif"
    //
    if (strneql(p, "elif", 4) && isspace(p[4])) {
	p += 5;
	while (isspace(*p))
	    ++p;

	*valuep = eval(p);
	return TT_ELIF;
    }
     
    // look for "else"
    //
    if (strneql(p, "else", 4)) {
	return TT_ELSE;
    }

    // look for "endif"
    //
    if (strneql(p, "endif", 5)) {
	return TT_ENDIF;
    }
     
    return TT_UNRECOGNIZED;
}

// Evaluate <name> appearing in an `if' or `elif' directive.
// Taken:    `//-#if <condition>'
//                  ^cursor
//    or:    `//-#elif <condition>'
//                    ^cursor
//		where condition is name <&& name> <|| name>
//			(!name toggles the sense)
//  Returns the name of the token, but NOT null terminated
//    Upon return, *c points to the cursor, the character immediately after
//       the end of the name 
//    It will return the leading ! if one exists.
//  
//    The only characters forbidden to a token are '&', '|', and whitespace.
char *
getToken(char **cursorp)
{
    char *start, *cursor;
    cursor = *cursorp;
    while (isspace(*cursor))
	++cursor;
    start = cursor;
    while (*cursor && !isspace(*cursor) && *cursor != '&' && *cursor != '|')
	++cursor;
    *cursorp = cursor;
    return start;
}


// Returned:  true  --> on top of a boolean operator ("||"
//		    or "&&")
//            false --> not on top of one.
// Trims leading whitespace, leaves the cursor before the bool. operator.

bool getBoolean(char **cursorp)
{
    while (isspace(**cursorp))
	++*cursorp;

    return ((*cursorp)[0] == '|' && (*cursorp)[1] == '|') 
	|| ((*cursorp)[0] == '&' && (*cursorp)[1] == '&');
}


/* Returns an index into Constant[i], which our caller derefs. for
 * Constant[i].Value. */ 
int 
evalReplace(char *cursor) 
{
    char *name = getToken( &cursor );
    assert( ( cursor == name ) ? ( *name == '\0' ) : *name);
    size_t len = cursor - name;
    if (len == 0)
	inputErr("The //-#value <name> preprocessor construct needs a <name>");
	
    for (int i = 0; i < Constants; ++i) {
	const char *constantName = Constant[i].Name;
	if (strlen(constantName) == len && strneql(name, constantName, len))
	{
	    if (Constant[i].Value == NULL)
		inputErr(
		    "//-#value used on non-value (true/false) constant '%s'", 
		    constantName);
	    return i;
	}
    }
    inputErr("//-#value used on undefined constant '%*.*s'", (int) len, (int) len, name);
    /* NOTREACHED */
}


bool
eval(char *cursor) 
{
    int match;			// -1 if not found, 1 if defined true (1), 
				// 0 if defined 0 (false).
    for (;;) {
	match = -1;
	char *name = getToken( &cursor );
	int toggle = 0;
	if ( name[0] == '!' ) {
	    toggle = 1;
	    name++;
	}
	assert(cursor >= name);
	size_t len = cursor - name;
	if (len == 0)
	    inputErr("missing <name> in preprocessor condition");
	for (int i = 0; i < Constants; ++i)
	{
	    struct def *dp = Constant + i;
	    if (strlen(dp->Name) == len && memcmp(dp->Name, name, len) == 0) 
	    {
		/* Matched the token.  Now evaluate. */
		if (dp->Value) {
		    if (only_boolean_constants_in_conditions)
			inputErr(
			    "The constant name %*.*s is a Value constant;\n"
			    "     preprocessor conditions require a"
			    " True/False constant.", (int) len, (int) len, dp->Name);
		    match = 1;
		} else {
		    assert(!dp->Value);
		    assert(dp->isset != membof(dp->) UNINIT);

		    match = (dp->isset == membof(dp->) SET);
		}
		break;
	    }
	}
	if (match < 0) {
	    if (! undefined_constants_in_conditions)
		inputErr("Undefined constant name %*.*s"
			 " in preprocessor condition", (int) len, (int) len, name);
	    match = 0;
	}
	
	if ( toggle ) 
	    match = !match;
	if ( !getBoolean( &cursor ) ) 
	    break;
	if ( cursor[0] == '|' ) {
	    if ( match ) 	// skip further syntax checking; whoops!
		return true;
	} else {
	    assert(*cursor == '&');
	    if ( !match ) 	// skip further checking.
		return false;
	}
	cursor += 2;		// skip && or ||
    }
    while (*cursor && isspace(*cursor))
	++cursor;
    if (*cursor) {
	inputErr("Garbage characters (\"%s\") are at the"
		 "end of a preprocessor condition.", cursor);
	exit(2);
    }
    return match;
}




/* snprintf(), but with our own built-in error checks. */
void
xsnprintf(char *buf, size_t bufsize, const char *format, ...)
{
    va_list ap;
    va_start(ap, format);
    int n = vsnprintf(buf, bufsize, format, ap);
    va_end(ap);
    // Handle both the old and new GNU C library return values.
    if (n < 0 || n + 1 > (int) bufsize) {
	fprintf(stderr, "%s: xsnprintf(): Ran out of space in a"
		" fixed-size buffer while formatting a string"
		" starting with: \"%s\"\n", Me, buf);
	exit(4);
    }
    /* All is well. */
}


void
xsystem(const char *command)
{
    int ret;
    ret = system(command);

    if (ret == 0)
	return;

    if (ret < 0) {
	fputs(Me, stderr);
	perror(": Trouble while trying to use system()");
	exit(5);
    }

    fprintf(stderr, "%s: Trouble while running system(\"%s\").\n", Me, command);
    if (WIFEXITED(ret)) {
	if (WEXITSTATUS(ret) == 127) {
	    fputs("     The shell probably) could not be executed.\n", stderr);
	} else {
	    fprintf(stderr, "     The command exited with status %d\n", 
		    WEXITSTATUS(ret));
	}
    } else if (WIFSIGNALED(ret)) {
	fprintf(stderr, "     The command died because it got hit with signal #%d\n", WTERMSIG(ret));
    } else {
	fprintf(stderr, "    !!! The system() function failed for some reason that we do not understand; return status was %d.  You should never see this message.  Aborting execution.\n", ret);
	exit(13);
    }
    fputs("     Aborting execution.\n", stderr);
    exit(5);
}

    
void
shorthelp(FILE *out)
{
    fprintf(out, short_help_msg, Me);
    fprintf(out, "%s:   Use --help for more information.\n", Me);
}

      
static void delete_on_trouble(UNUSED_DECL_ARG int dummy_status , UNUSED_DECL_ARG void *dummy_arg ) 
    SIGNAL_ATTRIBUTE;


static void 
delete_on_trouble(UNUSED_DEF_ARG int dummy_status, UNUSED_DEF_ARG void *dummy_arg)
{
    if (DeleteOnTrouble) {
	// We're already in trouble, so might as well delete, no?
	int err = unlink(DeleteOnTrouble);
	if (err && errno != ENOENT) {
	    fprintf(stderr, "%s: More Trouble -- unable to clean up by deleting ", Me);
	    perror(DeleteOnTrouble);
	}
	DeleteOnTrouble = NULL;
    }
}

/* Signal handler for fatal signals. */
static void 
cleanup_and_die(int signum) 
{
    fprintf(stderr, "%s: Dying due to signal # %d; cleaning up.\n", Me, signum);
    delete_on_trouble(0, (void *) NULL);
    signal(signum, SIG_DFL);
    raise(signum);
}


/* These are like strcmp() and strncmp(), but with a more intuitive
   interface (at least, more intuitive for those of us who did not grow
   up using the FORTRAN Arithmetic IF!) */
bool 
strneql(const char *s, const char *t, size_t n) 
{
    return strncmp(s, t, n) == 0;
}


bool 
streql(const char *s, const char *t) 
{
    return strcmp(s, t) == 0;
}

	    
void 
inputErr(const char msg[], ...)
{
    va_list ap;
    va_start(ap, msg);
    fprintf(stderr, "%s: %s:%d: ", Me, SourceName, SourceLine);
    vfprintf(stderr, msg, ap);
    va_end(ap);			/* silly to clean up; we're going to die
				 * anyway.  */
    fprintf(stderr, "\n%s: Aborting Execution.\n", Me);
    exit(2);
}


#if 0				/* This appears to work fine, but some say
				   that sigaction() is more predictable. */
static void
xsignal_oldsignal(int signum, void (*handler)(int))
{
   void (*ret)(int) = signal(signum, handler);
    if (ret == SIG_ERR) {
	fprintf(stderr, 
		"%s: Trouble trying to set up a handler for signal %d: ",
		Me, signum);
	perror(NULL);
	fprintf(stderr, "%s: ...going on as best we can\n");
    } else if (ret == SIG_IGN) {
	/* Some shells may turn off handling of certain signals.  We shan't
	   interfere. */
	// ignore return value; nothing much to do anyway.
	signal(signum, SIG_IGN);
    }
}
#endif





static void
xsignal_sigaction(int signum, void (*handler)(int))
{
    struct sigaction act;
    struct sigaction oldact;
    
    memset(&act, '\0', sizeof act);
    act.sa_handler = handler;
    act.sa_flags |= SA_RESTART;
    int r = sigaction(signum, &act, &oldact);
    if (r) {
 	fprintf(stderr, 
 		"%s: Trouble trying to set up a handler for signal %d: ",
 		Me, signum);
	perror((char *) NULL);
	fprintf(stderr, "%s: ...going on as best we can\n", Me);
    } else if ( ! (oldact.sa_flags | SA_SIGINFO) && (oldact.sa_handler == SIG_IGN)) {
	/* Don't interfere with shells that turn off the handling of certain
	   signals; reset it, instead. */
	r = sigaction(signum, &oldact, (struct sigaction *) NULL);
	if (r) {
	    fprintf(stderr, "%s: Trouble resetting signal %d's handler back to SIG_IGN: ", Me, signum);
	    perror((char *) NULL);
	    fprintf(stderr, "%s:  ...going on as best we can.", Me);
	}
    }
    /* Ok; all is done!   We're either set up or we aren't. */
}


static void (*xsignal)(int signum, void (*handler)(int)) = xsignal_sigaction;

static void 
set_up_trouble_handlers(void)
{
    if (on_exit(&delete_on_trouble, (void *) NULL)) {
	fprintf(stderr,"%s: ", Me);
	perror("Unable to register delete_on_trouble() via on_exit.  This should never happen");
	exit(2);
    }

    xsignal(SIGINT, cleanup_and_die);
    xsignal(SIGHUP, cleanup_and_die);
    xsignal(SIGTERM, cleanup_and_die);
    xsignal(SIGQUIT, cleanup_and_die);
    xsignal(SIGPIPE, cleanup_and_die);
    xsignal(SIGXFSZ, cleanup_and_die);
    xsignal(SIGXCPU, cleanup_and_die);
    xsignal(SIGFPE, cleanup_and_die);
    xsignal(SIGILL, cleanup_and_die);
    xsignal(SIGSEGV, cleanup_and_die);
    xsignal(SIGBUS, cleanup_and_die);
    xsignal(SIGABRT, cleanup_and_die);
#ifdef SIGEMT
    xsignal(SIGEMT, cleanup_and_die);
#endif
    xsignal(SIGSYS, cleanup_and_die);
    
}

