package net.citotech.cito.financialmessage;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** Secure XML boundary for ISO 20022 adapter payloads. */
public final class Iso20022XmlValidator {

    private Iso20022XmlValidator() {}

    /**
     * Parses an ISO 20022 XML payload with external entities and DTDs disabled.
     *
     * <p>This verifies XML safety/well-formedness only. Semantic ISO 20022 conformance requires the
     * exact registered message definition and counterparty usage guideline.
     */
    public static Document parseSecure(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        try {
            DocumentBuilderFactory factory = secureFactory();
            return factory
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid or unsafe ISO 20022 XML payload", e);
        }
    }

    /**
     * Validates XML against a caller-supplied, controlled schema after applying secure parser rules.
     */
    public static Document validate(byte[] xml, Schema schema) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(xml, "xml");
        try {
            DocumentBuilderFactory factory = secureFactory();
            factory.setSchema(schema);
            return factory
                    .newDocumentBuilder()
                    .parse(new InputSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("ISO 20022 XML failed the configured schema/profile validation", e);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
