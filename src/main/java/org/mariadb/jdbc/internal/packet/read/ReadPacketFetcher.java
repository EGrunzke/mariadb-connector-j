/*
MariaDB Client for Java

Copyright (c) 2012 Monty Program Ab.

This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the Free
Software Foundation; either version 2.1 of the License, or (at your option)
any later version.

This library is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License along
with this library; if not, write to Monty Program Ab info@montyprogram.com.

This particular MariaDB Client for Java file is work
derived from a Drizzle-JDBC. Drizzle-JDBC file which is covered by subject to
the following copyright and notice provisions:

Copyright (c) 2009-2011, Marcus Eriksson, Trond Norbye

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the driver nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/


package org.mariadb.jdbc.internal.packet.read;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ReadPacketFetcher {
    public static final int AVOID_CREATE_BUFFER_LENGTH = 1024;
    private final InputStream inputStream;
    private byte[] headerBuffer = new byte[4];
    private byte[] reusableBuffer = new byte[AVOID_CREATE_BUFFER_LENGTH];

    public ReadPacketFetcher(final InputStream is) {
        this.inputStream = is;
    }

    public RawPacket getRawPacket() throws IOException {
        return RawPacket.nextPacket(inputStream);
    }

    /**
     * Get buffer without stream sequence information.
     *
     * @return ByteBuffer the bytebuffer
     * @throws IOException if any
     */
    public ByteBuffer getReusableBuffer() throws IOException {
        int remaining = 4;
        int off = 0;
        do {
            int count = inputStream.read(reusableBuffer, off, remaining);
            if (count <= 0) {
                throw new EOFException("unexpected end of stream, read " + (4 - remaining) + " bytes from " + 4);
            }
            remaining -= count;
            off += count;
        } while (remaining > 0);

        int length = (reusableBuffer[0] & 0xff) + ((reusableBuffer[1] & 0xff) << 8) + ((reusableBuffer[2] & 0xff) << 16);

        byte[] rawBytes;
        if (length < ReadPacketFetcher.AVOID_CREATE_BUFFER_LENGTH) {
            rawBytes = reusableBuffer;
        } else {
            rawBytes = new byte[length];
        }
        remaining = length;
        off = 0;
        do {
            int count = inputStream.read(rawBytes, off, remaining);
            if (count <= 0) {
                throw new EOFException("unexpected end of stream, read " + (length - remaining) + " bytes from " + length);
            }
            remaining -= count;
            off += count;
        } while (remaining > 0);

        return ByteBuffer.wrap(rawBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Skip next stream.
     * @throws IOException if connection errors occur.
     */
    public void skipNextPacket() throws IOException {
        int remaining = 4;
        int off = 0;
        do {
            int count = inputStream.read(headerBuffer, off, remaining);
            if (count <= 0) {
                throw new EOFException("unexpected end of stream, read " + (4 - remaining) + " bytes from " + 4);
            }
            remaining -= count;
            off += count;
        } while (remaining > 0);

        long length = (headerBuffer[0] & 0xff) + ((headerBuffer[1] & 0xff) << 8) + ((headerBuffer[2] & 0xff) << 16);

        while (length > 0) {
            length -= inputStream.skip(length);
        }
    }

    public RawPacket getReusableRawPacket() throws IOException {
        return RawPacket.nextReusablePacket(inputStream, headerBuffer, reusableBuffer);
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
