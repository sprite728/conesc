/* Generated By:JavaCC: Do not edit this line. ParserConstants.java */
// Copyright (c) 2013 Mikhail Afanasov and DeepSe group. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package parsers.makefile;


/**
 * Token literal values and constants.
 * Generated by org.javacc.parser.OtherFilesGen#start()
 */
public interface ParserConstants {

  /** End of File. */
  int EOF = 0;
  /** RegularExpression Id. */
  int COMPONENT = 5;
  /** RegularExpression Id. */
  int PFLAGS = 6;
  /** RegularExpression Id. */
  int EQUALS = 7;
  /** RegularExpression Id. */
  int ADD = 8;
  /** RegularExpression Id. */
  int INCLUDEFLAG = 9;
  /** RegularExpression Id. */
  int DIRECTORY = 10;
  /** RegularExpression Id. */
  int INCLUDE = 11;
  /** RegularExpression Id. */
  int VARNAME = 12;
  /** RegularExpression Id. */
  int NAME = 13;

  /** Lexical state. */
  int DEFAULT = 0;

  /** Literal token values. */
  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\r\"",
    "\"\\t\"",
    "\"\\n\"",
    "\"COMPONENT\"",
    "\"PFLAGS\"",
    "\"=\"",
    "\"+=\"",
    "\"-I\"",
    "<DIRECTORY>",
    "\"include\"",
    "<VARNAME>",
    "<NAME>",
  };

}
