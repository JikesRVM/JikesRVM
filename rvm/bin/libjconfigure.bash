#! /bin/bash
## BEGIN libjconfigure.bash
#
# (C) Copyright IBM Corp. 2001, 2003, 2003
#
# $Id$
#
# N.B.: This is an auxiliary set of library functions.  It is loaded
#       by "jconfigure" and by the "jbuild" and "jbuild."* scripts
#       that "jconfigure" generates.

#       We do not execute this script directly; the `#! /bin/bash'
#	is just there to give a hint to the Emacs text editor about which
#	variant of Shell-script mode it should use.
#
# @author Steven Augart
# @date  Tuesday, September 9, 2003
# @modified  Monday, September 15, 2003
# @modified  Wednesday, January 21, 2004


# The trace argument can be:
# -trace		 (gets most things but skips the thousands of
#			  lines of bootimagewriter output)
# -trace=ant		# Just ant is of interest
# -trace=jbuild		# high-level
# 
# This is then converted to appropriate values for TRACE_FLAG (q.v., below.)
# TRACE_FLAG: (empty)  # only if nothing was said
# TRACE_FLAG: ,no-all,
# TRACE_FLAG: ,-trace,
# We can be tracing any of the following
#	preprocessor,make,BootImageWriter,ant,jbuild
# As well as the groups:
#	most,all
# See the help message in "jbuild -help" for more info.

# "jbuild" and its children access TRACE_FLAG through the shell
# function tracing().

# Variables we'll refer to in jconfigure and in jbuild*:
#       
TRACE_FLAG=""			# Arguments to the -trace command-line
				# argument flag.

VFLAG=""			# Set to -v if we want to call "set -v" 
				#  in a shell script.

XFLAG=""			# Like VFLAG, but contains "-x"

CLEAN_FLAG=""

MFLAGS=--silent			# Pass this flag to the invocation of "make"

## ME: The name of this program, for purposes of printing error messages.
## This is supposed to be already set, but I'm setting it here so that it's
## easier to test libjconfigure.bash independently of the rest of jconfigure.
[[ "${ME-}" != "" ]] || ME="${0##*/}"

