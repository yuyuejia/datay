// Generated from /Users/chenjie/code/yuyuejia/datay/src/main/antlr4/Parameter.g4 by ANTLR 4.13.2
package com.data.expression.gen;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ParameterParser}.
 */
public interface ParameterListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ParameterParser#parameters}.
	 * @param ctx the parse tree
	 */
	void enterParameters(ParameterParser.ParametersContext ctx);
	/**
	 * Exit a parse tree produced by {@link ParameterParser#parameters}.
	 * @param ctx the parse tree
	 */
	void exitParameters(ParameterParser.ParametersContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DollarCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterDollarCurlyParam(ParameterParser.DollarCurlyParamContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DollarCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitDollarCurlyParam(ParameterParser.DollarCurlyParamContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DollarCurlyAttrParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterDollarCurlyAttrParam(ParameterParser.DollarCurlyAttrParamContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DollarCurlyAttrParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitDollarCurlyAttrParam(ParameterParser.DollarCurlyAttrParamContext ctx);
	/**
	 * Enter a parse tree produced by the {@code HashCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterHashCurlyParam(ParameterParser.HashCurlyParamContext ctx);
	/**
	 * Exit a parse tree produced by the {@code HashCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitHashCurlyParam(ParameterParser.HashCurlyParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link ParameterParser#dollarCurly}.
	 * @param ctx the parse tree
	 */
	void enterDollarCurly(ParameterParser.DollarCurlyContext ctx);
	/**
	 * Exit a parse tree produced by {@link ParameterParser#dollarCurly}.
	 * @param ctx the parse tree
	 */
	void exitDollarCurly(ParameterParser.DollarCurlyContext ctx);
	/**
	 * Enter a parse tree produced by {@link ParameterParser#dollarCurlyAttr}.
	 * @param ctx the parse tree
	 */
	void enterDollarCurlyAttr(ParameterParser.DollarCurlyAttrContext ctx);
	/**
	 * Exit a parse tree produced by {@link ParameterParser#dollarCurlyAttr}.
	 * @param ctx the parse tree
	 */
	void exitDollarCurlyAttr(ParameterParser.DollarCurlyAttrContext ctx);
	/**
	 * Enter a parse tree produced by {@link ParameterParser#hashCurly}.
	 * @param ctx the parse tree
	 */
	void enterHashCurly(ParameterParser.HashCurlyContext ctx);
	/**
	 * Exit a parse tree produced by {@link ParameterParser#hashCurly}.
	 * @param ctx the parse tree
	 */
	void exitHashCurly(ParameterParser.HashCurlyContext ctx);
}