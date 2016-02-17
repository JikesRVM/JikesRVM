## How to make a release of Jikes RVM

1. Leading up to a release, here are the steps to take.  All commits are to head of master (default branch).
    1. Update the release number in build.xml (will continue to have +hg suffix) and commit change
    2. Export the userguide from confluence.  Update html and pdf versions of userguide and commit.
    3. Update JIRA version management to indicate that version has been released.
    4. Generate text release notes from JIRA and put them in NEWS.txt.  Commit.
    5. Generate javadoc (apidoc target).  If needed, fix errors and commit changes.
    6. Upload javadoc to static webspace on sourceforge (htdocs/apidocs/version).  Switch "latest" symlink to point to version.

2. In a clean hg repository (no incoming/outgoing changesets). Perform the following steps
    1. Switch to the release branch (hg update release)
    2. Merge tip to the release branch (hg merge default; hg commit)
    3. Edit build.xml to remove the +hg from the release number and set the hg.version field.  Commit
    4. Tag the release (hg tag <version>; hg push)

3. Clone a new .hg repository and create the release tar balls
    1. hg clone http://hg.code.sourceforge.net/p/jikesrvm/code -b release jikesrvm-version
    2. rm -rf jikesrvm/.hg
    3. tar cjf jikesrvm-version.tar.bz2 jikesrvm-version;  tar czf jikesrvm-version.tar.gz jikesrvm-version;
    4. Extract the portion of NEWS.txt relevant to this release into README.txt (will be used for ReleaseNotes on SF file download).

4. Publish and announce the release
    1. Upload release tar balls and README.txt to sourceforge; set it as default download using Files GUI.
    2. Update the confluence Releases page to link to the new download version
    3. Send out mail announcements to jikesrvm-announce and jikesrvm-researchers
    4. Also post announcement in SF news and Confluence news.

