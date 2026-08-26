// Generated from /Users/chenjie/code/yuyuejia/datay/src/main/antlr4/Parameter.g4 by ANTLR 4.13.2
package com.data.expression.gen;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ParameterParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ParameterVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link ParameterParser#parameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameters(ParameterParser.ParametersContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DollarCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDollarCurlyParam(ParameterParser.DollarCurlyParamContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DollarCurlyAttrParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDollarCurlyAttrParam(ParameterParser.DollarCurlyAttrParamContext ctx);
	/**
	 * Visit a parse tree produced by the {@code HashCurlyParam}
	 * labeled alternative in {@link ParameterParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHashCurlyParam(ParameterParser.HashCurlyParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link ParameterParser#dollarCurly}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDollarCurly(ParameterParser.DollarCurlyContext ctx);
	/**
	 * Visit a parse tree produced by {@link ParameterParser#dollarCurlyAttr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDollarCurlyAttr(ParameterParser.DollarCurlyAttrContext ctx);
	/**
	 * Visit a parse tree produced by {@link ParameterParser#hashCurly}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHashCurly(ParameterParser.HashCurlyContext ctx);
}