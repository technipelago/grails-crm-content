/*
 * Copyright (c) 2017 Goran Ehrsson.
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
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils

import java.text.Normalizer

/**
 * Handy utils when working with GR8 CRM content.
 */
@CompileStatic
class CrmContentUtils {

    public static String encodeFilename(String filename) {
        filename != null ? URLEncoder.encode(filename, 'UTF-8') : null
    }

    public static String normalizeName(String name) {
        String result = name != null ? StringUtils.replaceChars(FilenameUtils.normalize(removeAccents(name)), '\\/|"\',:?&*<>', '---__.....()') : null
        // Replace repeated special character occurrences with a single character.
        // Also strip dot, comma and underscore from end of string.
        return result ? result.replaceAll(/([\.\-_])\1+/, '$1').replaceAll(/[\.\-_]$/, '') : null
    }

    public static String removeAccents(String text) {
        return text != null ? Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "") : null
    }
}
