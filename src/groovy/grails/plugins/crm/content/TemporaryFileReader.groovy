/*
 * Copyright (c) 2014 Goran Ehrsson.
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

package grails.plugins.crm.content

import groovy.transform.CompileStatic

/**
 * A Reader that deletes the input file when finished.
 */
@CompileStatic
class TemporaryFileReader extends Reader {

    private File file
    private InputStreamReader reader

    TemporaryFileReader(final File file) {
        reader = new InputStreamReader(file.newInputStream())
        this.file = file
    }

    TemporaryFileReader(final File file, final String charsetName) {
        reader = new InputStreamReader(file.newInputStream(), charsetName)
        this.file = file
    }

    @Override
    int read(final char[] chars, final int i, final int i1) throws IOException {
        reader.read(chars, i, i1)
    }

    @Override
    void close() throws IOException {
        try {
            reader.close()
        } catch(IOException e) {
            // Ignore.
        }
        file.delete()
    }
}
