/*
 * Copyright 2015. Qiao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.breezes.fxmanager.service.ftp;

import org.mockftpserver.core.command.Command;
import org.mockftpserver.core.command.ReplyCodes;
import org.mockftpserver.core.session.Session;
import org.mockftpserver.core.session.SessionKeys;
import org.mockftpserver.core.util.IoUtil;
import org.mockftpserver.fake.command.AbstractFakeCommandHandler;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystemEntry;
import org.mockftpserver.fake.filesystem.FileSystemException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import info.breezes.StreamUtils;


public class RetrCommandHandler extends AbstractFakeCommandHandler {

    protected void handle(Command command, Session session) {
        verifyLoggedIn(session);
        this.replyCodeForFileSystemException = ReplyCodes.READ_FILE_ERROR;

        String path = getRealPath(session, command.getRequiredParameter(0));
        FileSystemEntry entry = getFileSystem().getEntry(path);
        verifyFileSystemCondition(entry != null, path, "filesystem.doesNotExist");
        verifyFileSystemCondition(!entry.isDirectory(), path, "filesystem.isNotAFile");
        FileEntry fileEntry = (FileEntry) entry;

        // User must have read permission to the file
        verifyReadPermission(session, path);

        // User must have execute permission to the parent directory
        verifyExecutePermission(session, getFileSystem().getParent(path));

        sendReply(session, ReplyCodes.TRANSFER_DATA_INITIAL_OK);
        InputStream input = fileEntry.createInputStream();
        session.openDataConnection();
        byte[] bytes = new byte[10240];
        try {
            int c;
            while ((c = input.read(bytes)) != -1) {
                session.sendData(bytes, c);
            }
        } catch (IOException e) {
            LOG.error("Error reading from file [" + fileEntry.getPath() + "]", e);
            throw new FileSystemException(fileEntry.getPath(), null, e);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                LOG.error("Error closing InputStream for file [" + fileEntry.getPath() + "]", e);
            }
        }

//        if (isAsciiMode(session)) {
//            bytes = convertLfToCrLf(bytes);
//        }
        //session.sendData(bytes, bytes.length);
        session.closeDataConnection();
        sendReply(session, ReplyCodes.TRANSFER_DATA_FINAL_OK);
    }

    /**
     * Within the specified byte array, replace all LF (\n) that are NOT preceded by a CR (\r) into CRLF (\r\n).
     *
     * @param bytes - the bytes to be converted
     * @return the result of converting LF to CRLF
     */
    protected byte[] convertLfToCrLf(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        char lastChar = ' ';
        for (int i = 0; i < bytes.length; i++) {
            char ch = (char) bytes[i];
            if (ch == '\n' && lastChar != '\r') {
                out.write('\r');
                out.write('\n');
            } else {
                out.write(bytes[i]);
            }
            lastChar = ch;
        }
        return out.toByteArray();
    }

    private boolean isAsciiMode(Session session) {
        // Defaults to true
        return session.getAttribute(SessionKeys.ASCII_TYPE) != Boolean.FALSE;
    }
}
