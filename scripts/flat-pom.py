#!/usr/bin/env python3
"""Writes a pom.xml listing every library on a Leiningen classpath as a direct dependency.

Usage: lein classpath | scripts/flat-pom.py > flat-pom.xml

Reads the classpath on stdin, keeps the jars under a Maven repository (.../repository/<group
path>/<artifact>/<version>/<artifact>-<version>[-<classifier>].jar) and prints a pom naming each
once, with Leiningen's resolved version. A scanner that cannot resolve Clojars libraries
(OSV-Scanner's resolver reads one registry) scans this with resolution off and still sees every
library the app ships. Directories and jars outside a Maven repository are skipped.
"""
import sys
from xml.sax.saxutils import escape


def coords(path):
    """Returns (group, artifact, version) for a jar path in a Maven repository, or None."""
    parts = path.split('/')
    if 'repository' not in parts or not path.endswith('.jar'):
        return None
    rest = parts[len(parts) - 1 - parts[::-1].index('repository') + 1:]
    if len(rest) < 4:
        return None
    group, artifact, version = '.'.join(rest[:-3]), rest[-3], rest[-2]
    return group, artifact, version


def main():
    """Prints the flat pom for the classpath on stdin."""
    seen = {}
    for entry in sys.stdin.read().strip().split(':'):
        c = coords(entry)
        if c:
            seen[(c[0], c[1])] = c[2]
    print('<project xmlns="http://maven.apache.org/POM/4.0.0">')
    print('  <modelVersion>4.0.0</modelVersion>')
    print('  <groupId>orcpub</groupId><artifactId>orcpub-flat</artifactId><version>0</version>')
    print('  <dependencies>')
    for (g, a), v in sorted(seen.items()):
        print('    <dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version></dependency>'
              % (escape(g), escape(a), escape(v)))
    print('  </dependencies>')
    print('</project>')


if __name__ == '__main__':
    main()
