/*
 * Copyright 2008-2013 Red Hat, Inc, and individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.projectodd.polyglot.web;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WebApplicationMetaDataTest {
    @Test
    public void testContextPath() throws Exception {
        Map<String, String> paths = new HashMap<String, String>();

        paths.put(null, null);
        paths.put("", "/");
        paths.put("/", "/");
        paths.put("/something", "/something");
        paths.put("/something", "/something");
        paths.put("///something", "/something");
        paths.put("/something///", "/something");
        paths.put("something///", "/something");
        paths.put("something", "/something");
        paths.put("some/thing", "/some/thing");

        for (String path : paths.keySet()) {
            WebApplicationMetaData metaData = new WebApplicationMetaData();
            metaData.setContextPath(path);
            assertEquals(paths.get(path), metaData.getContextPath());
        }
    }

    @Test
    public void testUninitializedContextPath() throws Exception {
        assertEquals(null, new WebApplicationMetaData().getContextPath());
    }
}