# tracing() takes 0 or 1 arguments.
# Returns true (exit 0) if we are tracing
# Returns false (exit 1) if we are not.
# tracing() also turns off the -v and -x flags, if they're on, so
# that we don't get spammed with junk output.
function tracing() {
    set +vx
    # We go through some skulduggery here so that running jbuild
    # with -v or -x won't generate lots of junk output from tracing() and
    # other low-level functions.
    [[ $TRACE_FLAG ]] || { [ ! "$VFLAG$XFLAG" ] || set $VFLAG $XFLAG ; return 1 ; }

    local -i dflt=1      # default answer, false  (using exit codes)
    # If $TRACE_FLAG is set and is not in canonical form...
    if [[ $TRACE_FLAG != ,*, ]]; then
	# ...then canonicalize it.
	if [[ $TRACE_FLAG = -trace ]]; then
	    TRACE_FLAG=,most,
	elif [[ $TRACE_FLAG != -trace=* ]]; then
	    echo >&2 "${ME}: I don't understand the trace flag \"$TRACE_FLAG\"."
	    echo >&2 "${ME}: Aborting execution."
	    trap '' EXIT
	    kill $$
	else	        # TRACE_FLAG has the form -trace=<something>
	    TRACE_FLAG=",${TRACE_FLAG#-trace=},";
	fi
	# No-all is a no-op, since we only 
	# trace if it's explicitly requested.
	TRACE_FLAG=${TRACE_FLAG/,no-most,/,no-all,}
#	TRACE_FLAG=${TRACE_FLAG/,most,/,all,preprocessor,make,no-BootImageWriter,ant,jbuild,}
	TRACE_FLAG=${TRACE_FLAG/,most,/,all,no-BootImageWriter,}
    fi
    # TRACE_FLAG is now in canonical form.
    if [[ $TRACE_FLAG == *,all,* ]]; then
	dflt=0  # true
    fi
    if (( $# > 0 )); then
	local arg="$1"
	# If an exact match, return YES.
	if [[ $TRACE_FLAG == *,${arg},* ]]; then
	    { [ ! "$VFLAG$XFLAG" ] || set $VFLAG $XFLAG ; return 0; }
	# If an exact anti-match, return NO
	elif [[ $TRACE_FLAG == *,no-${arg},* ]]; then
	    { [ ! "$VFLAG$XFLAG" ] || set $VFLAG $XFLAG ; return 1; }
	fi
    fi 
    # Give in to the default, whatever that is.
    { [ ! "$VFLAG$XFLAG" ] || set $VFLAG $XFLAG ; return $dflt; }
}

# Copy the file  $1 to $2.  Display a message, consisting of the remaining args
# ($3 ... ), if given.  Feed $3 ... straight to "echo", which means we can 
# accept -n and other echo flags.
function copyIfNewer () {
    local SRC="$1"; shift;
    local DEST="$1"; shift;
    local -i copyit=0
    if [[ -d $DEST ]]; then
	local n=$(basename "$SRC")
	DEST=$DEST/$n
    fi
    if [[ ! -e $DEST ]] || [[ $SRC -nt $DEST ]]; then
	CLEANUP="rm -f $DEST"
	$CLEANUP
	run cp -f "$SRC" "$DEST"
	CLEANUP=""
	(( $# == 0 )) || echo >&2 "$@"
    fi
}

function run() {
    ! tracing make || { cleanline >&2 ; echo >&2 "$@" ; }
    "$@"
}

function chdir() {
    # Generate information useful for GNU Emacs.
    if tracing make; then
	echo >&2 "$ME: Leaving directory \`$PWD'"
    fi
    \cd "$1"
    if tracing make; then
	echo >&2 "$ME: Entering directory \`$PWD'"
    fi
}

## Error Reporting 

function config_gnufold () {
    ## If we have gnu fold, we can use it in showing messages.  If not, we can't.
    gnufold="/usr/bin/fold --width=65 --spaces"
    $gnufold < /dev/null &> /dev/null || gnufold=cat
}


function show_mesg_raw () {
    cleanline
    [[ ${gnufold-''} ]] || config_gnufold
    echo "$*" | $gnufold | sed -e '2,$s/^/     /';
}


function show_mesg () {
    # Display a message.  If it's a multi-line error message, indent
    # the second and subsequent lines by a few spaces.  
    # Try to auto-wrap the message if we have GNU Fold.

    show_mesg_raw "${ME}: $*";
}


function do_cleanup () {
    show_mesg >&2 "Cleaning up..."; 
    eval $CLEANUP
    if [[ "$CLEANUP" = ":" ]] ; then
	show_mesg >&2 "...no cleanup was needed."
    else
	show_mesg >&2 "...cleaned up."
    fi
}

function unexpected_exit() {
    xitstatus=$?
    show_mesg >&2 "Exiting unexpectedly with status $xitstatus."
    do_cleanup
}

trap 'unexpected_exit' EXIT

## Exit and report on error

## Between bash-2.05b-alpha1,
## and the previous version, bash-2.05a-release, the reporting of 
## line numbers changed.  The old behavior gives line #s relative to
## the start of the function we're in, when we're in a function.  The new
## (POSIX compliant) behavior is that the line #s are relative to the currently
## executing script.
declare -i have_function_relative_LINENO=0
# declare -i have_negative_LINENO_bug=0

function set_have_function_relative_LINENO () {
    # set_have_function_relative_LINENO depends upon its text being more 
    # than ten or so lines into the start of libjconfigure.bash.
    function err_guinea_pig () {
	local lineno="$1"; shift;
	local fname="$1"; shift;
	if (( lineno < 0 )); then
	    ## We actually don't handle this condition anywhere, just let the
	    ## default behavior fall through.  Instead we test for negative
	    ## values in the function err() itself.
	    # echo "have_negative_LINENO_bug=1"
	    echo "have_function_relative_LINENO=0"
	elif (( lineno < 20 )); then
	    echo "have_function_relative_LINENO=1"
	else
	    echo "have_function_relative_LINENO=0"
	fi
	# For debugging.
	[[ $- != *i* ]] ||  echo >&2 "err_guinea_pig($lineno, $fname)"
    }
## I have turned off this code because sending a signal gives us
## negative line #s in bash 2.05a and in 2.05b.
## I consider this to be a bug in Bash.
#     trap 'err_guinea_pig "$LINENO" USR1"$FUNCNAME"' SIGUSR1
#     # fire off err_guinea_pig
#     kill -USR1 $$
#     trap -- SIGUSR1
    trap 'err_guinea_pig "$LINENO" EXIT"$FUNCNAME"' EXIT
    set -e
    false
    :
}

## We execute this in a subshell.  We have to test using the EXIT
## form, since the ERR form triggers trouble if the ERR trap isn't
## implemented, and the signalling form triggers the negative_LINENO
## bug.
eval $( set_have_function_relative_LINENO )


## For the ERR Trap.
function err () {
    set +vx;			# turn off reporting here.
    local xited=$1; shift;
    local finalarg="$1"; shift;
    local lineno="$1"; shift;
    local funcname="$1"; shift;

    if  [[ ${lineno:-UNSET} = "UNSET" ]] || (( lineno <= 0 )); then
	show_mesg_raw >&2 "\
$ME: some command we just ran (probably with a final argument of \"$finalarg\") exited with status ${xited},  
${funcname:+in the shell function \"}${funcname}${funcname:+\"}";
    elif (( have_function_relative_LINENO )) && [[ ${funcname} ]]; then
	show_mesg >&2 "\
Some command we just ran (probably with a final argument of \"$finalarg\") exited with status ${xited}, in the shell function ${funcname}(), line # ${lineno}"

    else	  # Have script-relative line #s, and have a real one.
	show_mesg_raw >&2 "\
$ME:${lineno}: some command we just ran (probably with a final argument of \"$finalarg\") exited with status ${xited},  
${funcname:+in the shell function \"}${funcname}${funcname:+\"}";
    fi	
    if (( xited > 128 )); then
	local -i signo
	let signo=(xited - 128);
	show_mesg >&2 "The command was killed by signal # ${signo}"
    fi
    show_mesg >&2 "Aborting execution."; 
}

function enable_exit_on_error() {
    # Enable exit-on-error, but only if we're not interactive.
    # (if we're interactive, then we're busy debugging, and will go to 
    # other means instead... this is only active during development.)
    [[ $- == *i* ]] || set -e;
    # We've enabled it.  Reset the ERR trap, too
    # If we do not have the ERR trap, then the EXIT trap will have 
    # to serve double-duty as an ERR trap as well.
    trap 'err $? "$_" $LINENO "${FUNCNAME-}"' ERR 2> /dev/null ||     trap 'err_with_unexpected_exit $? "$_" $LINENO "${FUNCNAME-}"' EXIT 
}

function err_with_unexpected_exit () {
    set +vx;			# turn off reporting here.
    local xited=$1; shift;
    local finalarg="$1"; shift;
    local lineno="$1"; shift;
    local funcname="$1"; shift;
    err "$xited" "$finalarg" "$lineno" "$funcname"
    do_cleanup
}

enable_exit_on_error;
CLEANUP=":"
# This is a safety guard routine.  Trap any unexpected exits, although we don't
# anticipate any!

function signalled() {
    local -i xitstatus=$?
    local -i signum
#    let signum=(xitstatus - 128)
#    show_mesg >&2 "Got hit with signal # $signum.  Exiting abruptly."  
    show_mesg >&2 "Got hit with a signal while running in a Bash builtin.  Exiting abruptly."  
#    Cleaning up..."; 
#    eval $CLEANUP
#   show_mesg >&2 "...cleaned up."
    # and exit.
    trap -- INT
    kill -INT $$
    ## The EXIT trap will handle it.
}
maybe_trap=""
if [[ $(trap -l) == *") SIGPWR"* ]] ; then
    maybe_trap="PWR"
fi
trap signalled HUP INT ABRT BUS PIPE TERM ${maybe_trap}

# I'd like to make it always be an error if we were to access any 
# unset variables.  HOWEVER, we have a bug in BASH version 2.05b.0(1)-release
# (BASH_VERSINFO=([0]="2" [1]="05b" [2]="0" [3]="1" [4]="release" [5]="i386-redhat-linux-gnu"))
# whereby we are getting complaints that the variable "midline" was used before being set.
## So you may have to comment out the next line if the bug starts to show up for you.

set -o nounset;			# may cause trouble!

## Routines to clean a list of files or other things.

function cleanFileList() {
    sed -e $'s/^[ \t]*//' -e $'s/[ \t]*#.*//' -e '/^$/d' "$@"
}


## Message reporting in general.
declare -i midline=0;		# are we in the middle of a line?
declare -i num_echoes=0;
function echo() {
    builtin echo "$@"
    if [[ ${1-} = -n ]]; then
	midline=1
    else
	midline=0
    fi
    : ${num_echoes=0}		# Workaround Bash 2.05b bug.
    let ++num_echoes
}

declare -i opened_at=0;
function open_paren() {
    if [[ "$*" ]]; then
	echo -n "($*... "
    else
	echo -n '('
    fi
    opened_at=$num_echoes;
}
function close_paren() {
    : ${num_echoes=0} ${opened_at=0} # Work around bug in Bash 2.05b
    if (( opened_at < num_echoes )); then
	cleanline
	echo -n "..."
    fi
    echo -n "$@) "
}

## Print out a clean line.
function cleanline() {
    : ${midline=0}		# Work around Bash 2.05b bug.
    if ((midline)); then
	echo ""
    fi
}




## END libjconfigure.bash
