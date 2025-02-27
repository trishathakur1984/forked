/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.xsom.parser;

import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.impl.parser.NGCCRuntimeEx;
import com.sun.xml.xsom.impl.parser.ParserContext;
import com.sun.xml.xsom.impl.parser.state.Schema;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses possibly multiple W3C XML Schema files and compose
 * them into one grammar.
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class XSOMParser {

    private EntityResolver entityResolver;
    private ErrorHandler userErrorHandler;

    private AnnotationParserFactory apFactory;

    private final ParserContext context;

    /**
     * Creates a new XSOMParser that uses the given SAXParserFactory
     * for creating new SAX parsers.
     * <p>
     * The caller needs to configure
     * it properly. Don't forget to call <code>setNamespaceAware(true)</code>
     * or you'll see some strange errors.
     */
    public XSOMParser(SAXParserFactory factory) {
        this(new JAXPParser(factory));
    }

    /**
     * Creates a new XSOMParser that reads XML Schema from non-standard
     * inputs.
     * <p>
     * By implementing the {@link XMLParser} interface, XML Schema
     * can be read from something other than XML.
     *
     * @param parser This parser will be called to parse XML Schema documents.
     */
    public XSOMParser(XMLParser parser) {
        context = new ParserContext(this, parser);
    }

    /**
     * Parses a new XML Schema document.
     *
     * <p>
     * When using this method, XSOM does not know the system ID of
     * this document, therefore, when this stream contains relative
     * references to other schemas, XSOM will fail to resolve them.
     * To specify an system ID with a stream, use {@link InputSource}
     */
    public void parse(InputStream is) throws SAXException {
        parse(new InputSource(is));
    }

    /**
     * Parses a new XML Schema document.
     *
     * <p>
     * When using this method, XSOM does not know the system ID of
     * this document, therefore, when this reader contains relative
     * references to other schemas, XSOM will fail to resolve them.
     * To specify an system ID with a reader, use {@link InputSource}
     */
    public void parse(Reader reader) throws SAXException {
        parse(new InputSource(reader));
    }

    /**
     * Parses a new XML Schema document.
     */
    public void parse(File schema) throws SAXException, IOException {
        parse(schema.toURI().toURL());
    }

    /**
     * Parses a new XML Schema document.
     */
    public void parse(URL url) throws SAXException {
        parse(url.toExternalForm());
    }

    /**
     * Parses a new XML Schema document.
     */
    public void parse(String systemId) throws SAXException {
        parse(new InputSource(systemId));
    }

    /**
     * Parses a new XML Schema document.
     *
     * <p>
     * Note that if the {@link InputSource} does not have a system ID,
     * XSOM will fail to resolve them.
     */
    public void parse(InputSource source) throws SAXException {
        context.parse(source);
    }


    /**
     * Gets the parser implemented as a ContentHandler.
     * <p>
     * One can feed XML Schema as SAX events to this interface to
     * parse a schema. To parse multiple schema files, feed multiple
     * sets of events.
     *
     * <p>
     * If you don't send a complete event sequence from a startDocument
     * event to an endDocument event, the state of XSOMParser can become
     * unstable. This sometimes happen when you encounter an error while
     * generating SAX events. Don't call the getResult method in that case.
     *
     * <p>
     * This way of reading XML Schema can be useful when XML Schema is
     * not available as a stand-alone XML document.
     * For example, one can feed XML Schema inside a WSDL document.
     */
    public ContentHandler getParserHandler() {
        NGCCRuntimeEx runtime = context.newNGCCRuntime();
        Schema s = new Schema(runtime, false, null);
        runtime.setRootHandler(s);
        return runtime;
    }

    /**
     * Gets the parsed result. Don't call this method until
     * you parse all the schemas.
     *
     * @return If there was any parse error, this method returns null.
     * To receive error information, specify your error handler
     * through the setErrorHandler method.
     * @throws SAXException This exception will never be thrown unless it is thrown
     *                      by an error handler.
     */
    public XSSchemaSet getResult() throws SAXException {
        return context.getResult();
    }

    /**
     * Gets the set of {@link SchemaDocument} that represents
     * parsed documents so far.
     *
     * @return can be empty but never null.
     */
    public Set<SchemaDocument> getDocuments() {
        return new HashSet<>(context.parsedDocuments.keySet());
    }

    public EntityResolver getEntityResolver() {
        return entityResolver;
    }

    /**
     * Set an entity resolver that is used to resolve things
     * like {@code <xsd:import>} and {@code <xsd:include>}.
     */
    public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public ErrorHandler getErrorHandler() {
        return userErrorHandler;
    }

    /**
     * Set an error handler that receives all the errors encountered
     * during the parsing.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.userErrorHandler = errorHandler;
    }

    /**
     * Sets the annotation parser.
     * <p>
     * Annotation parser can be used to parse application-specific
     * annotations inside a schema.
     *
     * <p>
     * For each annotation, new instance of this class will be
     * created and used to parse {@code <xs:annotation>}.
     */
    public void setAnnotationParser(final Class<? extends AnnotationParser> annParser) {
        setAnnotationParser(new AnnotationParserFactory() {
            public AnnotationParser create() {
                try {
                    return

                            annParser.getConstructor().newInstance();
                } catch (IllegalAccessException e) {
                    throw new IllegalAccessError(e.getMessage());
                } catch (ReflectiveOperationException e) {
                    throw new InstantiationError(e.getMessage());
                }
            }
        });
    }

    /**
     * Sets the annotation parser factory.
     *
     * <p>
     * The specified factory will be used to create AnnotationParsers.
     */
    public void setAnnotationParser(AnnotationParserFactory factory) {
        this.apFactory = factory;
    }

    public AnnotationParserFactory getAnnotationParserFactory() {
        return apFactory;
    }
}
