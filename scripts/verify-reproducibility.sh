#!/bin/bash
# F-Droid Reproducible Builds Verification Script
# Following: https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/HOWTO:-diff-&-fix-APKs-for-Reproducible-Builds

UPSTREAM_APK="${1:-upstream-release.apk}"
FDROID_APK="${2:-fdroiddata-ci.apk}"
echo "Upstream: $UPSTREAM_APK"
echo "F-Droid:  $FDROID_APK"

fix-dexdump() {
	sed -i -r 's/^[0-9a-f]{6}: [ .0-9a-f]*\|(\[[0-9a-f]{6}\]|[0-9a-f]{4})?/|/' "$@"
	sed -i -r 's! // [a-z0-9_]+@[0-9a-f]+$!!' "$@"
	sed -i -r '/(interfaces_off|source_file_idx|annotations_off|class_data_off|superclass_idx|class_idx|method_idx|^Class #|^Annotations on|^ *0x[0-9a-f]{4} line=[0-9]+$)/d' "$@"
}
bdiff() {
  # use bat for coloured output
  diff -Naur "$@" | bat -p -l diff
}
diff2c() {
  # diff running 2 different commands on the same file
  cmd_a="$1" cmd_b="$2" file="$3"
  diff -Naur <( $cmd_a "$file" ) <( $cmd_b "$file" ) | bat -p -l diff
}
diff2f() {
  # diff running the same command on 2 different files
  cmd="$1" file_a="$2" file_b="$3"
  diff -Naur <( $cmd "$file_a" ) <( $cmd "$file_b" ) | bat -p -l diff
}


# if this prints OK and does not show an error, we're good :)
apksigcopier compare "$UPSTREAM_APK" --unsigned "$FDROID_APK" && echo OK

# it is expected for only upstream's signed APK to have v1 (JAR) signature files: META-INF/MANIFEST.MF, META-INF/*.SF, and META-INF/*.RSA (or .DSA/.EC).
diff2f 'repro-apk zipinfo -e' "$UPSTREAM_APK" "$FDROID_APK"

# If the ZIP contents are equal, you can diff the ZIP metadata using diff-zip-meta.py.
repro-apk diff-zip-meta "$UPSTREAM_APK" "$FDROID_APK"

rm -rf x y
unzip -q -o -d x "$UPSTREAM_APK"
unzip -q -o -d y "$FDROID_APK"
diff2f 'repro-apk dump-axml' x/AndroidManifest.xml y/AndroidManifest.xml

for res in x/res/*.xml; do
    diff2f 'repro-apk dump-axml' "$res" "${res/x/y}"
done

diff2f 'repro-apk dump-arsc' x/resources.arsc y/resources.arsc

for dex in x/*.dex; do
	base=$(basename "$dex")
	dexdump -a -d -f -h "$dex" > x/$base.dump
done

for dex in y/*.dex; do
	base=$(basename "$dex")
	dexdump -a -d -f -h "$dex" > y/$base.dump
done

# make the diff a lot smaller :)
fix-dexdump x/*.dex.dump y/*.dex.dump

for dex in x/*.dex.dump; do
    base=$(basename "$dex")
    bdiff "$dex" "y/$base"
done

diff -rq x/lib y/lib
diff -rq x/assets y/assets
