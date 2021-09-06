#
# Copyright @ 2015 Atlassian Pty Ltd
# Copyright @ 2021 Raptor Engineering, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CC=gcc

JNI_HEADERS = -I"$(JAVA_HOME)/include" -I"$(JAVA_HOME)/include/linux"
SCREENCAPTURE_HEADERS = -I"/usr/include"

CFLAGS = -Wall -Werror -std=c99 $(JNI_HEADERS) $(SCREENCAPTURE_HEADERS)

OBJS = org_jitsi_impl_neomedia_imgstreaming_ScreenCapture.c

LIBS = -lpthread

libjnscreencapture.so: clean
	$(CC) $(CFLAGS) -fPIC -shared $(OBJS) -o libjnscreencapture.so $(LIBS)

install32: libjnscreencapture.so
	cp libjnscreencapture.so "../../../lib/native/linux-x86/libjnscreencapture.so"
	cp libjnscreencapture.so "../../../../jitsi-videobridge/lib/native/linux/libjnscreencapture.so"

install64: libjnscreencapture.so
	cp libjnscreencapture.so "../../../lib/native/linux-x86-64/libjnscreencapture.so"
	cp libjnscreencapture.so "../../../../jitsi-videobridge/lib/native/linux-64/libjnscreencapture.so"

installppc64: libjnscreencapture.so
	cp libjnscreencapture.so "../../../lib/native/linux-ppc64le/libjnscreencapture.so"
	cp libjnscreencapture.so "../../../../jitsi-videobridge/lib/native/linux-ppc64le/libjnscreencapture.so"

clean:
	rm -f *.so
