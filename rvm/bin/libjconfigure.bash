#! /bin/bash
## BEGIN libjconfigure.bash
#
# (C) Copyright IBM Corp. 2001, 2003, 2003
#
# $Id$
#
# N.B.: This is an auxiliary set of library functions for loading.
#       We do not execute this script directly; the `#! /bin/bash'
#	is just there to give a hint to the text editor (or at least to
#	Emacs) about what mode to go into.
#
# @author Steven Augart
# @date  Tuesday, September 9, 2003


# tracing() takes 0 or 1 arguments.

# The trace argument can be:
# -trace		 (gets most things but skips the thousands of
#			  lines of bootimagewriter output)
# -trace=ant		# Just ant is of interest
# -trace=jbuild		# high-level
# 
# This is then converted to:
# TRACE_FLAG: (empty)  # only if nothing was said
# TRACE_FLAG: ,no-all,
# TRACE_FLAG: ,-trace,
# We can be tracing any of the following
#	preprocessor,make,BootImageWriter,ant,jbuild
# As well as the groups:
#	most,all
# See the help message in "jbuild -help" for more info.

# Returns true (exit 0) if we are tracing
# Returns false (exit 1) if we are not.

# Variables we'll refer to
TRACE_FLAG=""
VFLAG=""
XFLAG=""
CLEAN_FLAG=""
MFLAGS=--silent

function tracing() {
    set +vx
    # We go through some skulduggery here so that running jbuild
    # with -v or -x won't generate lots of junk output.
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
	TRACE_FLAG=${TRACE_FLAG/,most,/,preprocessor,make,no-BootImageWriter,ant,jbuild,}
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

# Copy $1 to $2.  Display a message, consisting of the remaining args
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
	(( $# == 0 )) || echo "$@"
    fi
}

function run () {
    ! tracing make || echo "$@"
    "$@"
}

function chdir() {
    # Generate information useful for GNU Emacs.
    ! tracing make || echo "$ME[0]: Entering directory \`$1'"
    \cd "$1"
}

## Error Reporting 

function config_gnufold () {
    ## If we have gnu fold, we can use it in showing messages.  If not, we can't.
    gnufold="/usr/bin/fold --width=65 --spaces"
    $gnufold < /dev/null &> /dev/null || gnufold=cat
}


function show_mesg_raw () {
    [[ ${gnufold-''} ]] || config_gnufold
    echo "$*" | $gnufold | sed -e '2,$s/^/     /';
}


function show_mesg () {
    # Display a message.  If it's a multi-line error message, indent
    # the second and subsequent lines by a few spaces.  
    # Try to auto-wrap the message if we have GNU Fold.

    show_mesg_raw "${ME}: $*";
}


## Exit and report on error

## For the ERR Trap.
function err () {
    set +vx;			# turn off reporting here.
    local xited=$1; shift;
    local finalarg="$1"; shift;
    local lineno="$1"; shift;
    local funcname="$1"; shift;
    show_mesg_raw >&2 "\
$ME:${lineno}: some command we just ran (probably with a
final argument of \"$finalarg\") exited with status ${xited},
${funcname:+possibly in the shell function \"}${funcname}${funcname:+\"}";
    show_mesg >&2 "Aborting execution."; 
    [[ $- != *i* ]] || { trap '' TERM ; kill $$ ; }
}

function enable_exit_on_error() {
    # Enable exit-on-error, but only if we're not interactive.
    # (if we're interactive, then we're busy debugging, and will go to 
    # other means instead... this is only active during development.)
    [[ $- == *i* ]] || set -e;
    # We've enabled it.  Reset the ERR trap, too
    trap 'err $? "$_" $LINENO "${FUNCNAME-}"' ERR 2> /dev/null || :
}


enable_exit_on_error;
CLEANUP=":"
# This is a safety guard routine. Trap any unexpected exits, although we don't
# anticipate any!

function signalled() {
    local -i xitstatus=$?
    local -i signum
    let signum=(xitstatus - 128)
    show_mesg >&2 "Got hit with signal # $signum.  Exiting abruptly."  
#    Cleaning up..."; 
#    eval $CLEANUP
#   show_mesg >&2 "...cleaned up."
    # and exit.
    trap -- TERM
    kill $$
}
trap signalled HUP INT ABRT BUS PIPE TERM PWR

function unexpected_exit() {
    xitstatus=$?
    show_mesg >&2 "Exiting unexpectedly with status $xitstatus.  Cleaning up..."; 
    eval $CLEANUP
    show_mesg >&2 "...cleaned up."
}

trap 'unexpected_exit' EXIT

# Stop immediately if any programs we call return errors.
#
set -o nounset;			# may cause trouble!

## Routines to clean a list of files or other things.

function cleanFileList() {
    ${SED=sed} -e 's/^[ 	]*//' -e 's/[ 	]*#.*//' -e '/^$/d' "$@"
}

## END libjconfigure.bash
