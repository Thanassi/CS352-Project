#!/usr/bin/env python

import os
import sys

request = os.environ.get("REQUEST_METHOD", "")

if request == "GET":
	sys.stdout.write("<html><body><form method=\"post\" action=\"service.cgi\"> "
					 "<input type=\"file\" name=\"Browse\" value=\"Browse\"> "
					 "<br/><br/> MD5: <input type =\"text\" name=\"md5\" required> "
					 "<br/><br/> <input type=\"submit\" name=\"Submit\" value=\"Submit\"> "
					 "<br/><br/></form></body></html>")