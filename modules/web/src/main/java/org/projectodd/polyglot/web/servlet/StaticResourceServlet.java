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

/*
 * A large part of the code in this file was borrowed from
 * org.apache.catalina.servlets.DefaultServlet in JBossWeb- it's original
 * license is below
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectodd.polyglot.web.servlet;

import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.util.ETag;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.StringTokenizer;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StaticResourceServlet extends HttpServlet {
    
    private static final long serialVersionUID = 7173759925797350928L;

    private String resourceRoot;
    private String cacheDirectory;
    private String cacheExtension;
    private ResourceManager resourceManager;


    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 0;


    /**
     * Finalize this servlet.
     */
    public void destroy() {
    }

    /**
     * Initialize this servlet.
     */
    public void init() throws ServletException {

        if (getServletConfig().getInitParameter("debug") != null)
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));

        this.resourceRoot = getServletConfig().getInitParameter( "resource.root" );
        this.cacheDirectory = getServletConfig().getInitParameter( "cache.directory" );
        this.cacheExtension = getServletConfig().getInitParameter( "cache.extension" );
        this.resourceManager = new FileResourceManager(Paths.get(this.resourceRoot));

    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     */
    protected String getRelativePath(HttpServletRequest request) {

        // Are we being processed by a RequestDispatcher.include()?
        if (request.getDispatcherType() == DispatcherType.INCLUDE && request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            String result = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_PATH_INFO);
            if (result == null)
                result = (String) request.getAttribute(
                        RequestDispatcher.INCLUDE_SERVLET_PATH);
            if ((result == null) || (result.equals("")))
                result = "/";
            return (result);
        }

        // No, extract the desired path directly from the request
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return (result);

    }

    protected String getPageCachedRelativePath(HttpServletRequest request) throws IOException {
        // First look for static files
        String path = resourceRoot + getRelativePath(request);
        Resource resource = resourceManager.getResource(path);

        if (resource != null) {
            if (resource.isDirectory()) {
                if (path.endsWith( "/" )) {
                    path = path + "index.html";
                } else {
                    path = path + "/index.html";
                }
            }
        } else if (cacheDirectory != null) {
            // If page caching is enabled and a static file wasn't found,
            // look in the page cache
            path = cacheDirectory + request.getContextPath() + getRelativePath(request);
            resource = resourceManager.getResource(path);
            if (resource == null) {
                // no page cache found - try appending the cache extension to the path
                if (path.endsWith( "/" )) {
                    path = path.substring( 0, path.length() - 1 );
                }
                path = path + this.cacheExtension;
            }
        }
        return path;
    }


    /**
     * Process a GET request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response)
                    throws IOException, ServletException {

        // Serve the requested resource, including the data content
        serveResource(request, response, true);

    }


    /**
     * Process a HEAD request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doHead(HttpServletRequest request,
            HttpServletResponse response)
                    throws IOException, ServletException {

        // Serve the requested resource, without the data content
        serveResource(request, response, false);

    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response)
                    throws IOException, ServletException {
        doGet(request, response);
    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setAttribute( "polyglot.servlet", true );
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;

    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setAttribute( "polyglot.servlet", true );
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;

    }


    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true if the resource meets all the specified conditions,
     * and false if any of the conditions is not satisfied, in which case
     * request processing is stopped
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource)
                    throws IOException {

        return checkIfMatch(request, response, resource)
                && checkIfModifiedSince(request, response, resource)
                && checkIfNoneMatch(request, response, resource)
                && checkIfUnmodifiedSince(request, response, resource);

    }


    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
            HttpServletResponse response,
            boolean content)
                    throws IOException, ServletException {

        request.setAttribute( "polyglot.servlet", true );

        // Identify the requested resource path
        String path = getPageCachedRelativePath(request);
        if (debug > 0) {
            if (content)
                log("DefaultServlet.serveResource:  Serving resource '" +
                        path + "' headers and data");
            else
                log("DefaultServlet.serveResource:  Serving resource '" +
                        path + "' headers only");
        }

        Resource resource = resourceManager.getResource(path);

        if (resource == null) {
            // Check if we're included so we can return the appropriate 
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included, and the response.sendError() below is going
                // to be ignored by the resource that is including us.
                // Therefore, throw an exception to notify the error.
                throw new FileNotFoundException(path);
            }

            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    requestUri);
            return;
        }

        // If the resource is not a collection, and the resource path
        // ends with "/" or "\", return NOT FOUND
        if (!resource.isDirectory()) {
            if (path.endsWith("/") || (path.endsWith("\\"))) {
                // Check if we're included so we can return the appropriate 
                // missing resource name in the error
                String requestUri = (String) request.getAttribute(
                        RequestDispatcher.INCLUDE_REQUEST_URI);
                if (requestUri == null) {
                    requestUri = request.getRequestURI();
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        requestUri);
                return;
            }
        }

        // Check if the conditions specified in the optional If headers are
        // satisfied.
        if (!resource.isDirectory()) {

            // Checking If headers
            boolean included =
                    (request.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH) != null);
            if (!included
                    && !checkIfHeaders(request, response, resource)) {
                return;
            }

        }

        // Find content type.
        String contentType = getServletContext().getMimeType(resource.getName());

        long contentLength = -1L;

        if (resource.isDirectory()) {
            // Skip directory listings
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    request.getRequestURI());
            return;
        } else {
            // ETag header
            if (resource.getETag() != null) {
                response.setHeader("ETag", resource.getETag().toString());
            }

            // Last-Modified header
            response.setHeader("Last-Modified", resource.getLastModifiedString());

            // Get content length
            contentLength = resource.getContentLength();
            // Special case for zero length files, which would cause a
            // (silent) ISE when setting the output buffer size
            if (contentLength == 0L) {
                content = false;
            }

        }

        // Set the appropriate output headers
        if (contentType != null) {
            if (debug > 0)
                log("DefaultServlet.serveFile:  contentType='" +
                        contentType + "'");
            response.setContentType(contentType);
        }
        if ((!resource.isDirectory()) && (contentLength >= 0)) {
            if (debug > 0)
                log("DefaultServlet.serveFile:  contentLength=" +
                        contentLength);
            if (contentLength < Integer.MAX_VALUE) {
                response.setContentLength((int) contentLength);
            } else {
                // Set the content-length as String to be able to use a long
                response.setHeader("content-length", "" + contentLength);
            }
        }

        resource.serve(HttpServletRequestImpl.getRequestImpl(request).getExchange());

    }


    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfMatch(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource)
                    throws IOException {

        ETag etag = resource.getETag();
        String headerValue = request.getHeader("If-Match");
        if (etag != null && headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                        (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(etag.toString()))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                    (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;

    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfModifiedSince(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource)
                    throws IOException {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resource.getLastModified().getTime();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                        && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    if (resource.getETag() != null) {
                        response.setHeader("ETag", resource.getETag().toString());
                    }

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfNoneMatch(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource)
                    throws IOException {

        ETag etag = resource.getETag();
        String headerValue = request.getHeader("If-None-Match");
        if (etag != null && headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                        new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(etag.toString()))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.getMethod()))
                        || ("HEAD".equals(request.getMethod())) ) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", etag.toString());

                    return false;
                } else {
                    response.sendError
                    (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;

    }


    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true if the resource meets the specified condition,
     * and false if the condition is not satisfied, in which case request
     * processing is stopped
     */
    protected boolean checkIfUnmodifiedSince(HttpServletRequest request,
            HttpServletResponse response,
            Resource resource)
                    throws IOException {
        try {
            long lastModified = resource.getLastModified().getTime();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if ( lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }

}
