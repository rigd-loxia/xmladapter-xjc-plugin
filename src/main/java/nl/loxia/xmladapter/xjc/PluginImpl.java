package nl.loxia.xmladapter.xjc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;

/**
 * XmlAdapter-XJC plugin implementation.
 *
 * @author <a href="mailto:jan.blok@rigd-loxia.nl">Jan Blok</a>
 */
public final class PluginImpl extends Plugin {

	private static final String OPTION_NAME = "xmladapter";
	private static final String XMLADAPTER_NAMES_OPTION_NAME = "-xmladapters:";// FQNS

	private static final JType[] NO_ARGS = new JType[0];

	private List<String> xmlAdapaterFQCNs = new ArrayList<>();
	private Map<String, JClass> boundTypeToAdapterClass = new HashMap<>();
	private Map<String, JClass> boundTypeToValueType = new HashMap<>();
	private Options options;

	@Override
	public boolean run(final Outline model, final Options options, final ErrorHandler errorHandler) {
		boolean success = true;
		this.options = options;

		this.log(Level.INFO, "title");
		
		fillAdapterToValueTypeAndBoundTypes(model);

		for (ClassOutline clazz : model.getClasses()) {
			JDefinedClass implClass = clazz.implClass;

			changeFieldTypeAndAnnotate(implClass);
		}

		this.options = null;
		return success;
	}

	private void changeFieldTypeAndAnnotate(JDefinedClass clazz) {
		for (JFieldVar jFieldVar : clazz.fields().values()) {
			JClass valueTypeJClass = boundTypeToValueType.get(jFieldVar.type().fullName());
			if (valueTypeJClass != null) {
				JAnnotationUse annotationUse = jFieldVar.annotate(XmlJavaTypeAdapter.class);
				annotationUse.param("value", boundTypeToAdapterClass.get(jFieldVar.type().fullName()));
				
				jFieldVar.type(valueTypeJClass);
				
				JMethod getter = getGetterProperty(clazz, jFieldVar.name());
				getter.type(valueTypeJClass);
				JMethod setter = getSetterProperty(clazz, jFieldVar.name());
				if (setter != null) {
					setter.listParams()[0].type(valueTypeJClass);
				}
			}
		}		
	}

	private void fillAdapterToValueTypeAndBoundTypes(Outline model) {
		for (String className : xmlAdapaterFQCNs) {
				String[] classNames = className.split(",");
				if (classNames.length != 3) {
					log(Level.SEVERE, "Error during reading FQN xmladapterClass,boundTypeClass,valueTypeClass found: "+classNames);
					return;
				}
				String adapterType = classNames[0];
				String boundType = classNames[1];
				String valueType = classNames[2];
				JClass valueTypeJClass = model.getCodeModel().ref(valueType);
				JClass boundTypeJClass = model.getCodeModel().ref(boundType);
				JClass adapterTypeJClass = model.getCodeModel().ref(adapterType);
				boundTypeToAdapterClass.put(boundTypeJClass.fullName(), adapterTypeJClass);
				boundTypeToValueType.put(boundTypeJClass.fullName(), valueTypeJClass);
		}
	}

	@Override
	public String getOptionName() {
		return OPTION_NAME;
	}

	@Override
	public String getUsage() {
		final String n = System.getProperty("line.separator", "\n");
		StringBuilder retval = new StringBuilder("  -");
		retval.append(OPTION_NAME);
		retval.append("  :  ");
		retval.append("Apply XMLAdapters.");
		retval.append(n);
		retval.append("  ");
		retval.append(XMLADAPTER_NAMES_OPTION_NAME);
		retval.append("       :  ");
		retval.append("Specify fully qualified xmladapter class names.");
		retval.append(n);
		return retval.toString();
	}

	@Override
	public int parseArgument(final Options opt, final String[] args, final int i) throws BadCommandLineException, IOException {
		if (args[i].startsWith(XMLADAPTER_NAMES_OPTION_NAME)) {
			String value = args[i].substring(XMLADAPTER_NAMES_OPTION_NAME.length());
			String[] classNames = value.trim().split("[\\s\\r\\n\\t]+");
			this.xmlAdapaterFQCNs = Arrays.asList(classNames);
			return 1;
		}
		return 0;
	}

	private static JMethod getGetterProperty(final JDefinedClass clazz, final String name) {
		JMethod getter = clazz.getMethod("get" + StringUtils.capitalize(name), NO_ARGS);

		if (getter == null) {
			getter = clazz.getMethod("is" + StringUtils.capitalize(name), NO_ARGS);
		}
		return getter;
	}

	private static JMethod getSetterProperty(final JDefinedClass clazz, final String name) {
		JMethod setter = clazz.getMethod("set" + StringUtils.capitalize(name), NO_ARGS);
		return setter;
	}
	
	private void log(final Level level, final String key, final Object... args) {
		final String message = "[xmladapter] [" + level.getLocalizedName() + "] " + key + args;

		int logLevel = Level.WARNING.intValue();
		if (this.options != null && !this.options.quiet) {
			if (this.options.verbose) {
				logLevel = Level.INFO.intValue();
			}
			if (this.options.debugMode) {
				logLevel = Level.ALL.intValue();
			}
		}

		if (level.intValue() >= logLevel) {
			if (level.intValue() <= Level.INFO.intValue()) {
				System.out.println(message);
			} else {
				System.err.println(message);
			}
		}
	}
}
