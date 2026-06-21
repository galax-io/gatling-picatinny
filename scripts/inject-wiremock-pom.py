#!/usr/bin/env python3
"""Inject WireMock dependency before the last </dependencies> in a Maven pom.xml."""
import sys

project_dir = sys.argv[1]
pom_path = project_dir + "/pom.xml"

dep = (
    "\n    <dependency>"
    "\n      <groupId>org.wiremock</groupId>"
    "\n      <artifactId>wiremock</artifactId>"
    "\n      <version>3.13.2</version>"
    "\n      <scope>test</scope>"
    "\n    </dependency>"
)

pom = open(pom_path).read()
idx = pom.rfind("</dependencies>")
assert idx >= 0, "No </dependencies> block found in pom.xml"
open(pom_path, "w").write(pom[:idx] + dep + "\n  " + pom[idx:])
