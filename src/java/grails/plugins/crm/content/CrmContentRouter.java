/*
 * Copyright (c) 2016 Goran Ehrsson.
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

package grails.plugins.crm.content;

/**
 * A content router decides what content provider that should handle content.
 */
public interface CrmContentRouter {

    /**
     * Decide what content provider can handle a specific type of content.
     *
     * @param filename name of content
     * @param length content length in bytes
     * @param reference domain instance the content i attached to
     * @param username the user that wants to store this content
     * @return a content provider that accepts to store this type of content
     */
    CrmContentProvider getProvider(String filename, long length, Object reference, String username);
}
