/* -*-coding: iso-8859-1 -*-
 *
 * (C) Copyright © IBM Corp 2001, 2003, 2004

 * $Id$

 * Documentation for this program is in the variables short_help_msg and
 * long_help_msg, immediately below.

 * Jikes RVM is distribed under the Common Public License (CPL),
 * which has been approved by the Open Source Initiative
 * as a fully certified open source license.

 * Jikes is a trademark of IBM Corp.
*/

static const char short_help_msg[] = ""
"Usage: %s [--help] [--trace]\n"
"        [ --[no-]undefined-constants-in-conditions ]\n"
"        [ --[no-]only-boolean-constants-in-conditions ]\n"
"        [ --disable-modification-exit-status ]\n"
"        [ -D<name>[ =1 | =0 | =<string-value> ] ]...\n"
"        [ -- ] <output directory> [ <input file> ]...\n";

      
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
"        of the form \"-D<name>=1\",\n"
"        of the equivalent shorthand form \"-D<name>\", \n"
"        of the form \"-D<name>=0\",\n"
"        and/or of the form \"-D<name>=<string-value>\".\n"
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
"         '+' for each file that needed preprocessing."
"\n"
"   --verbose, -v  The preprocessor prints a message for each file\n"
"         examined, and prints a summary at the end \n"
"\n"
"   --help, -h  Show this long help message and exit with status 0.\n"
"\n"
"   --keep-going, -k  Keep going in spite of errors; still exit with bad status.\n"
"\n"
"   -D<name>=0 is historically a no-op; equivalent to never defining <name>.\n"
"   (However, the --no-undefined-constants-in-conditions flag changes that\n"
"    behavior, requiring that any <name> in an //-#if <name> directive.\n"
"    be defined with -D<name>=0 or -D<name>=1.)\n"
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
"           Historically, it is not an error for <name> to have never been\n"
"           defined; it's equivalent to -D<name>=0.  This can be \n"
"           experimentally promoted to an error with the flag\n"
"           --no-undefined-constants-in-conditions.  The historical\n"
"           behavior (default) is explicitly requested with\n"
"           --undefined-constants-in-conditions\n"
"           \n"
"           Historically, //-#if only checks whether <name> is defined.\n"
"           If you specify --only-boolean-constants-in-conditions, then\n"
"           you get stricter behavior where <name> must've been defined with\n"
"           -D<name>=1 or -D<name>=0.\n"
"\n"
"           \"//-#if\" also supports the constructs '!' (invert the sense of \n"
"           the next test), '&&', and '||'.  '!' binds more tightly \n"
"           than '&&' and '||' do.   '&&' and '||' are at the same precedence,\n"
"           and are short-circuit evaluated in left-to-right order.\n"
"\n"
"           The preprocessor does not support parentheses in //-#if constructs.\n"
"           If you don't mix '&&' and '||' in the same line, you'll be OK.\n"
"\n"
"      //-#elif  <name>\n"
"            Takes the same arguments that //-#if does. \n"
"\n"
"      //-#else  <optional-comment>\n"
"\n"
"      //-#endif <optional-comment>\n"
"\n"
"      //-#value <preprocessor-symbol>\n"
"           <-preprocessor-symbol> is the name of a constant defined on the\n"
"           command line with -D; it will be replaced with the defined value.\n"
"\n"
"           It is an error for <preprocessor-symbol> not to be defined.\n"
"\n"
"           It is an error for <preprocessor-symbol> to have been defined\n"
"           with -D<name>=1 or with -D<name>\n"
"\n"
"          (This is an odd restriction, but is the way the code was written\n"
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


#define _GNU_SOURCE 1           // so we get vsnprintf() guaranteed on gnu libc.
//#define _XOPEN_SOURCE 500     // the most extended version.  GNU explicitly
// tests for this being equal to 500.  Fun, eh?
#include <stdio.h>
#include <errno.h>
#include <string.h>     /* strcmp */
#include <libgen.h>     /* basename */
#include <unistd.h>     /* unlink */
#include <stdlib.h>     /* exit */
#include <sys/stat.h>   /* stat */
#if (defined __linux__)
#include <linux/limits.h> /* xxx_MAX */
#elif (defined __MACH__)
#include <limits.h> /* xxx_MAX */
#else
#include <sys/limits.h> /* xxx_MAX */
#endif
#include <assert.h>             // assert()
#include <ctype.h>              // isspace()
#include <stdarg.h>             // va_list, for snprintf()
#include <sys/types.h>
#include <sys/wait.h>
#include <signal.h>             // for sigaction() and signal().
#ifndef __cplusplus
#include <stdbool.h>
#endif
#include "cAttributePortability.h"

