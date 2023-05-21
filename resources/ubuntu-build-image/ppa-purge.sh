#!/bin/bash -u
# SPDX-License-Identifier: GPL-3.0-only
# Source: https://git.launchpad.net/~jarnos/ppa-purge/tree/ppa-purge?id=f4bc62ba5cb5ef2ad4bbbfbad56a8594790e81e7
#
# A script to disable a repository and to remove all packages installed in your
# system from it and to revert back to the version (if any) available from a
# leftover repository.
#
# AUTHORS: Robert Hooker (Sarvatt), Lorenzo De Liso, Tormod Volden,
# Lorenzo De Liso, Tim Lunn (Darkxst), Jarno Ilari Suni (jarnos)

export LC_ALL=C # Use C locale to get standard sorting and better regex
# performance.
export TMPDIR=/dev/shm # dir for mktemp to create files in

# Constants
declare -r APT=apt-get \
yes_option='--force-yes' \
F_ARCHS=$(dpkg --print-foreign-architectures) \
mytmpdir=$(mktemp -d)
declare -r PKGS=${mytmpdir}/pkgs \
REVERTS=${mytmpdir}/reverts \
EXITCODE=${mytmpdir}/exitcode \
program_name=ppa-purge \
program_full_name='ppa-purge APT Software Purger' \
program_pkg_name=ppa-purge \
program_version=0.5.0.0 \
copyright='authors' \
copyright_year=

# Initialize some variables
restore=t
declare -a lists=()
url=
no_update=
unset -v IFS

# Defaults
# Default for RELEASE will be set below, if needed.
yes=
simulate=
initial_update=
remove=
verbose=1
figure_soname=

# Functions to write output nicely.
msg() {
	echo "[$program_name] $*"
}

warn() {
	msg "Warning:  $*" 1>&2
}

error() {
	msg "Error:  $1" 1>&2
	exit ${2:-1}
}

apt_update() {
	msg "Updating package lists..."
	case $verbose in
		0)	exec 3>/dev/null ;;
		1)	exec 3>&1
	esac
	local exit_code=
	{
		# read and echo warning & error messages
		w=
		while IFS= read -r; do
			printf '%s\n' "$REPLY" >&2
			[[ $REPLY =~ ^[EW]: ]] && exit_code=0 # warning or error detected
		done
	} < <($APT update 2>&1 >&3 || printf $? >$EXITCODE)
	exec 3>&-

	[[ -s $EXITCODE ]] && IFS= read -r exit_code <"$EXITCODE"

	[[ $exit_code ]] && {
		no_update=t
		[[ $yes || $exit_code -ne 0 ]] || {
		 read -r -p "$(msg 'Continue even if error or warning was detected (Y/n)? ')"
		 [[ ! ( -z $REPLY || $REPLY =~ ^[Yy]$ ) ]]
		} && error "Updating package lists failed; error code ${exit_code}." 5
	}
	return 0
}

