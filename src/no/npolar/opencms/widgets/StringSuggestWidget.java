package no.npolar.opencms.widgets;

import com.google.common.xml.XmlEscapers;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.types.CmsResourceTypePlain;
import org.opencms.i18n.CmsMessages;
import org.opencms.json.JSONObject;
import org.opencms.main.OpenCms;
import org.opencms.widgets.I_CmsWidget;
import org.opencms.xml.types.A_CmsXmlContentValue;

/**
 * Backing class for a custom configurable widget that will suggest strings as
 * the user types.
 * <p>
 * The widget fetches suggestions by querying a web service, which must respond 
 * in JSONP. The suggestions source is easily swappable, by changing the widget 
 * configuration - different sources can be "tailored" to fit each 
 * implementation of the widget.
 * <p>
 * For more info, see the widget's associated javascript (string_suggest_widget.js).
 * <p>
 * Created with much help from the OpenCms community.
 * 
 * @author Paul-Inge Flakstad, Norwegian Polar Institute
 */
public class StringSuggestWidget extends ConfigurableWidget {
    /** The logger. */
    private static final Log LOG = LogFactory.getLog(StringSuggestWidget.class);

    /** Paths to javascript files to be included for the widget (relative to {@link ConfigurableWidget#PATH_RESOURCES}). */
    private static final String[] PATHS_JS_RESOURCES = {
        "//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min.js",
        "//ajax.googleapis.com/ajax/libs/jqueryui/1.9.1/jquery-ui.min.js",
        "js/underscore-min.js",
        "js/custom-functions.js",
        "js/string-suggest-widget-helpers.js",
        "js/string-suggest-widget.js"
    };

    /** Paths to CSS files to be included for the widget (relative to {@link ConfigurableWidget#PATH_RESOURCES}). */
    private static final String[] PATHS_CSS_RESOURCES = {
        "/system/modules/no.npolar.common.jquery/resources/jquery.jqueryui.autocomplete.css",
        "css/string-suggest-widget.css"
    };

    /** The name of the javascript method that is called to initialize instances of this widget. */
    private static final String JS_INIT_METHOD = "initStringSuggestWidget";
    
    /** The configuration key for the URI property inside widget template objects. */
    protected static final String CONF_KEY_TEMPLATE_URI = "uri";
    /** The configuration key for the widget's template for suggestions. */
    protected static final String CONF_KEY_TEMPLATE_SUGGESTION = "tpl_suggestion";
    /** The configuration key for the widget's template for the info box. */
    protected static final String CONF_KEY_TEMPLATE_INFO = "tpl_info";

    /**
     * Default constructor, necessary for registering the class.
     */
    public StringSuggestWidget() {
        this("");
    }

    /**
     * Creates a new widget with the given configuration.
     *
     * @param configuration The configuration string (from the <code>configuration</code> attribute of the XSD file where the widget is employed).
     */
    public StringSuggestWidget(String configuration) {
        super(configuration);
    }
    
    /**
     * Returns the configuration string that is passed to the javascript widget.
     *
     * @param cms The current OpenCms object.
     * @param schemaType The schema type.
     * @param messages The messages.
     * @param resource The edited resource.
     * @param contentLocale The content locale.
     * @return A configuration string that is passed to the javascript widget.
     */
    @Override
    public String getConfiguration(
                    CmsObject cms,
                    A_CmsXmlContentValue schemaType,
                    CmsMessages messages,
                    CmsResource resource,
                    Locale contentLocale) {
        try {
            if (jsonConf == null) {
                throw new NullPointerException("Widget configuration is either missing or not parseable as JSON.");
            }

            // 1: Handle config file reference
            // (NOTE: Has to be the first step, as this will overwrite the current configuration object.)
            if (jsonConf.has(CONF_KEY_CONFIG_FILE_URI))
                resolveConfigFile(cms);

            // 2: Add all the mandatory configuration settings
            addMandatoryConfig(cms, schemaType, messages, resource, contentLocale);

                // Begin special handling for this child class
                    // 3: Handle widget templates defined as URIs
                    resolveTemplates(cms);
                // End special 

            // 4: Resolve any dynamic values, and return the configuration string
            return resolveDynamicValues(jsonConf.toString(), cms, resource, contentLocale);
        } catch (Exception e) {
            if (LOG.isErrorEnabled())
                LOG.error("Error in widget configuration: " + e.getMessage());
            return "Configuration error: " + e.getMessage();
        }
    }
    
