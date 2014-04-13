// Copyright (c) 2013 Mikhail Afanasov and DeepSe group. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package core;

import java.io.StringReader;

import parsers.configuration.ParseException;
import parsers.configuration.Parser;

public class Configuration extends Component{

	public Configuration(FileManager fm, String filename) {
		super(fm, filename);
	}

	@Override
	public void parse() {
		parse(_file_cnc);
		parseComponents();
	}
	
	@Override
	public void build() {
		parse();
		if (_lang == Lang.NESC) return;
		buildConfiguration();
	}
	
	protected void parse(String file_cnc) {
		Parser parser = new Parser(new StringReader(file_cnc));
		try {
			parser.parse();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		_file  = parser.getParsedFile();
	}
	
	protected void buildConfiguration() {
		String builtConf = "";
		
		// building includes
		for (String include : _file.includes)
			builtConf += "#include " + include + "\n";
		
		builtConf += "configuration " + _file.name + " {\n";
		for (String key : _file.interfaces.keySet())
			if(key.equals("uses")||key.equals("provides"))
				for (String elem : _file.interfaces.get(key))
					builtConf += "  " + key + " interface " + elem + ";\n";
		builtConf += "}\nimplementation {\n";
		
		builtConf += "  components";
		for (String group : _file.contextGroups)
			builtConf += "\n    " + group + "Configuration,";
		for (String component : _file.components)
			builtConf += "\n    " + component + ",";
		
		builtConf = builtConf.substring(0, builtConf.length() - 1);
		builtConf += ";\n";
		
		for (String key : _file.wires.keySet()) {
			//System.out.println(key + "\n" + key.contains(".") + "\n" + key.split(".")[0]);
			if (_file.contextGroups.contains(_file.wires.get(key)) && key.endsWith("."+_file.wires.get(key))) {
				builtConf += "  " + key + "Group -> " + _file.wires.get(key) + "Configuration;\n";
				if (this.getComponents().containsKey(_file.wires.get(key)) &&
					this.getComponents().get(_file.wires.get(key)).hasLayeredFunctions())
					builtConf += "  " + key + "Layer -> " + _file.wires.get(key) + "Configuration;\n";
			} else if (_file.contextGroups.contains(_file.wires.get(key)) && !key.endsWith("."+_file.wires.get(key))) {
				String lvalue = key;
				if (key.contains(".") && _file.contextGroups.contains(key.split("\\.")[0])) {
					lvalue = key.split("\\.")[0] + "Configuration." + key.split("\\.")[1];
				}
				builtConf += "  " + lvalue + " -> " + _file.wires.get(key) + "Configuration;\n";
			} else if (key.contains(".") && _file.contextGroups.contains(key.split("\\.")[0])) {
				builtConf += "  " + key.split("\\.")[0] + "Configuration." + key.split("\\.")[1] + " -> " + _file.wires.get(key) + ";\n";
			}else
				builtConf += "  " + key + " -> " + _file.wires.get(key) + ";\n";
		}
		
		for (String key : _file.equality.keySet())
			for (String comp : _file.equality.get(key))
				builtConf += "  " + key + " = " + comp + ";\n";
		
		builtConf += "}";
		
		_generatedFiles.put(_file.name + ".nc", builtConf);
	}
}
