// Copyright (c) 2013 Mikhail Afanasov and DeepSe group. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package core;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import parsers.configuration.ParseException;
import parsers.configuration.Parser;

public class ContextConfiguration extends Configuration{
	
	private boolean _isParsed = false;
	private ArrayList<String> _usedGroups = new ArrayList<>();
	
	public ContextConfiguration(FileManager fm, String name) {
		super(fm, name);
	}
	
	public List<Function> getLayeredFunctions() {
		return _file.functions.get("layered");
	}
	
	@Override
	public void parse() {
		Parser parser = new Parser(new StringReader(_file_cnc));
		try {
			System.out.println("Parsing "+getFilename());
			parser.parse();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println(_file_cnc);
		}
		_file  = parser.getParsedFile();
		
		_sourceFileArray = _file_cnc.split("\n");
		
		parseComponents();
		
		if( _components.containsKey(_file.name)) {
			int strNum = getNumberOf("(,\\s+|\\s+)" + _file.name + "(\\s*,|\\s*;|\\s*)");
			Print.error(_file.name + ".cnc " + strNum, "Group " + _file.name + " can not be used within itself!");
			_components.remove(_file.name);
		}
		
		if(!_file.defaultContext.isEmpty() &&
			(!_components.containsKey(_file.defaultContext) ||
			_components.get(_file.defaultContext).getType() != Component.Type.CONTEXT)) {
			int strNum = getNumberOf("(,\\s+|\\s+)" + _file.defaultContext + "(\\s*,|\\s*;|\\s*)");
			Print.error(_file.name+".cnc " + strNum, "Component " + _file.defaultContext + " is not a Context or does not exist, but declared as a default Context!");
			_file.defaultContext = "default";
			ContextsHeader.add(_file.name, "default" + _file.name);
		}
		
		if(!_file.errorContext.isEmpty() &&
			(!_components.containsKey(_file.errorContext) ||
			_components.get(_file.errorContext).getType() != Component.Type.CONTEXT)) {
			int strNum = getNumberOf("(,\\s+|\\s+)" + _file.errorContext + "(\\s*,|\\s*;|\\s*)");
			Print.error(_file.name+".cnc " + strNum, "Component " + _file.errorContext + " is not a Context or does not exist, but declared as an error Context!");
			_file.errorContext = "";
		}
		
		if (_file.errorContext.isEmpty() && !_file.contexts.contains("Error"))
			_file.contexts.add("Error");

		ContextsHeader.addAll(_file.name, _file.contexts);
		
		_isParsed = true;
		
		for (String context : _file.contexts) {
			if (!_components.containsKey(context)) continue;
			for (String condition : ((Context)_components.get(context)).getTransitionConditions().values()) {
				String group = condition.split("\\.")[0];
				if (_usedGroups.contains(group) || 
					group.equals(_file.name)) continue;
				_usedGroups.add(group);
				if (_file.contextGroups.contains(group)) continue;
				_file.contextGroups.add(group);
			}
			for (String trigger : ((Context)_components.get(context)).getTriggers()) {
				String group = trigger.split("\\.")[0];
				if (_usedGroups.contains(group) ||
					group.equals(_file.name)) continue;
				_usedGroups.add(group);
				if (_file.contextGroups.contains(group)) continue;
				_file.contextGroups.add(group);
			}
		}
	}
	
	@Override
	public boolean hasLayeredFunctions() {
		return !_file.functions.get("layered").isEmpty();
	}
	
	@Override
	public void build() {
		if (!_isParsed) parse();
		buildGroup();
		buildInterface();
		buildErrorContext();
		buildConfiguration();
	}
	
