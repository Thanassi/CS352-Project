#!/usr/bin/env python

import os
import sys
import datetime
import emails.utils
import sets
import time

username = ""
password = ""
submit = False
contentLength = 0

request = os.environ.get("REQUEST_METHOD", "")

if request == "GET":
	sys.stdout.write("<html><body> <form method="post" action="store.cgi"> "
					 "Username: <input type="text" name="fname" required> "
					 "<br/><br/> Password: <input type=password "
					 "name="fpassword" required><br/><br/> "
					 "<input type="submit" name="Submit" value="Submit"> "
					 "<br/><br/></form></body></html")
					 
elif request == "POST:
	cookie = os.environ.get("HTTP_COOKIE", "")
	split = cookie.split(";")
	splitCookie = [tuple(split2.split("=")) for split2 in split]
	
	for sC in splitCookie:
		for name in sC:
			if name == "name" or name == " name":
				username = t[1]
	
	contentLength = int(os.environ.get("CONTENT_LENGTH, ""))
	content = sys.stdin.read(contentLength)
	
	if username == "":
		split3 = content.split("&")
		splitContent = [tuple(split4.split("=")) for split4 in split3]
		
		for sC in splitContent:
			if sC[0] == "fname":
				username = sC[1]
			elif sC[0] == "fpassword":
				password = sC[1]
			elif sC[0] == "Submit":
				submit = True
		
		if(username == "" or password == "" or submit == False):
			sys.stdout.write("Set-Cookie: cart=\r\n\r\n")
			sys.stdout.write("<html><body> <form method=\"post\" action=\"store.cgi\"> "
					 "Username: <input type=\"text\" name=\"fname\" required> "
					 "<br/><br/> Password: <input type=password "
					 "name=\"fpassword\" required><br/><br/> "
					 "<input type=\"submit\" name=\"Submit\" value=\"Submit\"> "
					 "<br/><br/></form></body></html")
			sys.exit()
		
		expireTime = datetime.now() + timedelta(days=1)
		time = mktime(expireTime.timetuple())
		expires = formatdate(timeval=time, localtime=True, usegmt=True)
		expires=expires[:expires.find("-")]
		expires=expires + "EDT"
		
		sys.stdout.write("Set-Cookie: name=" + username + 
						 ";Expires=" + expires + "\r\n" + 
						 "Set-Cookie: cart=\r\n\r\n" + 
						 "<html><body><h1> " + username +
						 "</h1><form method=\"post\" action=\"store.cgi\"> "
						 "<select name=\"furniture\"> "
						 "<option value=\"bed\">Bed: cost=2000</option> "
						 "<option value=\"desk\">Desk: cost=500</option> "
						 "<option value=\"couch\">Bed: cost=800</option> "
						 "</select><input type=\"submit\" name=\"add\" value=\"Add To Cart\"> "
						 "<br/></form></body></html>")
		
		