finish() {
	set +o pipefail
	((${#lists[@]} > 0)) && {

		[[ $restore ]] && {
			msg 'Restoring .list file(s):'
			for file in "${lists[@]}"; do
			 mv -fv "$file".save "$file"
			done
			[[ $no_update ]] || apt_update || :
		} || {
			[[ $remove ]] && {
				msg 'Removing .list file(s):'
				for file in "${lists[@]}"; do
					grep -Evq '^[[:blank:]]*(#.*)?$' "$file" || {
						# everything is commented out
						rm -rv "$file" "$file".save* || :
					}
				done
			}
		}
	}
	rm -rf "$mytmpdir"
}

trap finish 0

usage() {
fold -s -w "${COLUMNS:-80}" << EOF
Usage: $program_name [options] ppa:<ppaowner>[/<ppaname>]
or: $program_name [options] <URL> [<distribution>]
or: $program_name [options] <distribution>
or: $program_name { --help | --version }

$program_name will disable matching repository/repositories and revert \
packages to versions available for target release. If a \
package is not available from any remaining repositories, it will be removed, \
unless it is a package related to current kernel or it is the package of this \
command. If <ppaname> is not given, 'ppa' is used as default. If a \
distribution (such as xenial-proposed) is given, repository must match it to \
be purged. Bash completion is supported.

Exit Status:
 0 on success; non-zero integer on error

Options:
 --figure-soname-bumps    Explicitly install packages that are figured as soname
                          bumped version of a package to be removed. Display
                          list of these packages.
 -s, --simulate           No action; do not downgrade or remove packages.
 -i, --initial-update     Do initial update of package lists. Use this, if you
                          are not sure the package lists are updated already.
 -r, --remove             Remove unneeded .list files or repository entries
                          instead of leaving backups or commenting entries out,
                          respectively.
 -v <level>, -v<level>, --verbose <level>, --verbose=<level>
                          Verbosity level of "$APT update" output.
                          0: only errors and warninings
                          1: normal output (default).
 -y, --yes                Run non-interactively; do not prompt for changes.
 -h, --help               Display this help text and exit.
 -V, --version            Show version and exit.

Environment:
 RELEASE    Target release to be downgraded to. If not set, output of
            "lsb_release -cs" is used.

Examples:
 sudo $program_name ppa:ubuntu-x-swat/x-updates

 Purge the same PPA on Linux Mint 18.1 (based on Ubuntu 16.04):

 sudo RELEASE=16.04 $program_name ppa:ubuntu-x-swat/x-updates

 Purge Google Chrome repository:

 sudo $program_name http://dl.google.com/linux/chrome/deb

 Downgrade packages from xenial-proposed in Ubuntu 16.04:

 sudo $program_name xenial-proposed

EOF
	exit $1
}

show_version() {
printf "$program_full_name $program_version
Copyright (C) $copyright_year $copyright.
License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>
This is free software: you are free to change and redistribute it.
There is NO WARRANTY, to the extent permitted by law.\n"
}

suggest_usage() {
	echo "Run '$program_name -h' to get help."
}

# escape special characters (http://unix.stackexchange.com/a/209744/111181)
escape_regex() {
	printf '%s' "$1" | sed 's/[.[\*^$()+?{|]/\\&/g'
}


revert() {
	msg "Generating revert list..."

	# Create apt argument list for reverting packages

	# store available packages in a sorted variable for faster access.
	avail_pkgs=$(apt-cache dumpavail | grep '^Package:' | cut -d' ' -f2 | sort -urV)

	# Create an associated array for fast checking of available packages.
	# Creating takes some time though, say 1s, but will save a lot if there are many
	# reverts.
	declare -A avail=()
	for PKG in $avail_pkgs; do avail[$PKG]=; done

	# tag for current kernel version
	curkerversion=$(escape_regex $(uname -r | cut -d- -f1,2))
	# tag for current kernel flavor
	curkerflavor=$(escape_regex $(uname -r | cut -d- -f3-))
	REINSTALL=
	declare -a sonamepkg=()
	while IFS= read -r PACKAGE; do
		PKG=${PACKAGE%:*}

		# Test if PKG is still available
	#	if ! grep -xFq "$PKG" <<<"$avail_pkgs"; then
		if [[ -z ${avail[$PKG]+x} ]]; then

			# Explicitly remove the package that does not exist in archive unless
			# it is versioned kernel package matching the current kernel or the
			# package of this program.
			[[ $PKG =~ ^linux-.+-${curkerversion}(-${curkerflavor})?$ ||
			$PKG == $program_pkg_name ]] &&
			msg "Note: Not removing $PKG package." ||
			{
				REINSTALL+=" $PACKAGE-"

				[[ $figure_soname && $PKG != linux-* ]] && {
					# Maybe could even restrict to lib* ?
					# check if the package is availabe with another version tag
					pkg_regex=$(escape_regex "$PKG")
					soname_regex=$(sed -r \
					-e 's/[[:digit:]][[:lower:]]$/[[:digit:]][[:lower:]]/' \
					-e 's/[[:digit:]]+/[[:digit:]]+/g' <<<"$pkg_regex")
					[[ $soname_regex != $pkg_regex ]] && {

						# get the greatest available version of the package
						APTAVAIL=$(grep -m 1 -E "^${soname_regex}$" \
						<<<"$avail_pkgs" || :)

						# downgrade packages that have a soname bump
						[[ $APTAVAIL ]] && {
							newpkg=${APTAVAIL}${PACKAGE#$PKG}
							sonamepkg+=("$newpkg")
							REINSTALL+=" $newpkg/$RELEASE"
						}
					}
				}
			}
		else
			REINSTALL+=" $PACKAGE/$RELEASE"
		fi
	done <$REVERTS
	unset -v avail avail_pkgs
	>$REVERTS

	[[ ${#sonamepkg[*]} -gt 0 ]] && {
		msg "Going to install these packages due to soname bump figuring:
	$(printf '%s\n' "${sonamepkg[@]}")"
		msg "These of them are installed already (but maybe different version):
	$(dpkg-query -W -f='${db:Status-Abbrev} ${binary:Package}\n' "${sonamepkg[@]}" \
	2>/dev/null | awk '/^.i /{print $2}')"

		[[ $yes ]] || {
			read -p "$(msg 'Continue (Y/n)? ')" -r
			[[ -z $REPLY || $REPLY =~ ^[Yy]$ ]] || exit
		}
	}

	msg "Reverting..."
	$APT $simulate $yes -V install $REINSTALL || {
		exit_status=$?
		[[ $exit_status -eq 1 ]] &&
		# User aborted apt-get; no packages were downgraded or removed.
		 exit 5 ||
		 error "Something went wrong with $APT; error code $exit_status. \
	Packages may not have been reverted." 5
	}
	msg "Reverting packages finished successfully."
}

# Command line options
env -u GETOPT_COMPATIBLE getopt --test >/dev/null || [[ $? -ne 4 ]] && {
	# This should not happen with util-linux's getopt.
	error '`getopt --test` failed in this environment.' 3
}

# Option name followed by ':' denotes an option that has an argument.
# Options are separated by ','.
params=$(env -u GETOPT_COMPATIBLE getopt -o siyhrv:V \
-l simulate,initial-update,yes,help,version,remove,verbose:,\
figure-soname-bumps --name "[$program_name] Error" -- "$@") || {
	# If $? = 1, command line is invalid; getopt displays an error message
	[[ $? -eq 1 ]] && exit 1
	>&2 error 'getopt failed.' 3
}
# $params contains at least --

eval set -- "$params"
unset -v params
while :; do
	case $1 in
		-s|--simulate ) simulate='-s' ;;
		-i|--initial-update ) initial_update=t ;;
		-y|--yes ) yes="$yes_option" ;;
		-V|--version ) show_version; exit ;;
		-h|--help ) usage 0 ;;
		-r|--remove ) remove=t ;;
		-v|--verbose )
			case $2 in
			 0|1) verbose="$2" ;;
			 *) error "Invalid verbose level." 1 ;;
			esac
			shift 2; continue ;;
		--figure-soname-bumps ) figure_soname=t ;;
		-- ) # End of all options.
			shift; break
	esac
	shift