	@Override
	protected void buildConfiguration() {
		String builtConf = "";
		
		// building includes
		for (String include : _file.includes)
			builtConf += "#include " + include + "\n";
		
		builtConf += "configuration " + _file.name + "Configuration {\n";
		builtConf += "  provides interface ContextGroup;\n";
		if (!_file.functions.get("layered").isEmpty())
			builtConf += "  provides interface " + _file.name + "Layer;\n";
		for (String intrfce : _file.interfaces.get("provides"))
			builtConf += "  provides interface " + intrfce + ";\n";
		for (String intrfce : _file.interfaces.get("uses"))
			builtConf += "  uses interface " + intrfce + ";\n";
		builtConf += "}\n";
		
		builtConf += "implementation {\n";
		
		if (!_file.contextGroups.isEmpty()) {
			builtConf += "  context groups";
			for (String conf : _file.contextGroups)
				builtConf += "\n    " + conf + ",";
			builtConf = builtConf.substring(0, builtConf.length() - 1) + ";\n";
		}
		
		builtConf += "  components";
		
		builtConf += "\n    " + _file.name + "Group,";
		
		for (String context : _file.contexts)
			builtConf += "\n    " + context + _file.name + "Context,";
		for (String component : _file.components)
			builtConf += "\n    " + component + ",";
		
		// if there are some groups are used in contexts, but not declared...
		for(String contextname : _file.contexts) {
			if (!_components.containsKey(contextname)) continue;
			Context context = (Context)_components.get(contextname);
			for(String usedGroup : context.getUsedGroups())
				if (!_file.usedGroups.contains(usedGroup) && !_file.contextGroups.contains(usedGroup)) {
					//...declare them...
					builtConf += "\n    " + usedGroup + "Configuration,";
					//...and bind it to the context.
					_file.wires.put(contextname + "." + usedGroup + "Group", usedGroup + "Configuration");
				}
		}

		builtConf = builtConf.substring(0, builtConf.length()-1);
		builtConf += ";\n";
		
		for (String key : _file.wires.keySet()) {
			String[] splitted_key = key.split("\\.");
			String value = _file.wires.get(key);
			if (_file.contexts.contains(value))
				value += _file.name + "Context";
			if (!_file.contexts.contains(splitted_key[0])) {
				builtConf += "  " + key + " -> " + value + ";\n";
			} else {
				builtConf += "  " + splitted_key[0] + _file.name + "Context." + 
								splitted_key[1] + " -> " + value + ";\n";
			}
		}
		
		for (String context : _file.contexts) {
			builtConf += "  " + _file.name + "Group." + context + _file.name + "Context -> " +
						 context + _file.name + "Context;\n";
			if (!context.equals("Error") && !_file.functions.get("layered").isEmpty())
				builtConf += "  " + _file.name + "Group." + context + _file.name + "Layer -> " +
								context + _file.name + "Context;\n";
		}
		
		for (String group : _usedGroups)
			builtConf += "  " + _file.name + "Group." + group + " -> " + group + ";\n";
			
		
		builtConf += "  ContextGroup = " + _file.name + "Group;\n";
		if (!_file.functions.get("layered").isEmpty())
			builtConf += "  " + _file.name + "Layer = " + _file.name + "Group;\n";
		
		for (String key : _file.equality.keySet())
			for (String comp : _file.equality.get(key)) {
				String rightElem = comp;
				String leftElem = key;
				if (_file.contexts.contains(comp))
					rightElem = comp + _file.name + "Context";
				if (key.contains(".") && _file.contexts.contains(key.split("\\.")[0]))
					leftElem = key.split("\\.")[0] + _file.name + "Context." + key.split("\\.")[1];
				builtConf += "  " + leftElem + " = " + rightElem + ";\n";
			}
		
		builtConf += "}\n";
		
		String oldName = _file.name;
		int oldType = _file.type;
		List<Function> oldLayeredFunction = new ArrayList<>(_file.functions.get("layered"));
		// after this function _file.name will be changed to _file.name+"Configuration"
		// we are trying to save it
		// the same with the type
		super.parse(builtConf);
		super.buildConfiguration();
		
		_file.functions.put("layered",oldLayeredFunction);
		_file.name = oldName;
		_file.type = oldType;
	}
	
	private void buildInterface() {
		if (_file.functions.get("layered").isEmpty()) return;
		String builtInterface = "";
		
		// building includes
		for (String include : _file.includes)
			builtInterface += "#include " + include + "\n";
		
		builtInterface += "interface " + _file.name + "Layer {\n";
		for (Function f : _file.functions.get("layered")) {
			builtInterface += "  command " + f.returnType + " " + f.name + "(";
			int last = f.variables.size() - 1;
			for (Variable var : f.variables) {
				builtInterface += var.type + var.lexeme +" " + var.name;
				if (f.variables.lastIndexOf(var) != last)
					builtInterface += ", ";
			}
			builtInterface += ");\n";
		}
		
		builtInterface += "}\n";
		
		_generatedFiles.put(_file.name + "Layer.nc",builtInterface);
	}
	