char *Me; // name to appear in error messages

//bool only_boolean_constants_in_conditions = true; /* strict behav. */
bool only_boolean_constants_in_conditions = false; /* historical behaviour */
bool undefined_constants_in_conditions = true; /* Historical behavior */
//bool undefined_constants_in_conditions = false; /* Strict behavior */
bool exit_with_status_1_if_files_modified = true; /* Historical behavior */
bool keep_going = false;



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
    const char *Value;          // this is the value for the "value"
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
int   SourceLineNo;            // current line number therein

int   Nesting;               // number of unclosed #if's
/* Structure for unmatched conditionals.  This is a simple stack. */
/* At nesting level 1, the item is stored in array element 0, and so on.
 * Ugly, no? */
struct nest_st {
    int lineNo; // line number of currently active #if, #elif, or #else at each level
    char lineTxt[MAXLINE];              // A copy of the current line, but
                                        // with the line ending chopped off
                                        // (for error messages)
    bool trueSeen; // has any block evaluated true at the current nesting level?
    bool val;                   // has the current block at current nesting level evaluated true
} unmatched[MAXNESTING];


bool   PassLines;             // if true, pass lines through.   false --> don't

char *PutIntoPackage = NULL;    // points to memory that's part of argv.

// Forward references, and prototypes to shut up GCC's paranoid warning
// settings. 
int  preprocess(const char *srcFile, const char *destinationFile);
void reviseState(void);
#if DEBUG
void printState(FILE *fout, char *constant, char *line);
#endif
bool eval(char *p, int &trouble);
const char *evalReplace(char *cursor, int &trouble);
char *getToken(char **c, int &trouble);
bool getBoolean(char **c);
static char *safe_fgets(char *buf, size_t buflen, FILE *fin);

// Types of tokens returned by scan().
//
enum scan_token {
    TT_TEXT         = 0,        // nothing to replace.
    TT_IF           = 1,        // arg.If
    TT_ELIF         = 2,        // arg.If
    TT_ELSE         = 3,        // no arg
    TT_ENDIF        = 4,        // no arg
    TT_REPLACE      = 5,        // arg.Replace
    TT_UNRECOGNIZED = 6,
};

union scan_arg {
    bool If;
    const char *Replace;
};

enum scan_token scan(const char *srcFile, char *line, union scan_arg *argp,
                     int &trouble);


/* snprintf(), but with our own built-in error checks. */
void xsnprintf(char *buf, size_t bufsize, const char *format, ...)
    __attribute__((__format__(__printf__, 3, 4)));
void xsystem(const char *command);
void inputErr(const char msg[], ...) 
//    __attribute__((noreturn))
    __attribute__((__format__(__printf__, 1, 2)));

static void set_up_trouble_handlers();
bool  strneql(const char *s, const char *t, size_t n) ;
bool  streql(const char *s, const char *t);
static void shorthelp(FILE *out);

/** Delete this file on trouble.  This is the interface to
 * delete_on_trouble().  */
const char *DeleteOnTrouble = NULL;
static void delete_on_trouble(void) SIGNAL_ATTRIBUTE;

enum Trouble { TROUBLE = -1,  OK = 0 };