done

distro=
while [[ ${1-} ]]; do
	if [[ $1 =~ ^ppa:([^/[:blank:]]+)(/[^/[:blank:]]+)?$ ]]; then
		url=ppa.launchpad.net/${BASH_REMATCH[1]}${BASH_REMATCH[2]:-/ppa}/ubuntu
	elif [[ $1  =~ ^https?://([^[:blank:]]*[^/[:blank:]])/?$ ]]; then
		url=${BASH_REMATCH[1]}
	else
		distro=$1
	fi
	shift
done

[[ -z $url && -z $distro ]] && {
	error "No argument given. $(suggest_usage)"
}

if [[ ${RELEASE+x} ]]; then
	[[ $RELEASE =~ ^[^/[:blank:]]+$ ]] || {
		error "Invalid target release name :" \'"$RELEASE"\'
	}
else
	RELEASE=$(lsb_release -c -s)
fi

if [ "$(id -u)" != "0" ]; then
	error "This script would need superuser privileges, use sudo" 2
fi

[[ $initial_update ]] && apt_update
no_update=t

msg "To be removed: ${url} ${distro}"

# Make list of all packages available from the matching archive(s)
[[ $url ]] && _url=${url//\//_} || _url='*'
for LIST in /var/lib/apt/lists/${_url}_dists_*_Packages; do
	if [ -f $LIST ]; then
		nomatch=
		if [[ $distro ]]; then
			dist=${LIST##*_dists_}
			dist=${dist%%_*}
			[[ $dist == $distro ]] || nomatch=t
		fi
		[[ $nomatch ]] ||
		 grep "^Package: " $LIST | cut -d " " -f2 >> $PKGS
	fi
done

no_pkgs=
if [ ! -s $PKGS ]; then
	warn "Could not find matching packages."
	no_pkgs=t
else
	# Get multi-arch package names for revert list
	sort -u $PKGS | xargs dpkg-query -W \
	 -f='${binary:Package}\t${db:Status-Abbrev}\n' 2>/dev/null |
	  awk '/\tii $/{print $1}' > $REVERTS
	>$PKGS
fi

# Disable matching lines from sources.list files
[[ $url ]] &&
 regex='^deb(-src)?([[:blank:]]+\[.+\])?[[:blank:]]+https?://'"$(escape_regex "$url")"'/?[[:blank:]]+([^[:blank:]]+)' ||
 regex='^deb(-src)?([[:blank:]]+\[.+\])?[[:blank:]]+https?://[^[:blank:]]+ +([^[:blank:]]+)'
newlist=
for LIST in $(find /etc/apt/ -name "*.list" -exec realpath -e '{}' \;); do
	changed=
	while read -r; do
	 nomatch=
	 [[ "$REPLY" =~ $regex ]] && {
		 if [[ $distro ]]; then
			[[ ${BASH_REMATCH[3]} == $distro ]] || nomatch=t;
		 fi
	 } || nomatch=t

	 [[ $nomatch ]] && newlist+="$REPLY"$'\n' || {
		[[ $remove ]] && msg "Remove entry: $REPLY" || {
			 newlist+="# $REPLY"$'\n'
			 msg "Disable entry: $REPLY"
		}
		changed=t
	 }
	done <$LIST

	[[ $changed ]] && {
		msg "Making backup of .list file:"
		mv -fvb "$LIST" "$LIST".save
		lists+=("$LIST")
		printf %s "$newlist" > "$LIST"
	}
	newlist=
done

(( ${#lists[@]} > 0 )) && {
	[[ $yes ]] || {
		read -p "$(msg 'Continue with these changes (Y/n)? ')" -r
		[[ -z $REPLY || $REPLY =~ ^[Yy]$ ]] || exit
	}

	no_update=
	apt_update

	[[ $no_pkgs ]] || revert

	[[ $simulate ]] &&
	 msg "Restoring repository entries, because this was just simulation:" \
	 || restore=
	# successful operation; no need to restore, unless in simulation mode.
}

exit 0