	private void buildErrorContext() {
		if (!_file.errorContext.isEmpty()) return;
		
		String errorContext = "";
		
		// building includes
		for (String include : _file.includes)
			errorContext += "#include " + include + "\n";
		
		errorContext +=
			"module Error" + _file.name + "Context {\n" +
			"  provides interface ContextCommands as Command;\n" +
			"  uses interface ContextEvents as Event;\n" +
			"}\n" +
			"implementation {\n" +
			"  event void Event.activated(){\n" +
			"  }\n" +
			"  event void Event.deactivated(){\n" +
			"  }\n" +
			"  command bool Command.check(){\n" +
			"    return TRUE;\n" +
			"  }\n" +
			"  command void Command.activate() {\n" +
			"    signal Event.activated();\n" +
			"  }\n" +
			"  command void Command.deactivate() {\n" +
			"    signal Event.deactivated();\n" +
			"  }\n" +
			"  command bool Command.transitionIsPossible(context_t con) {\n" +
			"    return TRUE;\n" +
			"  }\n" +
			"  command bool Command.conditionsAreSatisfied(context_t to, context_t cond) {\n" +
			"    return TRUE;\n" +
			"  }\n" +
			"}";
		_generatedFiles.put("Error" + _file.name + "Context.nc", errorContext);
	}
	