int
main(int argc, char **argv) 
{
    Me = basename(*argv++); --argc;

    set_up_trouble_handlers();

    // gather arguments
    //
    bool trace = false;
    bool verbose = false;
    for (; argc > 0 && **argv == '-'; ++argv, --argc) {
        char *arg = *argv;
        
        if (streql(arg, "--")) {
            ++argv, --argc;
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
      
        if (streql(arg, "-keep-going") || streql(arg, "-k")) {
            keep_going = true;
            continue;
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
                *arg = '\0';    // null-terminate the name.
                val = arg + 1;
            } else {
                assert(*arg == '\0');
                val = NULL;     // Special sentinel value.
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
                continue;               // ignore it (!)
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
            --argc;
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
    
    if (argc <= 0) {
        fprintf(stderr, "%s: Must specify an output directory!\n", Me);
        shorthelp(stderr);
        exit(2);
    }
    
    char *outputDirectory = *argv++; --argc;

    // check timestamps and preprocess new/changed files
    //
    int examined     = 0;
    int preprocessed = 0;
    int failed = 0;

    if (argc == 0 && (trace || verbose)) {
        fprintf(stderr, "%s: I won't preprocess any files, since you didn't\n"
                "    specify any on the command line.", Me);
    }
    

    while (argc > 0) {
        char *source = *argv++; --argc;
        char  destination[PATH_MAX + 1];
        xsnprintf(destination, sizeof destination, "%s/%s", outputDirectory, basename(source));

        struct stat info;
        time_t      sourceTime;
        time_t      destinationTime;
        
        if (stat(source, &info) < 0) {
            fprintf(stderr, "%s: Trouble looking at the file \"%s\": %s\n",
                    Me, source, strerror(errno));
            fprintf(stderr, __FILE__ ":%d: stat() failed\n", __LINE__);
            
            if (keep_going) {
                ++failed;
                continue;
            }
            fprintf(stderr, "%s: Aborting Execution.\n", Me);
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
                if (keep_going) {
                    ++failed;
                    delete_on_trouble();
                    continue;
                }
                fprintf(stderr, "%s: Aborting Execution.\n", Me);
                exit(2);             // treat as fatal error
            }
            DeleteOnTrouble = NULL;
          

            // Move file to the right subdirectory if it is part of a package
            //
            if (*package) {
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
    if (failed > 0) {
        if (verbose)
            fprintf(stdout, "\n%s: %d additional files had trouble and weren't preprocessed\n", Me, failed);
        exit(2);
    }
    if (exit_with_status_1_if_files_modified && preprocessed)
        exit(1);
    exit(0);
}


// Preprocess a file.
// Takes:    name of file to be read
//           name of file to be written
// Returns: 0 --> success
//          -1 --> trouble
// Whether trouble leads to immediate aborting by the caller depends upon the
// value of keep_going.
int
preprocess(const char *srcFile, const char *dstFile)
{
    int trouble = OK;           // have we encountered any trouble yet?
    
    *package = 0;
    FILE *fin = fopen(srcFile, "r");
    if (!fin) {
        fprintf(stderr, "%s: can't find `%s'\n", Me, srcFile);
        return TROUBLE;
    }

    FILE *fout = fopen(dstFile, "w");
    if (!fout) {
        fprintf(stderr, "%s: can't create `%s':", Me, dstFile);
        perror("");
        (void) fclose(fin);     // in case keep_going is set.
        return TROUBLE;
    }

#if DEBUG
    for (int i = 0; i < Constants; ++i) {
        struct def *dp = Constant + i;
        fprintf(fout, "// [%s=%s]\n", dp->Name, 
                dp->Value ? : 
                ( dp->isset == membof(dp->) SET ? "1 (*SET*)" : "0 (*UNSET*)"));
    }
        
#endif

    SourceName = srcFile;
    SourceLineNo = 0;
    Nesting = 0;
    reviseState();

    if (PutIntoPackage != NULL)
        fprintf(fout, "\npackage %s;\n\n", PutIntoPackage);

    for (;;) {
        char line[MAXLINE];
        size_t linelen;

        if (ferror(fout)) {
            fprintf(stderr, "%s: Trouble while writing to output file: ", Me);
            perror(dstFile);
            /* Let's not keep going on this file in the face of I/O
             * trouble. */ 
            (void) fclose(fin);
            (void) fclose(fout);
            return TROUBLE;
        }
        

        if (!safe_fgets(line, sizeof line, fin)) { 
	    // fprintf(stderr, "line = %s", line);
            if (feof(fin)) {
                if (fclose(fin)) {
                    fprintf(stderr, 
                            "%s Trouble while closing an input file: ", Me);
                    perror(srcFile);
                    if (!keep_going)
                        return TROUBLE;
                    trouble = TROUBLE;
                }; 

                if (fclose(fout)) {
                    fprintf(stderr,
                            "%s: Trouble while closing an output file: ", Me);
                    perror(dstFile);
                    if (!keep_going)
                        return TROUBLE;
                    trouble = TROUBLE;
                }

                if (Nesting) {
                    do {
                        struct nest_st *u = &unmatched[--Nesting];
                        fprintf(stderr, "%s: %s:%d: Never found a matching #endif for this line: %s\n", Me, SourceName, u->lineNo, u->lineTxt);
                    } while (Nesting > 0);
                    
                    if (!keep_going)
                        return TROUBLE;
                    trouble = TROUBLE;
                }
                // Done preprocessing the file!
                return trouble;
            }
            if (ferror(fin)) {
                fprintf(stderr, "%s: Trouble while reading the file \"%s\": ",
                        Me, srcFile);
                perror("");
                /* Let's not keep going on this file in the face of I/O
                 * trouble. */ 
                (void) fclose(fin);
                (void) fclose(fout);
                return TROUBLE;
            } 
            fprintf(stderr, "%s: Internal error: fgets() returned NULL, but\n"
                    "     neither feof() nor ferror() are true!\n"
                    "     This should never happen.\n"
                    "%s: Aborting execution.\n", Me, Me);
            exit(13);
        }
        linelen = strlen(line);
        assert(linelen > 0);    // otherwise we'd have gotten feof().
        if (line[ linelen - 1 ] != '\n') {
            inputErr("Line too long (over %lu characters).\n", 
                     (unsigned long) linelen);
            exit(13);
        }

        ++SourceLineNo;
        

        enum scan_token token_type;
        union scan_arg scanned;

        switch (token_type = scan(srcFile, line, &scanned, trouble)) {
        case TT_TEXT:
#if DEBUG
            printState(fout, "TEXT ", line);
#endif
            // Note: We could do error checking on each write, but we'll check
            // at fclose() time instead, since ferror() is persistent until
            // cleared. 
            fputs(PassLines ? line : "\n", fout);
            continue;

        case TT_REPLACE:
#if DEBUG
            printState(fout, "REPLACE ", line);
#endif
            fputs(PassLines ? scanned.Replace : "\n", fout);
            continue;

        case TT_IF: 
        {
            /* temp. pointer to current unmatched struct */
            register struct nest_st *u = &unmatched[Nesting++];
            u->trueSeen = u->val = scanned.If;
            u->lineNo = SourceLineNo;
            strcpy(u->lineTxt, line);
            char *nlp = strchr(u->lineTxt, '\n');
            assert(nlp);        // must be newline terminated.  That's how
                                // fgets() works, and we tested above.
            *nlp = '\0';
            
            reviseState();
#if DEBUG
            printState(fout, "IF   ", line);
#endif
            fputs("\n", fout);
            continue;
        }
        case TT_ELIF:
        {
            if (Nesting == 0) {
                inputErr("#elif with no corresponding #if");
                continue;
            }
            register struct nest_st *u = &unmatched[Nesting - 1];
            
            if (u->trueSeen) 
                u->val = false;
            else 
                u->trueSeen = u->val = scanned.If;
            u->lineNo = SourceLineNo;
            
            reviseState();
#if DEBUG
            printState(fout, "ELIF ", line);
#endif
            fputs("\n", fout);
            continue;
        }
        case TT_ELSE:
        {
            if (Nesting == 0) {
                inputErr("#else with no corresponding #if");
                continue;
            }
            register struct nest_st *u = &unmatched[Nesting - 1];
            if (u->trueSeen) 
                u->val = false;
            else
                u->trueSeen = u->val = true;

            u->lineNo = SourceLineNo;
            reviseState();
#if DEBUG
            printState(fout, "ELSE ", line);
#endif
            fputs("\n", fout);
            continue;
        }
        case TT_ENDIF:
            if (Nesting == 0) {
                inputErr("#endif with no corresponding #if: %s", line);
                continue;
            }
            --Nesting;
            reviseState();
#if DEBUG
            printState(fout, "ENDIF", line);
#endif
            fputs("\n", fout);
            continue;

        case TT_UNRECOGNIZED:
            inputErr("unrecognized preprocessor directive: %s", line);
            continue;

        default:
            fprintf(stderr, "%s: %s:%d: Internal error: scan() should never return token type %d\n", Me, SourceName, SourceLineNo, token_type);
            exit(13);
        }
        /* NOTREACHED */
    }
    /* NOTREACHED */
}

// Compute new preprocessor state after scanning a constant.
// Uses:    nesting[].val
//          Nesting
// Sets: PassLines
//
void reviseState(void) 
{

    PassLines = true;
    for (int i = 0; i < Nesting; ++i)
        PassLines = PassLines && unmatched[i].val;
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
        fprintf(fout, "%s ", unmatched[i].val ? "true" : "false");
   
    for (; i < 5; ++i)
        fprintf(fout, "..");
   
    fprintf(fout, "%s] %s %s", PassLines ? "pass" : "hide", constant, line);
}
#endif

// Scan for a preprocessor directive.  Also handles "package" declarations.
//
// Taken:    line to be scanned
//           argp: place to put value of #if or #elif directive, if found
//              or place to put replacement text
// Returned: TT_TEXT         --> found no                directive
//           TT_IF           --> found '//-#if <name>'   directive
//           TT_ELIF         --> found `//-#elif <name>' directive
//           TT_ELSE         --> found `//-#else'        directive
//           TT_ENDIF        --> found `//-#endif'       directive
//           TT_REPLACE      --> found `//-#value <name>' directive
//           TT_UNRECOGNIZED --> found unrecognized      directive
// In some cases, value is modified.

enum scan_token
scan(const char *srcFile, char *line, union scan_arg *argp, int &trouble) 
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
        if (*package)
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
       
        argp->Replace = evalReplace(p, trouble);
        
        return TT_REPLACE;
    }

    // look for "if "
    //
    if (strneql(p, "if", 2) && isspace(p[2])) {
        p +=3;
        while (isspace(*p))
            ++p;
        argp->If = eval(p, trouble);
        return TT_IF;
    }
     
    // look for "elif"
    //
    if (strneql(p, "elif", 4) && isspace(p[4])) {
        p += 5;
        while (isspace(*p))
            ++p;

        argp->If = eval(p, trouble );
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
//              where condition is name <&& name> <|| name>
//                      (!name toggles the sense)
//  Returns the name of the token, but NOT null terminated
//    Upon return, *c points to the cursor, the character immediately after
//       the end of the name 
//    It will return the leading ! if one exists.
//  
//    The only characters forbidden to a token are '&', '|', and whitespace.
char *
getToken(char **cursorp, int UNUSED &trouble)
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
//                  or "&&")
//            false --> not on top of one.
// Trims leading whitespace, leaves the cursor before the bool. operator.

bool getBoolean(char **cursorp)
{
    while (isspace(**cursorp))
        ++*cursorp;

    return ((*cursorp)[0] == '|' && (*cursorp)[1] == '|') 
        || ((*cursorp)[0] == '&' && (*cursorp)[1] == '&');
}


/* Returns a pointer to the replacement token for a //-#value directive.
 * Note that this will interpret the token !FOO as being a lookup of a token
 * named "!FOO".  This is not good -- it would lead to a strange error message
 * in response to the case "//-#value !FOO" -- but has not been a problem, in
 * practice. */
const char *
evalReplace(char *cursor, int &trouble) 
{
    char *name = getToken( &cursor, trouble );
    assert( ( cursor == name ) ? ( *name == '\0' ) : *name);
    size_t len = cursor - name;
    if (len == 0) {
        inputErr("The //-#value <name> preprocessor construct needs a <name>");
        trouble = TROUBLE;
        return "BOGUS #value";
    }
    for (int i = 0; i < Constants; ++i) {
        struct def *dp = Constant + i;
        
        if (strlen(dp->Name) == len && strneql(name, dp->Name, len)) {
            if (! dp->Value) {
                inputErr(
                    "//-#value used on boolean constant '%s'", 
                    dp->Name);
                trouble = TROUBLE;
                return dp->Name;        // Error return
            }
            return dp->Value;
        }
    }
    inputErr("//-#value used on undefined constant '%*.*s'", (int) len, (int) len, name);
    trouble = TROUBLE;
    return name;
}


bool
eval(char *cursor, int &trouble) 
{
    int match;                  // -1 if not found, 1 if defined true (1), 
                                // 0 if defined 0 (false).
    for (;;) {
        match = -1;
        char *name = getToken( &cursor, trouble );
        int toggle = 0;
        if ( name[0] == '!' ) {
            toggle = 1;
            name++;
        }
        assert(cursor >= name);
        size_t len = cursor - name;
        if (len == 0) {
            inputErr("missing <name> in preprocessor condition");
            trouble = TROUBLE;
            return false;       // did not match.
        }
        
        for (int i = 0; i < Constants; ++i) {
            struct def *dp = Constant + i;
            if (strlen(dp->Name) == len && memcmp(dp->Name, name, len) == 0) {
                /* Matched the token.  Now evaluate. */
                if (dp->Value) {
                    if (only_boolean_constants_in_conditions) {
                        inputErr(
                            "The constant '%*.*s' is a Value constant;\n"
                            "     //-#if and //-#elif require a"
                            " Boolean (1 or 0) constant.", (int) len, (int) len, dp->Name);
                        trouble = TROUBLE;
                        return false; // So we will assume false.
                    }
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
            if (! undefined_constants_in_conditions) {
                inputErr("Undefined constant named \"%*.*s\""
                         " in preprocessor condition", (int) len, (int) len, name);
                trouble = TROUBLE;
            }
            match = 0;
        }
        
        if ( toggle ) 
            match = !match;
        if ( !getBoolean( &cursor ) ) 
            break;
        if ( cursor[0] == '|' ) {
            if ( match )        // skip further syntax checking; TODO: Check
                                // the rest of the syntax.
                return true;
        } else {
            assert(*cursor == '&');
            if ( !match )       // skip further checking.
                return false;
        }
        cursor += 2;            // skip && or ||
    }
    while (*cursor && isspace(*cursor))
        ++cursor;
    if (*cursor) {
        inputErr("Garbage characters (\"%s\") are at the"
                 " end of a preprocessor condition.", cursor);
        trouble = TROUBLE;
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
        fprintf(stderr, "%s: Aborting Execution.\n", Me);
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
        fprintf(stderr, "%s: Aborting Execution.\n", Me);
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

      

static void 
delete_on_trouble()
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
    fprintf(stderr, "%s: Dying due to signal # %d"
#ifdef __GLIBC__            
            " (%s)"
#endif
            "; cleaning up.\n", Me, signum
#ifdef __GLIBC__            
            , strsignal(signum)
#endif
        );
    delete_on_trouble();
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
    fprintf(stderr, "%s: %s:%d: ", Me, SourceName, SourceLineNo);
    vfprintf(stderr, msg, ap);
    va_end(ap);                 /* silly to clean up; we're going to die
                                 * anyway.  */
    putc('\n', stderr);

    if (keep_going) {
        delete_on_trouble();
        return;
    }
    fprintf(stderr, "%s: Aborting Execution.\n", Me);
    exit(2);
}


#if 0                           /* This appears to work fine, but some say
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
                "%s: Trouble trying to set up a handler for signal %d",
                Me, signum);
#ifdef __GLIBC__
        fprintf(stderr, " (%s)", strsignal(signum));
#endif
        fputs(": ", stderr);
        perror((char *) NULL);
        fprintf(stderr, "%s: ...going on as best we can\n", Me);
        return;
    } 
    if (oldact.sa_handler == SIG_IGN) {
        /* Don't interfere with shells that turn off the handling of certain
           signals; reset it, instead. */
        r = sigaction(signum, &oldact, (struct sigaction *) NULL);
        if (r) {
            fprintf(stderr, "%s: Trouble resetting signal %d", Me, signum);
#ifdef __GLIBC__
            fprintf(stderr, " (%s)", strsignal(signum));
#endif
            fputs("'s handler back to SIG_IGN: ", stderr);
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
    if (atexit(&delete_on_trouble)) {
        fprintf(stderr,"%s: ", Me);
        perror("Unable to register delete_on_trouble() via on_exit.  This should never happen");
        // exit(2);
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

/** fgets, but handle certain errors internally by griping and dying:
 -- reading a null byte
 -- reading a line that is too long.

 If we read a final line that is not newline-terminated, then add a newline.
 This seems kludgy, but does simplify the error printing.
 
*/
static char *
safe_fgets(char *buf, size_t buflen, FILE *fin)
{
    unsigned i;
    int c = '\0';               // We know that buflen is always > 2, but the
                                // compiler does not, and warns.
    for (i = 0; i < buflen - 2; ) {
	c = getc(fin);
	// fprintf(stderr, "c == %c (%x)\n", c, c);
	
	if (c == EOF) {
	    if (i == 0)		// If no new chars were read
		return NULL;
	    else
		break;		// Otherwise, break and return what we have.
	}
	
	if (c == '\0') {
	    inputErr("The file contains a null (zero) byte -- this is not a valid Java program.");
	    exit(13);
	}
	buf[i++] = c;
	if (c == '\n')
	    break;
    }
    if (i >= buflen - 2) {
	inputErr("Line too long (over %lu characters).\n", 
		 (unsigned long) buflen - 2);
	exit(13);
    }
    assert(feof(fin) || (c == '\n')); // We DID read a complete line, no?
    if (c != '\n')
	buf[i++] = '\n';
    // null-terminate the line
    buf[i++] = '\0';
    assert(i <= buflen);
    // fprintf(stderr, "I JUST READ: %s", buf);
    return buf;
}
