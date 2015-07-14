package no.npolar.opencms.widgets;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.types.CmsResourceTypePlain;
import org.opencms.i18n.CmsMessages;
import org.opencms.json.JSONObject;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsFileUtil;
import org.opencms.widgets.A_CmsWidget;
import org.opencms.widgets.I_CmsADEWidget;
import org.opencms.widgets.I_CmsWidgetDialog;
import org.opencms.widgets.I_CmsWidgetParameter;
import org.opencms.xml.content.I_CmsXmlContentHandler.DisplayType;
import org.opencms.xml.types.A_CmsXmlContentValue;

/**
 * Generic base class for custom configurable widgets.
 * <p>
 * This implementation was created with help from the OpenCms community.
 * 
 * @author Paul-Inge Flakstad, Norwegian Polar Institute
 */
public abstract class ConfigurableWidget extends A_CmsWidget implements I_CmsADEWidget {
    /** The logger. */
    private static final Log LOG = LogFactory.getLog(ConfigurableWidget.class);
    
    /** Stores the widget's configuration, as defined in the <code>configuration</code> attribute of the XSD file where the widget is employed. */
    protected JSONObject jsonConf = null;

    /** Base path for this module's resources folder (which contains javascript, CSS, etc.). */
    protected static final String PATH_MODULE_RESOURCES_FOLDER = "/system/modules/no.npolar.opencms.widgets/resources/";
    
    /** The configuration key for the widget name. Any widget of this class automatically gets a name added to its configuration. See {@link #addName(org.opencms.json.JSONObject, org.opencms.xml.types.A_CmsXmlContentValue)}. */
    protected static final String CONF_KEY_NAME = "name";
    
    /** The configuration key for the widget name. Any widget of this class automatically gets a name added to its configuration. See {@link #addName(org.opencms.json.JSONObject, org.opencms.xml.types.A_CmsXmlContentValue)}. */
    protected static final String CONF_KEY_LOCALE = "locale";
    
    /** The dynamic <code>locale</code> value key; will be replaced by the actual current content locale, e.g. "en" or "no". */
    protected static final String DYNAMIC_VAL_LOCALE = "__CONTENT_LOCALE";
    
    /** The dynamic <code>self</code> value key; will be replaced by the root path of the resource being edited. */
    protected static final String DYNAMIC_VAL_SELF = "__SELF";
    
    /** Regex pattern that matches the beginning of any dynamic value, e.g. "__PROP", "__NOW", etc. Used to extract prefixes the respective dynamic value regex patterns. */
    protected static final String DYNAMIC_VAL_PREFIX_REGEX = "^_{2}[A-Z_]+";
    
    /** The dynamic <code>property</code> value prefix; will be replaced by the value of the specified property, e.g. __PROP[locale], as read from the resource being edited. */
    protected static final String DYNAMIC_VAL_PREFIX_PROPERTY = "__PROP";
    
    /** Regex pattern to match a dynamic property value notation. */
    protected static final String DYNAMIC_VAL_PROPERTY_REGEX = DYNAMIC_VAL_PREFIX_PROPERTY + "\\[[\\w\\.\\-]+\\]"; // \w = A-Za-z_0-9
    
    /** The dynamic <code>now</code> (current time) value prefix; will be replaced by a string representing the current time, using the specified date format, e.g. __NOW[d MMM yyyy]. */
    protected static final String DYNAMIC_VAL_PREFIX_CURRENT_TIME = "__NOW";
    
    /** Regex pattern to match a dynamic "current time" value notation. */
    protected static final String DYNAMIC_VAL_CURRENT_TIME_REGEX = DYNAMIC_VAL_PREFIX_CURRENT_TIME + "\\[[\\w\\s\\Q<>[]\\+-.!?\\E]+\\]"; // \w = A-Za-z_0-9
    
    /* The configuration key for the widget configuration's "config file uri". Used temporarily, before the config file is read and its content parsed into the configuration object. */
    protected static final String CONF_KEY_CONFIG_FILE_URI = "conf_uri";


    /**
     * Creates a new widget with the given configuration.
     *
     * @param configuration The configuration string, passed from the <code>configuration</code> attribute of the XSD file where the widget is employed.
     */
    public ConfigurableWidget(String configuration) {
        super(configuration); // The super class A_CmsWidget just calls setConfiguration(String)
        
        if (configuration == null) { // We require a configuration string
            if (LOG.isErrorEnabled()) {
                // Next line commented out because a lot of these were present in logs, even though everything was working just fine.
                // Not investigated further – suspect "dummy" instances are being created by OpenCms for some reason (with config=null).
                //LOG.error("Widget configuration missing (was '" + configuration + "'): Unable to create widget " + this.getClass().getSimpleName() + ".");
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Widget (" + this.getClass().getSimpleName() + ") initialized with configuration: " + configuration);
            }
        }
    }