    protected void resolveTemplates(CmsObject cms) {
        try {
            String[] templateConfigs = { 
                CONF_KEY_TEMPLATE_SUGGESTION, 
                CONF_KEY_TEMPLATE_INFO 
            };
            for (int i = 0; i < templateConfigs.length; i++) {
                // Substitute widget template URIs with the contents of the file at that URI:
                //  tpl_xxx: { uri:'/path/to/vfs-file' } => tpl_xxx : 'XML-escaped contents of /path/to/vfs-file'
                substituteTemplateUri(jsonConf, cms, templateConfigs[i]);
                //c = substituteTemplateUri(c, cms, templateConfigs[i]);
            }
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error processing template(s) in widget configuration.", e);
            }
        }
    }
    
    /**
     * @see StringSuggestWidget#substituteTemplateUri(org.opencms.json.JSONObject, org.opencms.file.CmsObject, java.lang.String) 
     */
    /*public JSONObject substituteTemplateUri(CmsObject cms, String templateIdentifier) {
        return substituteTemplateUri(jsonConf, cms, templateIdentifier);
    }*/
    
    /** 
     * Substitutes the URI property inside a widget template object with a
     * String that is the contents from the VFS file identified by that URI.
     * <p>
     * Widget templates may be defined directly inside the configuration (as a 
     * String), e.g.:
     * <p>
     * <code>tpl_info:'&amp;lt;a&amp;gt;%(title)&amp;lt;/a&amp;gt;'</code>
     * <p>
     * Or they may be defined using a <code>uri</code> reference (as a 
     * nested property object):
     * <p>
     * <code>tpl_info: { uri: '/vfs/path/to/template.tpl' }</code>
     * <p>
     * This method will return the configuration object unmodified, unless the 
     * widget template has a <code>uri</code> set.
     * <p>
     * If set, that URI should point to a VFS text file where the template is 
     * defined. The content of this VFS file is then parsed into an XML-escaped 
     * String, injected into the configuration object, replacing the original
     * configuration property identified by the given template identifier.
     * 
     * @param configuration The configuration object.
     * @param cms An initialized CmsObject.
     * @param templateIdentifier Property identifier for the template, e.g. <code>{@link #CONF_KEY_TEMPLATE_SUGGESTION}</code> or <code>{@link #CONF_KEY_TEMPLATE_INFO}</code>.
     * @return The (potentially) updated configuration object, with a valid template configuration.
     */
    public JSONObject substituteTemplateUri(JSONObject configuration, CmsObject cms, String templateIdentifier) {
        
        try {
            // Get the object identified by the given identifier (e.g. "tpl_xxx")
            Object tplObject = configuration.get(templateIdentifier);
            // Proceed only if that object is of type JSONObject (not String)
            if (tplObject instanceof JSONObject) {
                // We expect the object to contain a URI – get it
                String tplUri = ((JSONObject)tplObject).getString(CONF_KEY_TEMPLATE_URI);
                // Clean up the URI (getResourceLink() may add stuff like leading "/opencms/opencms", getRootPath() removes that)
                tplUri = OpenCms.getLinkManager().getRootPath(cms, getResourceLink(cms, tplUri));
                
                // Proceed only if there is a "plain" resource at the given URI
                if (cms.existsResource(tplUri) 
                        && cms.readResource(tplUri).getTypeId() == CmsResourceTypePlain.getStaticTypeId()) {

                    // Read the file contents into a string
                    String tplContent = new String(cms.readFile(tplUri).getContents(), OpenCms.getSystemInfo().getDefaultEncoding());
                    // XML-escape the string
                    String tplContentEscaped = XmlEscapers.xmlAttributeEscaper().escape(tplContent);

                    // Create a substitute JSON object, where the nested object is replaced by the XML-escaped string
                    String tplObjStr = "{ " + templateIdentifier + ": '" + tplContentEscaped + "'}";
                    // Do the substitution:
                    // Merge this object into the configuration, overwriting the existing template property
                    configuration.merge(new JSONObject(tplObjStr), true, false);
                } else {
                    throw new NullPointerException("The configuration references a template file at '" + tplUri + "', but no such file exists.");
                }
            }
        } catch (Exception e) {
            if (LOG.isErrorEnabled())
                LOG.error("Unable to insert template from URI found in widget configuration: " + e.getMessage(), e);
        }
        
        // Return the (potentially) upated configuration object
        return configuration;
    }

    /**
     * Returns the widget name and identifier. This is always the name of this
     * class, and it is also used in the client-side javascript.
     * 
     * @return The widget name and identifier (StringSuggestWidget).
     */
    @Override
    public String getWidgetName() {
        return StringSuggestWidget.class.getName(); // "StringSuggestWidget"
    }

    /**
     * Returns the name of the javascript function that is called to initialize 
     * this widget.
     * 
     * @return The name of the javascript function that is called to initialize this widget.
     * @see #JS_INIT_METHOD
     */
    @Override
    public String getInitCall() {
        // Return the name of the javascript function that initializes the widget
        return JS_INIT_METHOD;
    }

    /**
     * @see ConfigurableWidget#getCssPaths() 
     */
    @Override
    public String[] getCssPaths() {
        return PATHS_CSS_RESOURCES;
    }

    /**
     * @see ConfigurableWidget#getJsPaths() 
     */
    @Override
    public String[] getJsPaths() {
        return PATHS_JS_RESOURCES;
    }

    /**
     * Creates a new instance of this widget.
     * 
     * @see I_CmsWidget#newInstance() 
     */
    @Override
    public I_CmsWidget newInstance() {
        return new StringSuggestWidget(getConfiguration());
    }
}