	private void buildGroup(){
		String builtGroup = "";
		
		// building includes
		for (String include : _file.includes)
			builtGroup += "#include " + include + "\n";
		
		builtGroup += "#include \"Contexts.h\"\n" +
			"module " + _file.name + "Group {\n" +
			"  provides interface ContextGroup as Group;\n";
		if (!_file.functions.get("layered").isEmpty())
			builtGroup += "  provides interface " + _file.name + "Layer as Layer;\n";
		
		for (String context : _file.contexts) {
			builtGroup += "  uses interface ContextCommands as " + context + _file.name + "Context;\n";
			if (!context.equals("Error") && !_file.functions.get("layered").isEmpty())
				builtGroup += "  uses interface " + _file.name + "Layer as " + context + _file.name + "Layer;\n";
		}
		
		for (String group : _file.contextGroups)
			builtGroup += "  uses interface ContextGroup as " + group + "Group;\n";
		
		builtGroup += "}\nimplementation {\n";
		
		builtGroup += "  context_t context = " + _file.defaultContext.toUpperCase() + _file.name.toUpperCase() + ";\n";
		
		// building deactivate function, which is always called before context activation
		builtGroup += "  void deactivate() {\n" +
			"    switch (context) {\n";
		for (String context : _file.contexts)
			builtGroup += "      case " + context.toUpperCase() + _file.name.toUpperCase() + ":\n" +
					"        call " + context + _file.name + "Context.deactivate();\n" +
					"        break;\n";
		builtGroup += "      default:\n" +
					  "        break;\n" +
					  "    }\n" +
					  "  }\n";
		
		// building transitionIsPossible(), which is called to check if transition is possible
		builtGroup += "  bool transitionIsPossible(context_t con) {\n" +
					  "    switch (context) {\n";
		for (String context : _file.contexts) {
			if (_file.errorContext.isEmpty() && context.equals("Error")) continue;
			builtGroup += "      case " + context.toUpperCase() + _file.name.toUpperCase() + ":\n" +
				"        return call " + context + _file.name + "Context.transitionIsPossible(con);\n";
		}
		builtGroup += "      default:\n" +
				  "        return FALSE;\n" +
				  "    }\n" +
				  "  }\n";
		
		// building conditionsAreSatisfied(), which is called to check if transition conditions are satisfied
		// is not used anymore
		/*
		builtGroup += "  bool conditionsAreSatisfied(context_t to) {\n" +
		              "    switch (context) {\n";
		for (String context : _file.contexts) {
			if (!_components.containsKey(context)) continue;
			if (((Context)_components.get(context)).getTransitionConditions().isEmpty()) continue;
			builtGroup += "      case " + context.toUpperCase() + _file.name.toUpperCase() + ":\n";
			builtGroup += "        return ";
			for (int i = 0; i < _usedGroups.size(); i++) {
				String tab = "\n               ";
				if (i == 0) tab = "";
				builtGroup += tab + "call " + context + _file.name + "Context.conditionsAreSatisfied(to) ||, " +
				              "call " + _usedGroups.get(i) + "Group.getContext()) ||";
			}
			builtGroup = builtGroup.substring(0, builtGroup.length() - 3);
			builtGroup += ";\n";
		}
		
		builtGroup += "      default:\n" +
					  "        return TRUE;\n"+
				      "    }\n" +
					  "  }\n";
		*/
		// building activate()
		builtGroup += "  command void Group.activate(context_t con) {\n" +
		              "    if (con == context) return;\n" +
					  "    if (!transitionIsPossible(con)) {\n"+
					  "      deactivate();\n";
		if (_file.errorContext.isEmpty())
			builtGroup += "      call Error" + _file.name + "Context.activate();\n" +
		                  "      context = ERROR" + _file.name.toUpperCase() + ";\n" +
					  	  "      signal Group.contextChanged(ERROR" + _file.name.toUpperCase() + ");\n";
		else 
			builtGroup += "      call " + _file.errorContext + _file.name + "Context.activate();\n" +
		                  "      context = " + _file.errorContext.toUpperCase() + _file.name.toUpperCase() + ";\n" +
						  "      signal Group.contextChanged(" + _file.errorContext.toUpperCase() + _file.name.toUpperCase() + ");\n";
		builtGroup += "      return;\n" +
					  "    }\n";
		//builtGroup += "    if (!conditionsAreSatisfied(con)) return;\n";
		builtGroup += "    switch (con) {\n";
		
		for (String context : _file.contexts) {
			if (_file.errorContext.isEmpty() && context.equals("Error")) continue;
			builtGroup += "      case " + context.toUpperCase() + _file.name.toUpperCase() + ":\n" +
		        "        if (!call " + context + _file.name + "Context.check()) return;\n" +
				"        deactivate();\n" +
				"        call " + context + _file.name + "Context.activate();\n" +
				"        context = " + context.toUpperCase() + _file.name.toUpperCase() + ";\n";
			if (_components.containsKey(context) )
				for (String trigger : ((Context)_components.get(context)).getTriggers()) {
					String[] name = trigger.split("\\.");
					builtGroup += "        call " + name[0] + "Group.activate(" + name[1].toUpperCase() + name[0].toUpperCase() + ");\n";
				}
			builtGroup += "        break;\n";
		}
		
		builtGroup += "      default:\n" +
					  "        deactivate();\n";
		if (_file.errorContext.isEmpty())
			builtGroup += "        call Error" + _file.name + "Context.activate();\n" +
					  "        context = ERROR" + _file.name.toUpperCase() + ";\n" +
					  "        signal Group.contextChanged(ERROR" + _file.name.toUpperCase() + ");\n";
		else
			builtGroup += "        call " + _file.errorContext + _file.name + "Context.activate();\n" +
						  "        context = " + _file.errorContext.toUpperCase() + _file.name.toUpperCase() + ";\n" +
						  "        signal Group.contextChanged(" + _file.errorContext.toUpperCase() + _file.name.toUpperCase() + ");\n";
		builtGroup += "        return;\n" +
				  "    }\n" +
				  "    signal Group.contextChanged(con);\n" + 
				  "  }\n";
		
		// building getContext()
		builtGroup += "  command context_t Group.getContext() {\n" +
					  "    return context;\n" +
					  "  }\n";
		
		// building events for using groups
		// also for triggers and transitions
		for (String group : _file.contextGroups)
			builtGroup += "  event void " + group + "Group.contextChanged(context_t con) {\n" +
						  "  }\n";
		for (String group : _usedGroups)
			if (!_file.contextGroups.contains(group))
				builtGroup += "  event void " + group + "Group.contextChanged(context_t con) {\n" +
						  	  "  }\n";
		
		// building layered functions
		for (Function f : _file.functions.get("layered")) {
			builtGroup += "  command " + f.returnType + " Layer." + f.name + "(";
			int last = f.variables.size() - 1;
			for (Variable var : f.variables) {
				builtGroup += var.type + var.lexeme +" " + var.name;
				if (f.variables.lastIndexOf(var) != last)
					builtGroup += ", ";
			}
			builtGroup += ") {\n";
			
			builtGroup += "    switch (context) {\n";
			
			for (String context : _file.contexts) {
				if (_file.errorContext.isEmpty() && context.equals("Error")) continue;
				builtGroup += "      case " + context.toUpperCase() + _file.name.toUpperCase() + ":\n" +
			        "        call " + context + _file.name + "Layer." + f.name + "(";
				last = f.variables.size() - 1;
				for (Variable var : f.variables) {
					builtGroup += var.name;
					if (f.variables.lastIndexOf(var) != last)
						builtGroup += ", ";
				}
				builtGroup += ");\n";
				builtGroup += "        break;\n";
			}
			
			builtGroup += "      default:\n" +
						  "        break;\n" +
						  "    }\n" +
						  "  }\n";
		}
		
		builtGroup += "}\n";
		
		_generatedFiles.put(_file.name + "Group.nc", builtGroup);
	}

}