    /**
     * Sets the widget configuration. Invoked by the constructor.
     * <p>
     * The widget configuration is set by parsing the string defined in the 
     * <code>configuration</code> attribute of the XSD file where the widget is 
     * employed.
     * <p>
     * This string must describe a JSON object, containing either the actual 
     * configuration settings, or a reference to a file containing the actual
     * configuration settings.
     * <p>
     * In case of the latter, the JSON string should describe an object with a
     * single property: 
     * <code>{@link ConfigurableWidget#CONF_KEY_CONFIG_FILE_URI} : 'path-to-config-file'</code> .
     * <p>
     * <code>path-to-config-file</code> must be either the file's root path 
     * <strong>OR</strong> its path relative to 
     * {@link ConfigurableWidget#PATH_MODULE_RESOURCES_FOLDER}. The targeted 
     * file must be of type "plain" ({@link CmsResourceTypePlain}).
     *
     * @param configuration The configuration string, passed from the <code>configuration</code> attribute of the XSD file where the widget is employed.
     * @see org.opencms.widgets.I_CmsWidget#setConfiguration(java.lang.String)
     * @see org.opencms.widgets.A_CmsWidget#A_CmsWidget(java.lang.String) 
     */
    @Override
    public void setConfiguration(String configuration) {
        if (configuration != null && configuration.length() > 0) {
            
            try {
                // Parse the configuration string, creating a JSON object
                jsonConf = new JSONObject(configuration);
                if (LOG.isTraceEnabled()) {
                    // = Correct syntax
                    //LOG.trace("Widget successfully configured. (Configuration string was '" + configuration + "')");
                }
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    // = Incorrect syntax somewhere
                    LOG.error("Configuration string unparseable as JSON. Spelling error? (Configuration string was '" + configuration + "')", e);
                }
            }
        }
    }
    
    /**
     * Returns the configuration string that is passed to the javascript widget.
     *
     * @param cms The current, initialized OpenCms object.
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

        if (jsonConf == null) {
            //throw new NullPointerException("Widget configuration is either missing or not parseable as JSON.");
            if (LOG.isErrorEnabled()) {
                LOG.error("Widget configuration is either missing or not parseable as JSON.");
            }
        }
        
        // 1: Handle config file reference
        // (NOTE: Has to be the first step, as this will overwrite the current configuration object.)
        if (jsonConf.has(CONF_KEY_CONFIG_FILE_URI))
            resolveConfigFile(cms);
        
        // 2: Add all the mandatory configuration settings
        addMandatoryConfig(cms, schemaType, messages, resource, contentLocale);
        
        // 3: Resolve any dynamic values, and return the configuration string
        return resolveDynamicValues(jsonConf.toString(), cms, resource, contentLocale);
    }
    
    /**
     * Adds the mandatory configuration settings to the configuration object.
     * 
     * @see ConfigurableWidget#getConfiguration(org.opencms.file.CmsObject, org.opencms.xml.types.A_CmsXmlContentValue, org.opencms.i18n.CmsMessages, org.opencms.file.CmsResource, java.util.Locale) 
     */
    protected void addMandatoryConfig(
                    CmsObject cms,
                    A_CmsXmlContentValue schemaType,
                    CmsMessages messages,
                    CmsResource resource,
                    Locale contentLocale) {
        if (!jsonConf.has(CONF_KEY_NAME)) {
            this.addName(schemaType);
            this.addLocale(contentLocale);
        }
    }
    
    /**
     * Gets a JSON object with is identical to the given one, but with an added 
     * <code>name</code> property. The value of this property is composed from 
     * the given schema type, e.g.: "MyResourceTypeName:OpenCmsMyElementName".
     * 
     * @param schemaType The schema type.
     * @return The configuration object, updated with an added <code>name</code> property.
     * @see #getConfiguration(org.opencms.file.CmsObject, org.opencms.xml.types.A_CmsXmlContentValue, org.opencms.i18n.CmsMessages, org.opencms.file.CmsResource, java.util.Locale) 
     */
    protected JSONObject addName(A_CmsXmlContentValue schemaType) {
        if (jsonConf != null) {
            // Construct a string to be used as name attribute for the input element,
            // e.g.: "MyResourceTypeName:OpenCmsMyElementName"
            String typeName = schemaType.getContentDefinition().getTypeName();
            String nodeName = schemaType.getName();
            String elementName = typeName + ":" + nodeName;

            try {
                // Add "name" property to the configuration object
                JSONObject nameObj = new JSONObject("{ \"" + CONF_KEY_NAME + "\" : \"" + elementName + "\" }");
                jsonConf.merge(nameObj, true, false);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to add key 'name' to widget configuration.", e);
            }
        }
        return jsonConf;
    }
    
    /**
     * Gets a JSON object with is identical to the given one, but with an added 
     * <code>locale</code> property. The value of this property is derived from
     * the given <code>CmsObject</code>, i.e. "en".
     * 
     * @param contentLocale The content locale.
     * @return The configuration object, updated with an added <code>locale</code> property.
     * @see #getConfiguration(org.opencms.file.CmsObject, org.opencms.xml.types.A_CmsXmlContentValue, org.opencms.i18n.CmsMessages, org.opencms.file.CmsResource, java.util.Locale) 
     */
    protected JSONObject addLocale(Locale contentLocale) {
        if (jsonConf != null) {
            // Get the locale string, e.g. "en"
            String loc = contentLocale.getLanguage();

            try {
                // Add "name" property to the configuration object
                JSONObject jo = new JSONObject("{ \"" + CONF_KEY_LOCALE + "\" : \"" + loc + "\" }");
                jsonConf.merge(jo, true, false);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to add key 'locale' to widget configuration.", e);
            }
        }
        return jsonConf;
    }
    
    /**
     * Parses a file (its URI must already exist in the configuration object, 
     * under the key defined by {@link ConfigurableWidget#CONF_KEY_CONFIG_FILE_URI}) 
     * as JSON, and substitutes the current configuration with that JSON object.
     * 
     * @param cms The current, initialized OpenCms object.
     */
    protected void resolveConfigFile(CmsObject cms) {
        if (jsonConf.has(CONF_KEY_CONFIG_FILE_URI)) {
            // Read the config file URI (we don't know for sure yet if it's a file URI, but we'll assume so)
            String configFileUri = null;
            try {
                configFileUri = jsonConf.getString(CONF_KEY_CONFIG_FILE_URI);
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Failed to read '" + CONF_KEY_CONFIG_FILE_URI + "' value from the widget configuration.", e);
                }
            }
            
            // Check if it's ACTUALLY a file, and if so, parse the content into the config object
            try {
                // Clean up the URI (getResourceLink() may add stuff like leading "/opencms/opencms", getRootPath() removes that)
                configFileUri = OpenCms.getLinkManager().getRootPath(cms, getResourceLink(cms, configFileUri));

                // Proceed only if there is a "plain" resource at the given URI
                if (cms.existsResource(configFileUri) 
                        && cms.readResource(configFileUri).getTypeId() == CmsResourceTypePlain.getStaticTypeId()) {

                    // Read the file contents into a string
                    String configFileContent = new String(cms.readFile(configFileUri).getContents(), OpenCms.getSystemInfo().getDefaultEncoding());
                    jsonConf = new JSONObject(configFileContent);
                } else {
                    throw new NullPointerException("The widget configuration references a config file at '" + configFileUri + "', but no such file exists.");
                }
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to read widget configuration from file '" + configFileUri + "': " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Gets all regex patterns used to identify dynamic value notations.
     * 
     * @see ConfigurableWidget#DYNAMIC_VAL_LOCALE
     * @see ConfigurableWidget#DYNAMIC_VAL_SELF
     * @see ConfigurableWidget#DYNAMIC_VAL_PROPERTY_REGEX
     * @see ConfigurableWidget#DYNAMIC_VAL_CURRENT_TIME_REGEX
     * @return All regex patterns used to identify dynamic value notations.
     */
    protected String[] getDynamicValueRegexPatterns() {
        return new String[] {
            DYNAMIC_VAL_CURRENT_TIME_REGEX,
            DYNAMIC_VAL_PROPERTY_REGEX,
            DYNAMIC_VAL_SELF,
            DYNAMIC_VAL_LOCALE
        };
    }
    
    /**
     * Determines a dynamic value prefix by evaluating the given dynamic value 
     * regex pattern. E.g. if the given pattern is "__KEY[some-details]", then 
     * "__KEY" is the returned prefix.
     * 
     * @see ConfigurableWidget#DYNAMIC_VAL_PREFIX_REGEX
     * @see ConfigurableWidget#getDynamicValueRegexPatterns() 
     * @param regexPattern The dynamic value regex pattern to evaluate when determining the prefix.
     * @return The dynamic value prefix used in the given pattern, or null if no prefix can be determined.
     */
    protected String getDynamicValuePrefix(String regexPattern) {
        try {
            Pattern p = Pattern.compile(DYNAMIC_VAL_PREFIX_REGEX);
            Matcher m = p.matcher(regexPattern);

            if (m.find()) {
                return m.group();
            }
        } catch (Exception e) {
            LOG.error("Error attempting to extract the prefix for dynamic value regex pattern " + regexPattern + ".", e);
        }
        
        return null;
    }
    
    /**
     * Resolves all dynamic values in the given configuration string.
     * <p>
     * Dynamic values are resolved using the regex patterns returned by 
     * {@link ConfigurableWidget#getDynamicValueRegexPatterns()}.
     * 
     * @param configuration The configuration string, possibly containing dynamic value notations.
     * @param cms The current, initialized OpenCms object.
     * @param resource The edited resource.
     * @param contentLocale The content locale.
     * @return The configuration string, with dynamic value notations replaced by actual values.
     */
    protected String resolveDynamicValues(String configuration, CmsObject cms, CmsResource resource, Locale contentLocale) {
        
        // Get the regex patterns
        String[] regexes = getDynamicValueRegexPatterns();
        
        // No regexes = nothing to do here
        if (regexes.length < 1) {
            return configuration;
        }
        
        // Work on a copy
        String s = configuration;
        
        for (int i = 0; i < regexes.length; i++) {
            Map<String, String> replacements = new HashMap<String, String>();

            // Pattern and matcher used to locate dynamic value notations
            Pattern p = Pattern.compile(regexes[i]);
            Matcher m = p.matcher(s);

            // Search
            while (m.find()) {
                String match = null;
                try {
                    match = s.substring(m.start(), m.end());
                    String prefix = getDynamicValuePrefix(regexes[i]);
                    String replacement = prefix;
                    
                    //
                    // Current time:
                    //
                    if (prefix.equals(DYNAMIC_VAL_PREFIX_CURRENT_TIME)) {
                        String format = match.substring((prefix+"[").length());
                        // Read the date format
                        format = format.substring(0, format.length() - 1);
                        replacement = "" + new Date().getTime();
                        if (!format.equalsIgnoreCase("numeric")) {
                            try {
                                replacement = new SimpleDateFormat(format, contentLocale).format(new Date());
                            } catch (Exception dfe) {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Error formatting current time using formatter '" + format + "'.", dfe);
                                }
                            }
                        }
                    }
                    
                    //
                    // Property value:
                    //
                    else if (prefix.equals(DYNAMIC_VAL_PREFIX_PROPERTY)) {
                        String propName = match.substring((DYNAMIC_VAL_PREFIX_PROPERTY+"[").length());
                        propName = propName.substring(0, propName.length() - 1);

                        // Is this property defined?
                        if (cms.readPropertyDefinition(propName) == null) {
                            throw new NullPointerException("Dynamic value referenced non-existing property '" + propName + "'. Please define this property before referencing it.");
                        }
                        String noPropertyValue = "No property value";
                        // Map the match (e.g. "__PROP[locale]") to the property value (e.g. "en")
                        replacement = cms.readPropertyObject(resource, propName, true).getValue(noPropertyValue);
                        
                        // Handle missing property values?
                        if (replacement.equals(noPropertyValue)) { 
                            if (propName.equals(CmsPropertyDefinition.PROPERTY_LOCALE)) {
                                // No locale defined, fallback to default locale
                                replacement = OpenCms.getLocaleManager().getDefaultLocale(cms, resource).toString();
                            }
                        }
                        
                    }
                    
                    //
                    // Content locale (same as __PROP[locale], kept for backwards compatibility):
                    //
                    else if (prefix.equals(DYNAMIC_VAL_LOCALE)) {
                        replacement = contentLocale.toString();
                    }
                    
                    //
                    // Path to resource being edited:
                    //
                    else if (prefix.equals(DYNAMIC_VAL_SELF)) {
                        replacement = resource.getRootPath();
                    }
                    
                    //
                    // Additional dynamic value stuff can be added here ...
                    //
                    
                    // Map the match + replacement
                    replacements.put(match, replacement);
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error resolving dynamic value '" + match + "' from property.", e);
                    }
                }
            }

            s = injectDynamicValues(s, replacements);
        }
        
        return s;
    }
    
    /**
     * Injects dynamic values by replacing dynamic value notations with actual
     * values.
     * @param original The original string, possibly with dynamic value notations.
     * @param replacements A mapping of notations (the keys) and their respective actual values (the values).
     * @return The given string, with any dynamic value notations replaced by actual values.
     */
    protected String injectDynamicValues(String original, Map<String, String> replacements) {
        // Nothing to replace, return unmodified
        if (replacements == null || replacements.isEmpty()) {
            return original; 
        }
        
        // Replace
        String s = original;
        Iterator<String> i = replacements.keySet().iterator();
        while (i.hasNext()) {
            String match = null;
            try {
                match = i.next();
                // Replace the match (e.g. "__PROP[locale]") with its corresponding value (e.g. "en") in the given map
                s = s.replace(match, replacements.get(match));
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error injecting dynamic value '" + match + "' from property.", e);
                }
            }
        }
        return s;
    }

    /**
     * Not needed when using ADE (Advanced Direct Edit, standard in OpenCms 8.5), 
     * but mandatory because of the interface. Not used for our generic widgets.
     *
     * @param cms The current, initialized OpenCms object.
     * @param widgetDialog The dialog containing the widget.
     * @param param The widget parameter to generate the widget for.
     * @return Always returns <code>null</code>.
     */
    @Override
    public String getDialogWidget(CmsObject cms, I_CmsWidgetDialog widgetDialog, I_CmsWidgetParameter param) {
        return null;
    }

    /**
     * No widget of this class is an "internal" (core) widget, so always return 
     * false.
     *
     * @return Always returns <code>false</code>.
     */
    @Override
    public boolean isInternal() {
        return false;
    }

    /**
     * Returns the VFS path to widget resources (javascript, CSS, etc.) folder.
     * 
     * @return The VFS path to widget resources folder.
     * @see #PATH_MODULE_RESOURCES_FOLDER
     */
    protected String getResourcesFolderPath() {
        return PATH_MODULE_RESOURCES_FOLDER;
    }

    /**
     * Returns a list of paths to CSS files needed by the widget. OpenCms will
     * include these CSS files when editing.
     *
     * @param cms The current, initialized OpenCms object.
     * @return A list of paths to CSS files needed by the widget, or an empty list if none.
     */
    @Override
    public List<String> getCssResourceLinks(CmsObject cms) {
        // Initially just as an empty list
        List<String> cssList = new ArrayList<String>();
        // Then add all paths, taking care each one is formatted correctly
        for (String cssPath : getCssPaths()) {
            cssList.add(getResourceLink(cms, cssPath));
        }
        // Return the list (it may be empty)
        return cssList;
    }
    
    
    /**
     * Returns a list of paths to javascript files needed by the widget. OpenCms 
     * will include these javascript files when editing.
     *
     * @param cms The current, initialized OpenCms object.
     * @return A list of paths to javascript files needed by the widget, or an empty list if none.
     */
    @Override
    public List<String> getJavaScriptResourceLinks(CmsObject cms) {
        // Initially just as an empty list
        List<String> jsList = new ArrayList<String>();
        // Then add all paths, taking care each one is formatted correctly
        for (String jsPath : getJsPaths()) {
            jsList.add(getResourceLink(cms, jsPath));
        }
        // Return the list (it may be empty)
        return jsList;
    }

    /**
     * Gets the full resource path for the given path, which is expected as one
     * of the following forms:
     * <ul>
     *  <li>Relative (partial) path, e.g. <code>js/myscript.js</code></li> 
     *  <li>Full OpenCms path, e.g. <code>/system/modules/my.module/resources/css/my.css</code></li>
     *  <li>Fully qualified URL, e.g. <code>http://my.domain/my.css</code> or <code>//cdn.my.domain/my.css</code></li>
     * </ul>
     * 
     * @param cms The current, initialized OpenCms object.
     * @param path The path; a relative (partial) path, a full OpenCms path, or a fully qualified URL.
     * @return The full path for the resource identified by the given path.
     * @see #PATH_MODULE_RESOURCES_FOLDER
     */
    protected String getResourceLink(CmsObject cms, String path) {	
        if (isExternalPath(path))
            return path; // Assume the path is a fully qualified absolute URL, like "http://my.domain/my.css"
        
        if (path.startsWith("/"))
            return OpenCms.getLinkManager().substituteLink(cms, path); // Assume a full site-relative path like "/system/modules/my.module/resources/css/my.css"
        
        return OpenCms.getLinkManager().substituteLink(cms, getResourcesFolderPath() + path);
    }

    /**
     * Returns the paths to any CSS files needed by the widget.
     * <p>
     * The paths may be relative to the module's <code>resources</code> 
     * sub-folder, e.g. <code>css/mystyle.css</code>.
     * 
     * @see #PATH_MODULE_RESOURCES_FOLDER
     * @return The paths to any CSS files needed by the widget.
     */
    protected abstract String[] getCssPaths();

    /**
     * Returns the paths to any javascript files needed by the widget.
     * <p>
     * The paths may be relative to the module's <code>resources</code> 
     * sub-folder, e.g. <code>js/mywidget.js</code>.
     * 
     * @see #PATH_MODULE_RESOURCES_FOLDER
     * @return the paths to any javascript files needed by the widget.
     */
    protected abstract String[] getJsPaths();
    
    /**
     * @return Always returns {@link DisplayType#wide}.
     * @see org.opencms.widgets.I_CmsADEWidget#getDefaultDisplayType()
     */
    @Override
    public DisplayType getDefaultDisplayType() {
        return DisplayType.wide;
    }
    
    /**
     * Evaluates if the given path points to an external resource.
     * 
     * @param path The path to evaluate.
     * @return True if the given path points to an external resource (starts with "http" or "//"), false in all other cases.
     */
    protected boolean isExternalPath(String path) {
        if (path == null || path.isEmpty())
            return false;
        
        return path.startsWith("http") || path.startsWith("//");
    }
    
    
    
    
    
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Code below here is NOT USED ANYWHERE YET. (Ideas for improvement.)
    //
    ////////////////////////////////////////////////////////////////////////////
    
    
    
    /**
     * Reads asset (javascript, CSS, etc.) resources in a given folder, and 
     * returns a list of paths to any resources found that match the given 
     * file extension. 
     * 
     * @param cms The current, initialized OpenCms object.
     * @param fileExtension The file extension to match against, e.g. "js" or "css".
     * @param assetResourcesUri The asset resources folder to search in.
     * @return A list of paths to asset resources that match the given criteria.
     */
    protected List<String> readAssetResources(CmsObject cms, String fileExtension, String assetResourcesUri) {
        List<String> assetPaths = new ArrayList<String>();
        
        // Require something real at the given path
        if (cms.existsResource(assetResourcesUri)) {
            try {
                CmsResource assetsFolder = cms.readResource(assetResourcesUri);
                // Require that the given path identifies a folder
                if (assetsFolder.isFolder()) {
                    // Get all files in that folder, and loop them
                    List<CmsResource> allAssets = cms.readResources(assetResourcesUri, CmsResourceFilter.DEFAULT_FILES, false);
                    Iterator<CmsResource> i = allAssets.iterator();
                    while (i.hasNext()) {
                        CmsResource asset = i.next();
                        // Does the asset's extension match the given file extension?
                        if (CmsFileUtil.getExtension(asset.getName()).endsWith(".".concat(fileExtension.toLowerCase()))) {
                            // Yup! Then we add it to the list of asset paths
                            assetPaths.add(assetResourcesUri + asset.getName());
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Return all paths (or an empty list)
        return assetPaths;
    }
    
    /**
     * Based on the given path, this method will try to return a path that's 
     * readable from within this class. If the given path points to an external 
     * resource, it is returned unmodified.
     * 
     * @param cms The current, initialized OpenCms object.
     * @param relPath The path, in any form.
     * @return A path that's readable, if possible.
     */
    protected String getReadablePath(CmsObject cms, String relPath) {
        if (isExternalPath(relPath))
            return relPath;
        
        return OpenCms.getLinkManager().getRootPath(cms, getResourceLink(cms, relPath));
    }
    
    /**
     * Return the contents of a VFS file as a String.
     * 
     * @param cms The current, initialized OpenCms object.
     * @param path The path to the file to read from. (Typically a plain text file.)
     * @return The contents of the VFS file at the given path, or null if that fails.
     */
    protected String getVfsFileContent(CmsObject cms, String path) {
        try {
            return new String(cms.readFile(path).getContents(), OpenCms.getSystemInfo().getDefaultEncoding());
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) 
                LOG.error("Unable to read OpenCms VFS file '" + path + "'.", e);
            return null;
        }
    }
    
    /**
     * Returns a Properties instance, built from the given string.
     * 
     * @param s The string to build the Properties object from.
     * @return A properties object, built from the given string.
     * @throws IOException If anything goes south.
     */
    public Properties parsePropertiesString(String s) throws IOException {
        final Properties p = new Properties();
        p.load(new StringReader(s));
        return p;
        
    }
    
    
    /** The path to the global settings file, relative to {@link #PATH_MODULE_RESOURCES_FOLDER} */
    protected static final String PATH_GLOBAL_CONFIGURATION_FOLDER = "conf/";
    /** The path to the global settings file, relative to {@link #PATH_MODULE_RESOURCES_FOLDER} */
    protected static final String PATH_GLOBAL_SETTINGS_FILE = PATH_GLOBAL_CONFIGURATION_FOLDER + "settings.properties";
    /** Global widget global settings, as defined in the file at {@link #PATH_GLOBAL_SETTINGS_FILE}. */
    protected Properties globalSettings = null;
    
    /**
     * Returns the global settings for this widget. If the global settings have
     * not yet been read, this method will try to read them first.
     * 
     * @param cms The current, initialized OpenCms object.
     * @return The global settings for this widget, or null (if no global settings exists or an error occurs).
     */
    protected Properties getGlobalSettings(CmsObject cms) {
        if (globalSettings == null) {
            String fileContent = null;

            try {
                String propertiesFileUri = getReadablePath(cms, PATH_GLOBAL_SETTINGS_FILE);
                // Read the file contents into a string
                fileContent = getVfsFileContent(cms, propertiesFileUri);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to read widget settings.", e);
                return null;
            }

            try {
                globalSettings = parsePropertiesString(fileContent);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to parse widget settings as Properties object.", e);
                return null;
            }
        }
        
        return globalSettings;
    }
    
    /**
     * Gets a specific widget setting by name.
     * 
     * @param cms The current, initialized OpenCms object.
     * @param setting The name of the setting to get.
     * @return A specific widget setting.
     */
    protected String getGlobalSetting(CmsObject cms, String setting) {
        return getGlobalSettings(cms).getProperty(setting);
    }
    
    
    
    /**
     * Resolves any dynamic values in the given string.
     * <p>
     * For example, all occurrences of {@link ConfigurableWidget#DYNAMIC_VAL_LOCALE} 
     * will be replaced by the actual "current" locale string, e.g. "en" or "no".
     * 
     * @param s A string that may or may not contain dynamic value keywords.
     * @param cms The current, initialized OpenCms object.
     * @param resource The edited resource.
     * @return The given string, but with all dynamic value keywords replaced by actual values.
     */
    /*
    protected String resolveDynamicValues(String s, CmsObject cms, CmsResource resource) {
        try {
            s = s.replace(DYNAMIC_VAL_LOCALE, jsonConf.getString(CONF_KEY_LOCALE));
        } catch (Exception e) {
            // That'd be a JSON exception => no locale found, leave string unmodified.
        }
        try {
            s = resolvePropertyValues(s, cms, resource);
        } catch (Exception e) {
            // That'd be a JSON exception => no locale found, leave string unmodified.
        }
        return s;
    }
    //*/
    
    /**
     * Resolves dynamic values that are read from the properties of the given
     * resource.
     * <p>
     * Property value notations should look like: <code>__PROP[locale]</code> 
     * or <code>__PROP[Title]</code>.
     * <p>
     * The property value is read from the resource being edited, searching 
     * parents if not found on that resource directly. If no value is found, the
     * injected value will be "No property value".
     * 
     * @param s A string that may or may not contain dynamic property value notations.
     * @param cms The current, initialized OpenCms object.
     * @param resource The edited resource.
     * @return The given string, but with all dynamic property value notations replaced by actual property values.
     * @see ConfigurableWidget#DYNAMIC_VAL_PREFIX_PROPERTY
     */
    /*
    protected String resolvePropertyValues(String s, CmsObject cms, CmsResource resource) {
        // Storage for matches + replacements
        Map<String, String> replacements = new HashMap<String, String>();
        
        // Pattern and matcher used to locate dynamic property value notations, e.g. __PROP[locale]
        Pattern p = Pattern.compile(DYNAMIC_VAL_PROPERTY_REGEX);
        Matcher m = p.matcher(s);
        
        // Search
        while (m.find()) {
            String match = null;
            try {
                match = s.substring(m.start(), m.end()); // Found property notation
                String propName = match.substring((DYNAMIC_VAL_PREFIX_PROPERTY+"[").length());
                propName = propName.substring(0, propName.length() - 1);
                
                // Is this property defined?
                if (cms.readPropertyDefinition(propName) == null) {
                    throw new NullPointerException("Dynamic value referenced non-existing property '" + propName + "'. Please define this property before referencing it.");
                }
                
                // Map the match (e.g. "__PROP[locale]") to the property value (e.g. "en")
                replacements.put(match, cms.readPropertyObject(resource, propName, true).getValue("No property value"));
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error resolving dynamic value '" + match + "' from property.", e);
                }
            }
        }
        
        return injectDynamicValues(s, replacements);
    }
    //*/
    
    /**
     * Resolves dynamic values that are read from the properties of the given
     * resource.
     * <p>
     * Property value notations should look like: <code>__NOW[yyyy]</code> 
     * or <code>__NOW[d MMMM yyyy]</code>.
     * <p>
     * The property value is read from the resource being edited, searching 
     * parents if not found on that resource directly. If no value is found, the
     * injected value will be "No property value".
     * 
     * @param s A string that may or may not contain dynamic property value notations.
     * @param cms The current, initialized OpenCms object.
     * @param resource The edited resource.
     * @return The given string, but with all dynamic property value notations replaced by actual property values.
     * @see ConfigurableWidget#DYNAMIC_VAL_PREFIX_PROPERTY
     */
    /*
    protected String resolveCurrentTimeValues(String s, CmsObject cms, CmsResource resource, Locale contentLocale) {
        // Storage for matches + replacements
        Map<String, String> replacements = new HashMap<String, String>();
        
        // Pattern and matcher used to locate dynamic "now" (current time) notations, e.g. __NOW[d MMM yyyy]
        Pattern p = Pattern.compile(DYNAMIC_VAL_CURRENT_TIME_REGEX);
        Matcher m = p.matcher(s);
        
        // Search
        while (m.find()) {
            String match = null;
            try {
                match = s.substring(m.start(), m.end()); // Found "now" (current time) notation
                String format = match.substring((DYNAMIC_VAL_PREFIX_CURRENT_TIME+"[").length());
                // Read the date format
                format = format.substring(0, format.length() - 1);
                String replacement = "" + new Date().getTime();
                if (!format.equalsIgnoreCase("numeric")) {
                    try {
                        replacement = new SimpleDateFormat(format, contentLocale).format(new Date());
                    } catch (Exception dfe) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("Error formatting current time using formatter '" + format + "'.", dfe);
                        }
                    }
                }
                
                // Map the match + replacement
                replacements.put(match, replacement);
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error resolving dynamic value '" + match + "' from property.", e);
                }
            }
        }
        
        return injectDynamicValues(s, replacements);
    }
    //*/
    
    /**
     * Gets a JSON object with is identical to the given one, but with an added 
     * <code>name</code> property. The value of this property is composed from 
     * the given schema type, e.g.: "MyResourceTypeName:OpenCmsMyElementName".
     * 
     * @deprecated Replaced by {@link ConfigurableWidget#addName(org.opencms.xml.types.A_CmsXmlContentValue)}
     * @param configuration The configuration object, without the <code>name</code> property.
     * @param schemaType The schema type.
     * @return A configuration object identical to the given one, but with an added <code>name</code> property.
     * @see #getConfiguration(org.opencms.file.CmsObject, org.opencms.xml.types.A_CmsXmlContentValue, org.opencms.i18n.CmsMessages, org.opencms.file.CmsResource, java.util.Locale) 
     */
    /*
    protected static JSONObject addName(JSONObject configuration, A_CmsXmlContentValue schemaType) {
        if (configuration != null) {
            // Construct a string to be used as name attribute for the input element,
            // e.g.: "MyResourceTypeName:OpenCmsMyElementName"
            String typeName = schemaType.getContentDefinition().getTypeName();
            String nodeName = schemaType.getName();
            String elementName = typeName + ":" + nodeName;

            // Create a configuration object
            JSONObject c = configuration;
            try {
                // Add "name" property to the configuration object
                JSONObject nameObj = new JSONObject("{ \"" + CONF_KEY_NAME + "\" : \"" + elementName + "\" }");
                c.merge(nameObj, true, false);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to add key 'name' to widget configuration.", e);
            }
            return c;
        }
        return configuration;
    }
    //*/
    
    /**
     * Gets a JSON object with is identical to the given one, but with an added 
     * <code>locale</code> property. The value of this property is derived from
     * the given <code>CmsObject</code>, i.e. "en".
     * 
     * @deprecated Replaced by {@link ConfigurableWidget#addLocale(java.util.Locale)} 
     * @param configuration The configuration object, without the <code>locale</code> property.
     * @param contentLocale The content locale.
     * @return A configuration object identical to the given one, but with an added <code>locale</code> property.
     * @see #getConfiguration(org.opencms.file.CmsObject, org.opencms.xml.types.A_CmsXmlContentValue, org.opencms.i18n.CmsMessages, org.opencms.file.CmsResource, java.util.Locale) 
     */
    /*
    protected static JSONObject addLocale(JSONObject configuration, Locale contentLocale) {
        if (configuration != null) {
            // Get the locale string, e.g. "en"
            String loc = contentLocale.getLanguage();

            // Create a configuration object
            JSONObject c = configuration;
            try {
                // Add "name" property to the configuration object
                JSONObject jo = new JSONObject("{ \"" + CONF_KEY_LOCALE + "\" : \"" + loc + "\" }");
                c.merge(jo, true, false);
            } catch (Exception e) {
                if (LOG.isErrorEnabled())
                    LOG.error("Unable to add key 'locale' to widget configuration.", e);
            }
            return c;
        }
        return configuration;
    }
    //*/
}