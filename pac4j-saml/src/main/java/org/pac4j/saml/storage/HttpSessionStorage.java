package org.pac4j.saml.storage;

import org.opensaml.core.xml.XMLObject;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.util.CommonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;

/**
 * Class implements storage of SAML messages and uses HttpSession as underlying dataStore. As the XMLObjects
 * can't be serialized (which could lead to problems during failover), the messages are transformed into SAMLObject
 * which internally marshalls the content into XML during serialization.
 *
 * Messages are populated to a Hashtable and stored inside HttpSession. The Hashtable is lazily initialized
 * during first attempt to create or retrieve a message.
 *
 * @author Vladimir Schäfer
 */
public class HttpSessionStorage implements SAMLMessageStorage {

    /**
     * Class logger.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The web context to storage data.
     */
    private final WebContext context;

    /**
     * Internal storage for messages, corresponding to the object in session.
     */
    private Hashtable<String, XMLObject> internalMessages;

    /**
     * Session key for storage of the hashtable.
     */
    private static final String SAML_STORAGE_KEY = "_springSamlStorageKey";

    /**
     * Creates the storage object. The session is manipulated only once caller tries to store
     * or retrieve a message.
     *
     * In case request doesn't already have a started session, it will be created.
     *
     * @param context context to load/store internalMessages from
     */
    public HttpSessionStorage(final WebContext context) {
        CommonHelper.assertNotNull("context", context);
        this.context = context;
    }


    /**
     * Stores a request message into the repository. RequestAbstractType must have an ID
     * set. Any previous message with the same ID will be overwritten.
     *
     * @param messageID ID of message
     * @param message   message to be stored
     */
    @Override
    public void storeMessage(final String messageID, final XMLObject message) {
        log.debug("Storing message {} to session {}", messageID, context.getSessionIdentifier());
        final Hashtable<String, XMLObject> messages = getMessages();
        messages.put(messageID, message);
        updateSession(messages);
    }

    /**
     * Returns previously stored message with the given ID or null, if there is no message
     * stored.
     * <p>
     * Message is stored in String format and must be unmarshalled into XMLObject. Call to this
     * method may thus be expensive.
     * <p>
     * Messages are automatically cleared upon successful reception, as we presume that there
     * are never multiple ongoing SAML exchanges for the same session. This saves memory used by
     * the session.
     *
     * @param messageID ID of message to retrieve
     * @return message found or null
     */
    @Override
    public XMLObject retrieveMessage(final String messageID) {
        final Hashtable<String, XMLObject> messages = getMessages();
        final XMLObject o = messages.get(messageID);
        if (o == null) {
            log.debug("Message {} not found in session {}", messageID, context.getSessionIdentifier());
            return null;
        }

        log.debug("Message {} found in session {}, clearing", messageID, context.getSessionIdentifier());
        messages.clear();
        updateSession(messages);
        return o;

    }

    /**
     * Provides message storage hashtable. Table is lazily initialized when user tries to store or retrieve
     * the first message.
     *
     * @return message storage
     */
    private Hashtable<String, XMLObject> getMessages() {
        if (internalMessages == null) {
            internalMessages = initializeSession();
        }
        return internalMessages;
    }

    /**
     * Call to the method tries to load internalMessages hashtable object from the session, if the object doesn't exist
     * it will be created and stored.
     * <p>
     * Method synchronizes on session object to prevent two threads from overwriting each others hashtable.
     */
    @SuppressWarnings("unchecked")
    private Hashtable<String, XMLObject> initializeSession() {
        Hashtable<String, XMLObject> messages = (Hashtable<String, XMLObject>)
                context.getSessionAttribute(SAML_STORAGE_KEY);
        if (messages == null) {
            synchronized (context) {
                messages = (Hashtable<String, XMLObject>) context.getSessionAttribute(SAML_STORAGE_KEY);
                if (messages == null) {
                    messages = new Hashtable<>();
                    updateSession(messages);
                }
            }
        }
        return messages;
    }

    /**
     * Updates session with the internalMessages key. Some application servers require session value to be updated
     * in order to replicate the session across nodes or persist it correctly.
     */
    private void updateSession(final Hashtable<String, XMLObject> messages) {
        context.setSessionAttribute(SAML_STORAGE_KEY, messages);
    }
}
