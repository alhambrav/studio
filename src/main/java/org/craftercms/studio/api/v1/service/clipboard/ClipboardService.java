/*
 * Crafter Studio Web-content authoring solution
 * Copyright (C) 2007-2016 Crafter Software Corporation.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.craftercms.studio.api.v1.service.clipboard;

import org.craftercms.studio.api.v1.exception.ServiceException;

import javax.servlet.http.HttpSession;

import java.util.Set;
import java.util.HashSet;

/**
 * Clipboard service that tracks items clipped (COPY/CUT) by the caller and executes the 
 * proper content services (COPY TO DEST, MOVE TO DEST) on paste operation. 
 */
public interface ClipboardService {

    // RD: Remove session from this interface. 
    //     Either move clips the UI or move it to Spring
    //     Cannot move to spring at this time without Spring xml changes
    
    /**
     * cut a set of items and store on clipboard
     *
     * @param site - the project ID
     * @param path - path of item to be cut
     * @return true if item is properly placed on clipboard
     */
    boolean cut(String site, String path, HttpSession session) throws ServiceException;

    /**
     * copy a set of items and store on clipboard
     *
     * @param site - the project ID
     * @param path - path of item to be copied
     * @return true if item is properly placed on clipboard
     */
    boolean copy(String site, String path, HttpSession session) throws ServiceException;

    /**
     * copy a set of items and store on clipboard
     *
     * @param site - the project ID
     * @param clipItem - item to be copied
     * @return true if item is properly placed on clipboard
     */
    boolean copy(String site, ClipboardItem clipItem, HttpSession session) throws ServiceException;

    /**
     * paste a list of items provided to the specified destination
     *
     * @param site the project id
     * @param destination
     *          the root folder of all items' destination
     * @return a list of pasted items (new paths)
     * @throws org.craftercms.studio.api.v1.exception.ServiceException
     */
    Set<String> paste(String site, String destination, HttpSession session) throws ServiceException;

    /**
     * get the items on clipboard
     *
     * @param site - the project ID
     * @return clipped item or null
     */
    ClipboardItem getItems(String site, HttpSession session) throws ServiceException;

    ClipboardStore getClipboardStore(String site,  HttpSession session);

    /**
     * A ClipboardItem is a record for clip board opearation (CUT/COPY)
     * A clipboard contains a list of ops until a paste is called
     */
    class ClipboardItem {
        public ClipboardItem(String path, boolean cut) {
            this.isCut = cut;
            this.path = path;   
            this.children = new HashSet<ClipboardItem>();        
        };

        public boolean isCut = false;
        public boolean isDeep = true; // copy ops are not always deep, why is this hard coded? Suspect not used
        public String path = null;
        public Set<ClipboardItem> children;
    }

    /**
     * wrapper around a map used to store clipboard opearations
     */
    class ClipboardStore {

        public ClipboardStore() {
            this.op = null;
        };

        public void clear() {
            this.op = null;
        };

        public boolean addOp(ClipboardItem op) {
            this.op = op;
            return true;
        }

        public ClipboardItem getOps() {
            return this.op;
        };

        protected ClipboardItem op;
    }
}
